package io.ichnois.opentelemetry;

import static io.ichnois.TransactionContext.DEFAULT_TRANSACTION_NAME;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import io.ichnois.Baggage;
import io.ichnois.DsnUtil;
import io.ichnois.IScopes;
import io.ichnois.ISpan;
import io.ichnois.ITransaction;
import io.ichnois.Instrumenter;
import io.ichnois.PropagationContext;
import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCoreDate;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreLongDate;
import io.ichnois.IchnoisCoreTraceHeader;
import io.ichnois.SpanId;
import io.ichnois.SpanOptions;
import io.ichnois.SpanStatus;
import io.ichnois.TransactionContext;
import io.ichnois.TransactionOptions;
import io.ichnois.protocol.IchnoisCoreId;
import io.ichnois.protocol.TransactionNameSource;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated please use {@link OtelIchnoisCoreSpanProcessor} instead.
 */
@Deprecated
public final class IchnoisCoreSpanProcessor implements SpanProcessor {

  private static final String TRACE_ORIGN = "auto.otel";

  private final @NotNull List<SpanKind> spanKindsConsideredForIchnoisCoreRequests =
      Arrays.asList(SpanKind.CLIENT, SpanKind.INTERNAL);
  private final @NotNull SpanDescriptionExtractor spanDescriptionExtractor =
      new SpanDescriptionExtractor();

  @SuppressWarnings("deprecation")
  private final @NotNull io.ichnois.IchnoisCoreSpanStorage spanStorage =
      io.ichnois.IchnoisCoreSpanStorage.getInstance();

  private final @NotNull IScopes scopes;

  public IchnoisCoreSpanProcessor() {
    this(ScopesAdapter.getInstance());
  }

  IchnoisCoreSpanProcessor(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  @Override
  public void onStart(final @NotNull Context parentContext, final @NotNull ReadWriteSpan otelSpan) {
    if (!ensurePrerequisites(otelSpan)) {
      return;
    }

    final @NotNull TraceData traceData = getTraceData(otelSpan, parentContext);

    if (isIchnoisCoreRequest(otelSpan)) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Not forwarding OpenTelemetry span %s to IchnoisCore as this looks like a span for a request to IchnoisCore (trace %s).",
              traceData.getSpanId(),
              traceData.getTraceId());
      return;
    }
    final @Nullable ISpan ichnoisParentSpan =
        traceData.getParentSpanId() == null ? null : spanStorage.get(traceData.getParentSpanId());

