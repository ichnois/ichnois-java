package io.ichnois.opentelemetry;

import static io.ichnois.TransactionContext.DEFAULT_TRANSACTION_NAME;
import static io.ichnois.opentelemetry.InternalSemanticAttributes.IS_REMOTE_PARENT;
import static io.ichnois.opentelemetry.OtelInternalSpanDetectionUtil.isIchnoisCoreRequest;
import static io.ichnois.opentelemetry.OtelSpanUtils.maybeTransferOtelAttribute;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes;
import io.ichnois.Baggage;
import io.ichnois.DateUtils;
import io.ichnois.DefaultSpanFactory;
import io.ichnois.IScopes;
import io.ichnois.ISpan;
import io.ichnois.ITransaction;
import io.ichnois.Instrumenter;
import io.ichnois.ScopeType;
import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCoreDate;
import io.ichnois.IchnoisCoreInstantDate;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreLongDate;
import io.ichnois.SpanContext;
import io.ichnois.SpanId;
import io.ichnois.SpanOptions;
import io.ichnois.SpanStatus;
import io.ichnois.TransactionContext;
import io.ichnois.TransactionOptions;
import io.ichnois.featureflags.IFeatureFlagBuffer;
import io.ichnois.protocol.Contexts;
import io.ichnois.protocol.FeatureFlag;
import io.ichnois.protocol.FeatureFlags;
import io.ichnois.protocol.IchnoisCoreId;
import io.ichnois.protocol.TransactionNameSource;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IchnoisCoreSpanExporter implements SpanExporter {
  private volatile boolean stopped = false;
  private final List<SpanData> finishedSpans = new CopyOnWriteArrayList<>();
  private final @NotNull IchnoisCoreWeakSpanStorage spanStorage = IchnoisCoreWeakSpanStorage.getInstance();
  private final @NotNull SpanDescriptionExtractor spanDescriptionExtractor =
      new SpanDescriptionExtractor();
  private final @NotNull OpenTelemetryAttributesExtractor attributesExtractor =
      new OpenTelemetryAttributesExtractor();
  private final @NotNull IScopes scopes;

  private final @NotNull List<String> attributeKeysToRemove =
      Arrays.asList(
          InternalSemanticAttributes.IS_REMOTE_PARENT.getKey(),
          InternalSemanticAttributes.BAGGAGE.getKey(),
          InternalSemanticAttributes.BAGGAGE_MUTABLE.getKey(),
          InternalSemanticAttributes.SAMPLED.getKey(),
          InternalSemanticAttributes.SAMPLE_RATE.getKey(),
          InternalSemanticAttributes.SAMPLE_RAND.getKey(),
          InternalSemanticAttributes.PROFILE_SAMPLED.getKey(),
          InternalSemanticAttributes.PROFILE_SAMPLE_RATE.getKey(),
          InternalSemanticAttributes.PARENT_SAMPLED.getKey(),
          ProcessIncubatingAttributes.PROCESS_COMMAND_ARGS.getKey() // can be very long
          );

  private final @NotNull List<String> attributeToRemoveByPrefix =
      Arrays.asList("http.request.header.", "http.response.header.");
  private static final @NotNull Long SPAN_TIMEOUT = DateUtils.secondsToNanos(5 * 60);

  public static final String TRACE_ORIGIN = "auto.opentelemetry";

  public IchnoisCoreSpanExporter() {
    this(ScopesAdapter.getInstance());
  }

  public IchnoisCoreSpanExporter(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  @Override
  public CompletableResultCode export(Collection<SpanData> spans) {
    if (stopped) {
      // TODO unsure if there's a way to attach a message
      return CompletableResultCode.ofFailure();
    }

    final int openSpanCount = finishedSpans.size();
    final int newSpanCount = spans.size();

    final @NotNull List<SpanData> nonIchnoisCoreRequestSpans = filterOutIchnoisCoreSpans(spans);

    finishedSpans.addAll(nonIchnoisCoreRequestSpans);
    final @NotNull List<SpanData> remaining = maybeSend(finishedSpans);
    final int remainingSpanCount = remaining.size();
    final int sentSpanCount = openSpanCount + newSpanCount - remainingSpanCount;

    scopes
        .getOptions()
        .getLogger()
        .log(
            IchnoisCoreLevel.DEBUG,
            "SpanExporter exported %s spans, %s unset spans remaining.",
            sentSpanCount,
            remainingSpanCount);

    this.finishedSpans.clear();

    final @NotNull IchnoisCoreInstantDate now = new IchnoisCoreInstantDate();

    final @NotNull List<SpanData> nonExpired =
        remaining.stream().filter((span) -> !isSpanTooOld(span, now)).collect(Collectors.toList());

    this.finishedSpans.addAll(nonExpired);

    // TODO

    return CompletableResultCode.ofSuccess();
  }

  private boolean isSpanTooOld(final @NotNull SpanData span, final @NotNull IchnoisCoreInstantDate now) {
    final @NotNull IchnoisCoreDate startDate = new IchnoisCoreLongDate(span.getStartEpochNanos());
    boolean isTimedOut = now.diff(startDate) > SPAN_TIMEOUT;
    if (isTimedOut) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Dropping span %s as it was pending for too long.",
              span.getSpanId());
    }
    return isTimedOut;
  }

  private @NotNull List<SpanData> filterOutIchnoisCoreSpans(final @NotNull Collection<SpanData> spans) {
    return spans.stream()
        .filter((span) -> !isIchnoisCoreRequest(scopes, span.getKind(), span.getAttributes()))
        .collect(Collectors.toList());
  }

  private List<SpanData> maybeSend(final @NotNull List<SpanData> spans) {
    final @NotNull List<SpanNode> grouped = groupSpansWithParents(spans);
    final @NotNull List<SpanNode> remaining = new CopyOnWriteArrayList<>(grouped);
    final @NotNull List<SpanNode> rootNodes = findCompletedRootNodes(grouped);

    for (final @NotNull SpanNode rootNode : rootNodes) {
      remaining.remove(rootNode);
      final @Nullable SpanData span = rootNode.getSpan();
      if (span == null) {
        // TODO log
        continue;
      }
      final @Nullable ITransaction transaction = createTransactionForOtelSpan(span);
      if (transaction == null) {
        // TODO log
        continue;
      }

      for (final @NotNull SpanNode childNode : rootNode.getChildren()) {
        createAndFinishSpanForOtelSpan(childNode, transaction, remaining);
      }

      transaction.finish(
          mapOtelStatus(span, transaction), new IchnoisCoreLongDate(span.getEndEpochNanos()));
    }

    return remaining.stream()
        .map((node) -> node.getSpan())
        .filter((it) -> it != null)
        .collect(Collectors.toList());
  }

  private void createAndFinishSpanForOtelSpan(
      final @NotNull SpanNode spanNode,
      final @NotNull ISpan parentIchnoisCoreSpan,
      final @NotNull List<SpanNode> remaining) {
    remaining.remove(spanNode);
    final @Nullable SpanData spanData = spanNode.getSpan();

    // If this span should be dropped, we still want to create spans for the children of this
    if (spanData == null) {
      for (SpanNode childNode : spanNode.getChildren()) {
        createAndFinishSpanForOtelSpan(childNode, parentIchnoisCoreSpan, remaining);
      }
      return;
    }

    final @NotNull String spanId = spanData.getSpanId();
    final @Nullable IOtelSpanWrapper ichnoisSpanMaybe =
        spanStorage.getIchnoisCoreSpan(spanData.getSpanContext());
    final @NotNull OtelSpanInfo spanInfo =
        spanDescriptionExtractor.extractSpanInfo(spanData, ichnoisSpanMaybe, scopes.getOptions());

    scopes
        .getOptions()
        .getLogger()
        .log(
            IchnoisCoreLevel.DEBUG,
            "Creating IchnoisCore child span for OpenTelemetry span %s (trace %s). Parent span is %s.",
            spanId,
            spanData.getTraceId(),
            spanData.getParentSpanId());
    final @NotNull IchnoisCoreDate startDate = new IchnoisCoreLongDate(spanData.getStartEpochNanos());
    final @NotNull SpanOptions spanOptions = new SpanOptions();
    final @NotNull io.ichnois.SpanContext spanContext =
        parentIchnoisCoreSpan
            .getSpanContext()
            .copyForChild(
                spanInfo.getOp(),
                parentIchnoisCoreSpan.getSpanContext().getSpanId(),
                new SpanId(spanId));
    spanContext.setDescription(spanInfo.getDescription());
    spanContext.setInstrumenter(Instrumenter.ICHNOIS);
    if (ichnoisSpanMaybe != null) {
      spanContext.setSamplingDecision(ichnoisSpanMaybe.getSamplingDecision());
      spanOptions.setOrigin(ichnoisSpanMaybe.getSpanContext().getOrigin());
    } else {
      spanOptions.setOrigin(TRACE_ORIGIN);
    }

    spanOptions.setStartTimestamp(startDate);

    final @NotNull ISpan ichnoisChildSpan = parentIchnoisCoreSpan.startChild(spanContext, spanOptions);

    for (Map.Entry<String, Object> dataField :
        toMapWithStringKeys(spanData.getAttributes()).entrySet()) {
      ichnoisChildSpan.setData(dataField.getKey(), dataField.getValue());
    }

    setOtelInstrumentationInfo(spanData, ichnoisChildSpan);
    setOtelSpanKind(spanData, ichnoisChildSpan);
    transferSpanDetails(ichnoisSpanMaybe, ichnoisChildSpan);

    for (SpanNode childNode : spanNode.getChildren()) {
      createAndFinishSpanForOtelSpan(childNode, ichnoisChildSpan, remaining);
    }

    ichnoisChildSpan.finish(
        mapOtelStatus(spanData, ichnoisChildSpan), new IchnoisCoreLongDate(spanData.getEndEpochNanos()));
  }

  private void transferSpanDetails(
      final @Nullable IOtelSpanWrapper sourceSpanMaybe, final @NotNull ISpan targetSpan) {
    if (sourceSpanMaybe != null) {
      final @NotNull IOtelSpanWrapper sourceSpan = sourceSpanMaybe;

      final @NotNull Contexts contexts = sourceSpan.getContexts();
      targetSpan.getContexts().putAll(contexts);

      final @NotNull Map<String, Object> data = sourceSpan.getData();
      for (Map.Entry<String, Object> entry : data.entrySet()) {
        targetSpan.setData(entry.getKey(), entry.getValue());
      }

      final @NotNull SpanContext spanContext = sourceSpan.getSpanContext();
      final @NotNull IFeatureFlagBuffer featureFlagBuffer = spanContext.getFeatureFlagBuffer();
      final @Nullable FeatureFlags featureFlags = featureFlagBuffer.getFeatureFlags();
      if (featureFlags != null) {
        for (FeatureFlag featureFlag : featureFlags.getValues()) {
          targetSpan.setData(
              FeatureFlag.DATA_PREFIX + featureFlag.getFlag(), featureFlag.getResult());
        }
      }

      final @NotNull Map<String, String> tags = sourceSpan.getTags();
      for (Map.Entry<String, String> entry : tags.entrySet()) {
        targetSpan.setTag(entry.getKey(), entry.getValue());
      }

      targetSpan.setStatus(sourceSpan.getStatus());
    }
  }

  private @Nullable ITransaction createTransactionForOtelSpan(final @NotNull SpanData span) {
    final @NotNull String spanId = span.getSpanId();
    final @NotNull String traceId = span.getTraceId();
    final @Nullable IOtelSpanWrapper ichnoisSpanMaybe =
        spanStorage.getIchnoisCoreSpan(span.getSpanContext());
    final @Nullable IScopes scopesMaybe =
        ichnoisSpanMaybe != null ? ichnoisSpanMaybe.getScopes() : null;
    final @NotNull IScopes scopesToUseBeforeForking =
        scopesMaybe == null ? ScopesAdapter.getInstance() : scopesMaybe;
    final @NotNull IScopes scopesToUse =
        scopesToUseBeforeForking.forkedCurrentScope("IchnoisCoreSpanExporter.createTransaction");
    final @NotNull OtelSpanInfo spanInfo =
        spanDescriptionExtractor.extractSpanInfo(span, ichnoisSpanMaybe, scopesToUse.getOptions());

    scopesToUse
        .getOptions()
        .getLogger()
        .log(
            IchnoisCoreLevel.DEBUG,
            "Creating IchnoisCore transaction for OpenTelemetry span %s (trace %s).",
            spanId,
            traceId);
    final SpanId ichnoisSpanId = new SpanId(spanId);

    @Nullable String transactionName = spanInfo.getDescription();
    @NotNull TransactionNameSource transactionNameSource = spanInfo.getTransactionNameSource();
    @Nullable SpanId parentSpanId = null;
    @Nullable Baggage baggage = null;
    @NotNull IchnoisCoreId profilerId = IchnoisCoreId.EMPTY_ID;

    if (ichnoisSpanMaybe != null) {
      final @NotNull IOtelSpanWrapper ichnoisSpan = ichnoisSpanMaybe;
      final @Nullable String transactionNameMaybe = ichnoisSpan.getTransactionName();
      if (transactionNameMaybe != null) {
        transactionName = transactionNameMaybe;
      }
      final @Nullable TransactionNameSource transactionNameSourceMaybe =
          ichnoisSpan.getTransactionNameSource();
      if (transactionNameSourceMaybe != null) {
        transactionNameSource = transactionNameSourceMaybe;
      }
      final @NotNull SpanContext spanContext = ichnoisSpan.getSpanContext();
      parentSpanId = spanContext.getParentSpanId();
      baggage = spanContext.getBaggage();
      profilerId = spanContext.getProfilerId();
    }

    final @NotNull TransactionContext transactionContext =
        new TransactionContext(new IchnoisCoreId(traceId), ichnoisSpanId, parentSpanId, null, baggage);

    TransactionOptions transactionOptions = new TransactionOptions();

    transactionContext.setName(
        transactionName == null ? DEFAULT_TRANSACTION_NAME : transactionName);
    transactionContext.setTransactionNameSource(transactionNameSource);
    transactionContext.setOperation(spanInfo.getOp());
    transactionContext.setInstrumenter(Instrumenter.ICHNOIS);
    transactionContext.setProfilerId(profilerId);
    if (ichnoisSpanMaybe != null) {
      transactionContext.setSamplingDecision(ichnoisSpanMaybe.getSamplingDecision());
      transactionOptions.setOrigin(ichnoisSpanMaybe.getSpanContext().getOrigin());
    }

    transactionOptions.setStartTimestamp(new IchnoisCoreLongDate(span.getStartEpochNanos()));
    transactionOptions.setSpanFactory(new DefaultSpanFactory());

    ITransaction ichnoisTransaction =
        scopesToUse.startTransaction(transactionContext, transactionOptions);

    final @NotNull Map<String, Object> otelContext = toOtelContext(span);
    ichnoisTransaction.setContext("otel", otelContext);

    setOtelInstrumentationInfo(span, ichnoisTransaction);
    setOtelSpanKind(span, ichnoisTransaction);
    transferSpanDetails(ichnoisSpanMaybe, ichnoisTransaction);

    maybeTransferOtelAttribute(span, ichnoisTransaction, ThreadIncubatingAttributes.THREAD_ID);
    maybeTransferOtelAttribute(span, ichnoisTransaction, ThreadIncubatingAttributes.THREAD_NAME);

    maybeTransferOtelAttribute(
        span, ichnoisTransaction, MessagingIncubatingAttributes.MESSAGING_SYSTEM);
    maybeTransferOtelAttribute(
        span, ichnoisTransaction, MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME);
    maybeTransferOtelAttribute(
        span, ichnoisTransaction, MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE);
    maybeTransferOtelAttribute(
        span, ichnoisTransaction, MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID);
    maybeTransferOtelAttribute(
        span, ichnoisTransaction, MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE);
    maybeTransferOtelAttribute(
        span, ichnoisTransaction, MessagingIncubatingAttributes.MESSAGING_MESSAGE_ENVELOPE_SIZE);

    scopesToUse.configureScope(
        ScopeType.CURRENT,
        scope -> attributesExtractor.extract(span, scope, scopesToUse.getOptions()));

    return ichnoisTransaction;
  }

  private List<SpanNode> findCompletedRootNodes(final @NotNull List<SpanNode> grouped) {
    final @NotNull Predicate<SpanNode> isRootPredicate =
        (node) -> {
          return node.getParentNode() == null && node.getSpan() != null;
        };
    return grouped.stream().filter(isRootPredicate).collect(Collectors.toList());
  }

  private List<SpanNode> groupSpansWithParents(final @NotNull List<SpanData> spans) {
    final @NotNull Map<String, SpanNode> nodeMap = new HashMap<>();

    for (final @NotNull SpanData spanData : spans) {
      createOrUpdateSpanNodeAndRefs(nodeMap, spanData);
    }

    return nodeMap.values().stream().collect(Collectors.toList());
  }

  private void createOrUpdateSpanNodeAndRefs(
      final @NotNull Map<String, SpanNode> nodeMap, final @NotNull SpanData spanData) {
    final @NotNull String spanId = spanData.getSpanId();
    final String parentId = getParentId(spanData);
    if (parentId == null) {
      createOrUpdateNode(nodeMap, spanId, spanData, null, null);
      return;
    }

    final @NotNull SpanNode parentNode = createOrGetParentNode(nodeMap, parentId);
    final @NotNull SpanNode spanNode =
        createOrUpdateNode(nodeMap, spanId, spanData, null, parentNode);
    parentNode.addChild(spanNode);
  }

  private @Nullable String getParentId(final @NotNull SpanData spanData) {
    final @NotNull String parentSpanId = spanData.getParentSpanId();
    final @Nullable Boolean isRemoteParent = spanData.getAttributes().get(IS_REMOTE_PARENT);
    if (isRemoteParent != null && isRemoteParent) {
      return null;
    }
    if (io.opentelemetry.api.trace.SpanId.isValid(parentSpanId)) {
      return parentSpanId;
    }
    return null;
  }

  private @NotNull SpanNode createOrGetParentNode(
      final @NotNull Map<String, SpanNode> nodeMap, final @NotNull String spanId) {
    final @Nullable SpanNode existingNode = nodeMap.get(spanId);

    if (existingNode == null) {
      return createOrUpdateNode(nodeMap, spanId, null, null, null);
    }

    return existingNode;
  }

  // TODO do we ever pass children?
  private @NotNull SpanNode createOrUpdateNode(
      final @NotNull Map<String, SpanNode> nodeMap,
      final @NotNull String spanId,
      final @Nullable SpanData spanData,
      final @Nullable List<SpanNode> children,
      final @Nullable SpanNode parentNode) {
    final @Nullable SpanNode existingNode = nodeMap.get(spanId);

    if (existingNode != null) {
      final @Nullable SpanData existingNodeSpan = existingNode.getSpan();

      if (existingNodeSpan != null) {
        // If span is already set, nothing to do here
        return existingNode;
      }

      // If span is not set yet, we update it
      existingNode.setSpan(spanData);
      existingNode.setParentNode(parentNode);

      return existingNode;
    }

    final @NotNull SpanNode spanNode = new SpanNode(spanId);
    spanNode.setSpan(spanData);
    spanNode.setParentNode(parentNode);
    spanNode.addChildren(children);

    nodeMap.put(spanId, spanNode);

    return spanNode;
  }

  @SuppressWarnings("deprecation")
  private SpanStatus mapOtelStatus(
      final @NotNull SpanData otelSpanData, final @NotNull ISpan ichnoisSpan) {
    final @Nullable SpanStatus existingStatus = ichnoisSpan.getStatus();
    if (existingStatus != null && existingStatus != SpanStatus.UNKNOWN_ERROR) {
      return existingStatus;
    }

    final @NotNull StatusData otelStatus = otelSpanData.getStatus();
    final @NotNull StatusCode otelStatusCode = otelStatus.getStatusCode();

    if (StatusCode.OK.equals(otelStatusCode) || StatusCode.UNSET.equals(otelStatusCode)) {
      return SpanStatus.OK;
    }

    final @Nullable Long httpStatus =
        otelSpanData.getAttributes().get(HttpAttributes.HTTP_RESPONSE_STATUS_CODE);
    if (httpStatus != null) {
      final @Nullable SpanStatus spanStatus = SpanStatus.fromHttpStatusCode(httpStatus.intValue());
      if (spanStatus != null) {
        return spanStatus;
      }
    }

    return SpanStatus.UNKNOWN_ERROR;
  }

  private @NotNull Map<String, Object> toOtelContext(final @NotNull SpanData spanData) {
    final @NotNull Map<String, Object> context = new HashMap<>();

    context.put("attributes", toMapWithStringKeys(spanData.getAttributes()));
    context.put("resource", toMapWithStringKeys(spanData.getResource().getAttributes()));

    return context;
  }

  private @NotNull Map<String, Object> toMapWithStringKeys(final @Nullable Attributes attributes) {
    final @NotNull Map<String, Object> mapWithStringKeys = new HashMap<>();

    if (attributes != null) {
      attributes.forEach(
          (key, value) -> {
            if (key != null) {
              final @NotNull String stringKey = key.getKey();
              if (!shouldRemoveAttribute(stringKey)) {
                mapWithStringKeys.put(stringKey, value);
              }
            }
          });
    }

    return mapWithStringKeys;
  }

  private boolean shouldRemoveAttribute(final @NotNull String key) {
    if (attributeKeysToRemove.contains(key)) {
      return true;
    }

    for (String prefix : attributeToRemoveByPrefix) {
      if (key.startsWith(prefix)) {
        return true;
      }
    }

    return false;
  }

  private void setOtelInstrumentationInfo(
      final @NotNull SpanData span, final @NotNull ISpan ichnoisTransaction) {
    final @Nullable String otelInstrumentationName = span.getInstrumentationScopeInfo().getName();
    if (otelInstrumentationName != null) {
      ichnoisTransaction.setData("otel.instrumentation.name", otelInstrumentationName);
    }

    final @Nullable String otelInstrumentationVersion =
        span.getInstrumentationScopeInfo().getVersion();
    if (otelInstrumentationVersion != null) {
      ichnoisTransaction.setData("otel.instrumentation.version", otelInstrumentationVersion);
    }
  }

  private void setOtelSpanKind(final @NotNull SpanData otelSpan, final @NotNull ISpan ichnoisSpan) {
    ichnoisSpan.setData("otel.kind", otelSpan.getKind().name());
  }

  @Override
  public CompletableResultCode flush() {
    scopes.flush(10000);
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode shutdown() {
    stopped = true;
    scopes.close();
    return CompletableResultCode.ofSuccess();
  }
}