    if (ichnoisParentSpan != null) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Creating IchnoisCore child span for OpenTelemetry span %s (trace %s). Parent span is %s.",
              traceData.getSpanId(),
              traceData.getTraceId(),
              traceData.getParentSpanId());
      final @NotNull IchnoisCoreDate startDate =
          new IchnoisCoreLongDate(otelSpan.toSpanData().getStartEpochNanos());
      final @NotNull SpanOptions spanOptions = new SpanOptions();
      spanOptions.setOrigin(TRACE_ORIGN);
      final @NotNull ISpan ichnoisChildSpan =
          ichnoisParentSpan.startChild(
              otelSpan.getName(), otelSpan.getName(), startDate, Instrumenter.OTEL, spanOptions);
      spanStorage.store(traceData.getSpanId(), ichnoisChildSpan);
    } else {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Creating IchnoisCore transaction for OpenTelemetry span %s (trace %s).",
              traceData.getSpanId(),
              traceData.getTraceId());
      final @NotNull String transactionName = otelSpan.getName();
      final @NotNull TransactionNameSource transactionNameSource = TransactionNameSource.CUSTOM;
      final @Nullable String op = otelSpan.getName();
      final SpanId spanId = new SpanId(traceData.getSpanId());

      final @NotNull TransactionContext transactionContext =
          traceData.getIchnoisCoreTraceHeader() == null
              ? new TransactionContext(
                  new IchnoisCoreId(traceData.getTraceId()), spanId, null, null, null)
              : TransactionContext.fromPropagationContext(
                  PropagationContext.fromHeaders(
                      traceData.getIchnoisCoreTraceHeader(),
                      traceData.getBaggage(),
                      spanId,
                      scopes.getOptions()));
      ;
      transactionContext.setName(transactionName);
      transactionContext.setTransactionNameSource(transactionNameSource);
      transactionContext.setOperation(op);
      transactionContext.setInstrumenter(Instrumenter.OTEL);

      TransactionOptions transactionOptions = new TransactionOptions();
      transactionOptions.setStartTimestamp(
          new IchnoisCoreLongDate(otelSpan.toSpanData().getStartEpochNanos()));
      transactionOptions.setOrigin(TRACE_ORIGN);

      ISpan ichnoisTransaction = scopes.startTransaction(transactionContext, transactionOptions);
      spanStorage.store(traceData.getSpanId(), ichnoisTransaction);
    }
  }

  @Override
  public boolean isStartRequired() {
    return true;
  }

  @Override
  public void onEnd(final @NotNull ReadableSpan otelSpan) {
    if (!ensurePrerequisites(otelSpan)) {
      return;
    }

    final @NotNull TraceData traceData = getTraceData(otelSpan, null);
    final @Nullable ISpan ichnoisSpan = spanStorage.removeAndGet(traceData.getSpanId());

    if (ichnoisSpan == null) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Unable to find IchnoisCore span for OpenTelemetry span %s (trace %s). This may simply mean it is a IchnoisCore request.",
              traceData.getSpanId(),
              traceData.getTraceId());
      return;
    }

    if (isIchnoisCoreRequest(otelSpan)) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Not forwarding OpenTelemetry span %s to IchnoisCore as this looks like a span for a request to IchnoisCore (trace %s).",
              traceData.getSpanId(),
              traceData.getTraceId());
      return;
    }

    if (ichnoisSpan instanceof ITransaction) {
      final @NotNull ITransaction ichnoisTransaction = (ITransaction) ichnoisSpan;
      updateTransactionWithOtelData(ichnoisTransaction, otelSpan);
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Finishing IchnoisCore transaction %s for OpenTelemetry span %s (trace %s).",
              ichnoisTransaction.getEventId(),
              traceData.getSpanId(),
              traceData.getTraceId());
    } else {
      updateSpanWithOtelData(ichnoisSpan, otelSpan);
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Finishing IchnoisCore span for OpenTelemetry span %s (trace %s). Parent span is %s.",
              traceData.getSpanId(),
              traceData.getTraceId(),
              traceData.getParentSpanId());
    }

    final @NotNull SpanStatus ichnoisStatus = mapOtelStatus(otelSpan);
    final @NotNull IchnoisCoreDate endTimestamp =
        new IchnoisCoreLongDate(otelSpan.toSpanData().getEndEpochNanos());
    ichnoisSpan.finish(ichnoisStatus, endTimestamp);
  }

  @Override
  public boolean isEndRequired() {
    return true;
  }

  private boolean ensurePrerequisites(final @NotNull ReadableSpan otelSpan) {
    if (!hasIchnoisCoreBeenInitialized()) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Not forwarding OpenTelemetry span to IchnoisCore as IchnoisCore has not yet been initialized.");
      return false;
    }

    final @NotNull Instrumenter instrumenter = scopes.getOptions().getInstrumenter();
    if (!Instrumenter.OTEL.equals(instrumenter)) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Not forwarding OpenTelemetry span to IchnoisCore as instrumenter has been set to %s.",
              instrumenter);
      return false;
    }

    final @NotNull SpanContext otelSpanContext = otelSpan.getSpanContext();
    if (!otelSpanContext.isValid()) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Not forwarding OpenTelemetry span to IchnoisCore as the span is invalid.");
      return false;
    }

    return true;
  }

  @SuppressWarnings("deprecation")
  private boolean isIchnoisCoreRequest(final @NotNull ReadableSpan otelSpan) {
    final @NotNull SpanKind kind = otelSpan.getKind();
    if (!spanKindsConsideredForIchnoisCoreRequests.contains(kind)) {
      return false;
    }

    final @Nullable String httpUrl = otelSpan.getAttribute(UrlAttributes.URL_FULL);
    return DsnUtil.urlContainsDsnHost(scopes.getOptions(), httpUrl);
  }

  private @NotNull TraceData getTraceData(
      final @NotNull ReadableSpan otelSpan, final @Nullable Context parentContext) {
    final @NotNull SpanContext otelSpanContext = otelSpan.getSpanContext();
    final @NotNull String otelSpanId = otelSpanContext.getSpanId();
    final @NotNull String otelParentSpanIdMaybeInvalid =
        otelSpan.getParentSpanContext().getSpanId();
    final @NotNull String otelTraceId = otelSpanContext.getTraceId();
    final @Nullable String otelParentSpanId =
        io.opentelemetry.api.trace.SpanId.isValid(otelParentSpanIdMaybeInvalid)
            ? otelParentSpanIdMaybeInvalid
            : null;

    @Nullable IchnoisCoreTraceHeader ichnoisTraceHeader = null;
    @Nullable Baggage baggage = null;

    if (parentContext != null) {
      ichnoisTraceHeader = parentContext.get(IchnoisCoreOtelKeys.ICHNOIS_TRACE_KEY);
      if (ichnoisTraceHeader != null) {
        baggage = parentContext.get(IchnoisCoreOtelKeys.ICHNOIS_BAGGAGE_KEY);
      }
    }

    return new TraceData(otelTraceId, otelSpanId, otelParentSpanId, ichnoisTraceHeader, baggage);
  }

  private void updateTransactionWithOtelData(
      final @NotNull ITransaction ichnoisTransaction, final @NotNull ReadableSpan otelSpan) {
    final @NotNull OtelSpanInfo otelSpanInfo =
        spanDescriptionExtractor.extractSpanInfo(otelSpan.toSpanData(), null, scopes.getOptions());
    ichnoisTransaction.setOperation(otelSpanInfo.getOp());
    String transactionName = otelSpanInfo.getDescription();
    ichnoisTransaction.setName(
        transactionName == null ? DEFAULT_TRANSACTION_NAME : transactionName,
        otelSpanInfo.getTransactionNameSource());

    final @NotNull Map<String, Object> otelContext = toOtelContext(otelSpan);
    ichnoisTransaction.setContext("otel", otelContext);
  }

  private @NotNull Map<String, Object> toOtelContext(final @NotNull ReadableSpan otelSpan) {
    final @NotNull SpanData spanData = otelSpan.toSpanData();
    final @NotNull Map<String, Object> context = new HashMap<>();

    context.put("attributes", toMapWithStringKeys(spanData.getAttributes()));
    context.put("resource", toMapWithStringKeys(spanData.getResource().getAttributes()));

    return context;
  }

  private void updateSpanWithOtelData(
      final @NotNull ISpan ichnoisSpan, final @NotNull ReadableSpan otelSpan) {
    final @NotNull SpanData spanData = otelSpan.toSpanData();

    ichnoisSpan.setData("otel.kind", otelSpan.getKind());

    spanData
        .getAttributes()
        .forEach(
            (attributeKey, value) -> {
              if (value != null) {
                ichnoisSpan.setData(attributeKey.getKey(), value);
              }
            });

    final @NotNull OtelSpanInfo otelSpanInfo =
        spanDescriptionExtractor.extractSpanInfo(otelSpan.toSpanData(), null, scopes.getOptions());
    ichnoisSpan.setOperation(otelSpanInfo.getOp());
    ichnoisSpan.setDescription(otelSpanInfo.getDescription());
  }

  @SuppressWarnings("deprecation")
  private SpanStatus mapOtelStatus(final @NotNull ReadableSpan otelSpan) {
    final @NotNull SpanData otelSpanData = otelSpan.toSpanData();
    final @NotNull StatusData otelStatus = otelSpanData.getStatus();
    final @NotNull StatusCode otelStatusCode = otelStatus.getStatusCode();

    if (StatusCode.OK.equals(otelStatusCode) || StatusCode.UNSET.equals(otelStatusCode)) {
      return SpanStatus.OK;
    }

    final @Nullable Long httpStatus =
        otelSpan.getAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE);
    if (httpStatus != null) {
      final @Nullable SpanStatus spanStatus = SpanStatus.fromHttpStatusCode(httpStatus.intValue());
      if (spanStatus != null) {
        return spanStatus;
      }
    }

    return SpanStatus.UNKNOWN_ERROR;
  }

  private boolean hasIchnoisCoreBeenInitialized() {
    return scopes.isEnabled();
  }

  private @NotNull Map<String, Object> toMapWithStringKeys(final @Nullable Attributes attributes) {
    final @NotNull Map<String, Object> mapWithStringKeys = new HashMap<>();

    if (attributes != null) {
      attributes.forEach(
          (key, value) -> {
            if (key != null) {
              mapWithStringKeys.put(key.getKey(), value);
            }
          });
    }

    return mapWithStringKeys;
  }
}
