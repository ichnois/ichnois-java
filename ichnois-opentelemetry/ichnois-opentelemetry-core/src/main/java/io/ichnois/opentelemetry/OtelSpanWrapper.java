package io.ichnois.opentelemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.ichnois.Baggage;
import io.ichnois.BaggageHeader;
import io.ichnois.IScopes;
import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.ISpan;
import io.ichnois.Instrumenter;
import io.ichnois.MeasurementUnit;
import io.ichnois.NoOpScopesLifecycleToken;
import io.ichnois.NoOpSpan;
import io.ichnois.ScopeBindingMode;
import io.ichnois.IchnoisCoreDate;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreTraceHeader;
import io.ichnois.SpanContext;
import io.ichnois.SpanId;
import io.ichnois.SpanOptions;
import io.ichnois.SpanStatus;
import io.ichnois.TraceContext;
import io.ichnois.TracesSamplingDecision;
import io.ichnois.protocol.Contexts;
import io.ichnois.protocol.MeasurementValue;
import io.ichnois.protocol.IchnoisCoreId;
import io.ichnois.protocol.TransactionNameSource;
import io.ichnois.util.AutoClosableReentrantLock;
import io.ichnois.util.Objects;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** NOTE: This wrapper is not used when using OpenTelemetry API, only when using IchnoisCore API. */
@ApiStatus.Internal
public final class OtelSpanWrapper implements IOtelSpanWrapper {

  private final @NotNull IScopes scopes;

  /** The moment in time when span was started. */
  private @NotNull IchnoisCoreDate startTimestamp;

  private @Nullable IchnoisCoreDate finishedTimestamp = null;

  /**
   * OpenTelemetry span which this wrapper wraps. Needs to be referenced weakly as otherwise we'd
   * create a circular reference from {@link io.opentelemetry.sdk.trace.data.SpanData} to {@link
   * OtelSpanWrapper} and indirectly back to {@link io.opentelemetry.sdk.trace.data.SpanData} via
   * {@link Span}. Also see {@link IchnoisCoreWeakSpanStorage}.
   */
  private final @NotNull WeakReference<ReadWriteSpan> span;

  private final @NotNull SpanContext context;
  private final @NotNull Contexts contexts = new Contexts();
  private @Nullable String transactionName;
  private @Nullable TransactionNameSource transactionNameSource;
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  private final @NotNull Map<String, Object> data = new ConcurrentHashMap<>();
  private final @NotNull Map<String, MeasurementValue> measurements = new ConcurrentHashMap<>();

  /** A throwable thrown during the execution of the span. */
  private @Nullable Throwable throwable;

  private @NotNull Deque<IIchnoisCoreLifecycleToken> tokensToCleanup = new ArrayDeque<>(1);

  public OtelSpanWrapper(
      final @NotNull ReadWriteSpan span,
      final @NotNull IScopes scopes,
      final @NotNull IchnoisCoreDate startTimestamp,
      final @Nullable TracesSamplingDecision samplingDecision,
      final @Nullable IOtelSpanWrapper parentSpan,
      final @Nullable SpanId parentSpanId,
      final @Nullable Baggage baggage) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
    this.span = new WeakReference<>(span);
    this.startTimestamp = startTimestamp;
    final @Nullable Baggage baggageToUse =
        baggage != null
            ? baggage
            : (parentSpan != null ? parentSpan.getSpanContext().getBaggage() : null);
    this.context =
        new OtelSpanContext(span, samplingDecision, parentSpan, parentSpanId, baggageToUse);
  }

  @Override
  public @NotNull ISpan startChild(@NotNull String operation) {
    return startChild(operation, (String) null);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation, @Nullable String description, @NotNull SpanOptions spanOptions) {
    if (isFinished()) {
      return NoOpSpan.getInstance();
    }
    final @NotNull SpanContext spanContext =
        context.copyForChild(operation, getSpanContext().getSpanId(), null);
    spanContext.setDescription(description);

    return startChild(spanContext, spanOptions);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull SpanContext spanContext, @NotNull SpanOptions spanOptions) {
    if (isFinished()) {
      return NoOpSpan.getInstance();
    }

    final @NotNull ISpan childSpan =
        scopes.getOptions().getSpanFactory().createSpan(scopes, spanOptions, spanContext, this);

    if (ScopeBindingMode.ON == spanOptions.getScopeBindingMode()) {
      childSpan.makeCurrent();
    } else if (ScopeBindingMode.AUTO == spanOptions.getScopeBindingMode()) {
      final @Nullable SpanId parentSpanId = spanContext.getParentSpanId();
      if (parentSpanId != null) {
        final @Nullable Span currentOtelSpan = Span.fromContextOrNull(Context.current());
        if (currentOtelSpan != null) {
          if (currentOtelSpan
              .getSpanContext()
              .getSpanId()
              .equalsIgnoreCase(parentSpanId.toString())) {
            childSpan.makeCurrent();
          }
        }
      }
    }
    return childSpan;
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation, @Nullable String description, @Nullable IchnoisCoreDate timestamp) {
    return startChild(operation, description, timestamp, Instrumenter.ICHNOIS);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation,
      @Nullable String description,
      @Nullable IchnoisCoreDate timestamp,
      @NotNull Instrumenter instrumenter) {
    final @NotNull SpanContext spanContext =
        context.copyForChild(operation, getSpanContext().getSpanId(), null);
    spanContext.setDescription(description);
    spanContext.setInstrumenter(instrumenter);

    final @NotNull SpanOptions spanOptions = new SpanOptions();
    spanOptions.setStartTimestamp(timestamp);

    return startChild(spanContext, spanOptions);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation,
      @Nullable String description,
      @Nullable IchnoisCoreDate timestamp,
      @NotNull Instrumenter instrumenter,
      @NotNull SpanOptions spanOptions) {
    if (timestamp != null) {
      spanOptions.setStartTimestamp(timestamp);
    }

    final @NotNull SpanContext spanContext =
        context.copyForChild(operation, getSpanContext().getSpanId(), null);
    spanContext.setDescription(description);
    spanContext.setInstrumenter(instrumenter);

    return startChild(spanContext, spanOptions);
  }

  @Override
  public @NotNull ISpan startChild(@NotNull String operation, @Nullable String description) {
    final @NotNull SpanContext spanContext =
        context.copyForChild(operation, getSpanContext().getSpanId(), null);
    spanContext.setDescription(description);

    return startChild(spanContext, new SpanOptions());
  }

  @Override
  public @NotNull IchnoisCoreTraceHeader toIchnoisCoreTrace() {
    return new IchnoisCoreTraceHeader(getTraceId(), getOtelSpanId(), isSampled());
  }

  private @NotNull SpanId getOtelSpanId() {
    return context.getSpanId();
  }

  private @Nullable ReadWriteSpan getSpan() {
    return span.get();
  }

  @ApiStatus.Internal
  @Override
  public @Nullable Attributes getOpenTelemetrySpanAttributes() {
    final @Nullable ReadWriteSpan readWriteSpan = span.get();
    if (readWriteSpan != null) {
      return readWriteSpan.getAttributes();
    }
    return null;
  }

  @Override
  public @Nullable TraceContext traceContext() {
    if (scopes.getOptions().isTraceSampling()) {
      final @Nullable Baggage baggage = context.getBaggage();
      if (baggage != null) {
        updateBaggageValues(baggage);
        return baggage.toTraceContext();
      }
    }
    return null;
  }

  private void updateBaggageValues(final @NotNull Baggage baggage) {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
      if (baggage != null && baggage.isMutable()) {
        final AtomicReference<IchnoisCoreId> replayIdAtomicReference = new AtomicReference<>();
        scopes.configureScope(
            scope -> {
              replayIdAtomicReference.set(scope.getReplayId());
            });
        baggage.setValuesFromTransaction(
            getSpanContext().getTraceId(),
            replayIdAtomicReference.get(),
            scopes.getOptions(),
            this.getSamplingDecision(),
            getTransactionName(),
            getTransactionNameSource());
        baggage.freeze();
      }
    }
  }

  @Override
  public @Nullable BaggageHeader toBaggageHeader(@Nullable List<String> thirdPartyBaggageHeaders) {
    if (scopes.getOptions().isTraceSampling()) {
      final @Nullable Baggage baggage = context.getBaggage();
      if (baggage != null) {
        updateBaggageValues(baggage);
        return BaggageHeader.fromBaggageAndOutgoingHeader(baggage, thirdPartyBaggageHeaders);
      }
    }
    return null;
  }

  @Override
  public void finish() {
    finish(getStatus());
  }

  @Override
  public void finish(@Nullable SpanStatus status) {
    setStatus(status);
    final @Nullable Span otelSpan = getSpan();
    if (otelSpan != null) {
      otelSpan.end();
    }

    for (IIchnoisCoreLifecycleToken token : tokensToCleanup) {
      token.close();
    }
  }

  @Override
  public void finish(@Nullable SpanStatus status, @Nullable IchnoisCoreDate timestamp) {
    setStatus(status);
    final @Nullable Span otelSpan = getSpan();
    if (otelSpan != null) {
      if (timestamp != null) {
        otelSpan.end(timestamp.nanoTimestamp(), TimeUnit.NANOSECONDS);
      } else {
        otelSpan.end();
      }
    }
  }

  @Override
  public void setOperation(@NotNull String operation) {
    this.context.setOperation(operation);
  }

  @Override
  public @NotNull String getOperation() {
    return context.getOperation();
  }

  @Override
  public void setDescription(@Nullable String description) {
    this.context.setDescription(description);
  }

  @Override
  public @Nullable String getDescription() {
    return this.context.getDescription();
  }

  @Override
  public void setStatus(final @Nullable SpanStatus status) {
    context.setStatus(status);
  }

  @Override
  public @Nullable SpanStatus getStatus() {
    return context.getStatus();
  }

  @Override
  public void setThrowable(@Nullable Throwable throwable) {
    this.throwable = throwable;
  }

  @Override
  public @Nullable Throwable getThrowable() {
    return throwable;
  }

  @Override
  public @NotNull SpanContext getSpanContext() {
    return context;
  }

  @Override
  public void setTag(@Nullable String key, @Nullable String value) {
    context.setTag(key, value);
  }

  @Override
  public @Nullable String getTag(@Nullable String key) {
    if (key == null) {
      return null;
    }
    return context.getTags().get(key);
  }

  @Override
  @ApiStatus.Internal
  public @NotNull Map<String, String> getTags() {
    return context.getTags();
  }

  @Override
  public boolean isFinished() {
    final @Nullable ReadWriteSpan otelSpan = getSpan();
    if (otelSpan != null) {
      return otelSpan.hasEnded();
    }

    // if span is no longer available we consider it ended/finished
    return true;
  }

  @Override
  public void setData(@Nullable String key, @Nullable Object value) {
    if (key == null) {
      return;
    }
    if (value == null) {
      data.remove(key);
    } else {
      data.put(key, value);
    }
  }

  @Override
  public @Nullable Object getData(@Nullable String key) {
    if (key == null) {
      return null;
    }
    return data.get(key);
  }

  @Override
  public void setMeasurement(@NotNull String name, @NotNull Number value) {
    if (isFinished()) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "The span is already finished. Measurement %s cannot be set",
              name);
      return;
    }
    this.measurements.put(name, new MeasurementValue(value, null));
  }

  @Override
  public void setMeasurement(
      @NotNull String name, @NotNull Number value, @NotNull MeasurementUnit unit) {
    if (isFinished()) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "The span is already finished. Measurement %s cannot be set",
              name);
      return;
    }
    this.measurements.put(name, new MeasurementValue(value, unit.apiName()));
  }

  @Override
  public boolean updateEndDate(@NotNull IchnoisCoreDate date) {
    if (this.finishedTimestamp != null) {
      this.finishedTimestamp = date;
      return true;
    }
    return false;
  }

  @Override
  public @NotNull IchnoisCoreDate getStartDate() {
    return startTimestamp;
  }

  @Override
  public @Nullable IchnoisCoreDate getFinishDate() {
    return finishedTimestamp;
  }

  @Override
  public boolean isNoOp() {
    return false;
  }

  @Override
  public void setContext(@Nullable String key, @Nullable Object context) {
    contexts.put(key, context);
  }

  @Override
  public @NotNull Contexts getContexts() {
    return contexts;
  }

  @Override
  public void setTransactionName(@NotNull String name) {
    setTransactionName(name, TransactionNameSource.CUSTOM);
  }

  @Override
  public void setTransactionName(@NotNull String name, @NotNull TransactionNameSource nameSource) {
    this.transactionName = name;
    this.transactionNameSource = nameSource;
  }

  @Override
  @ApiStatus.Internal
  public @Nullable TransactionNameSource getTransactionNameSource() {
    return transactionNameSource;
  }

  @Override
  @ApiStatus.Internal
  public @Nullable String getTransactionName() {
    return this.transactionName;
  }

  @Override
  @NotNull
  public IchnoisCoreId getTraceId() {
    return context.getTraceId();
  }

  @Override
  public @NotNull Map<String, Object> getData() {
    return data;
  }

  @Override
  @NotNull
  public Map<String, MeasurementValue> getMeasurements() {
    return measurements;
  }

  @Override
  public @Nullable Boolean isSampled() {
    return context.getSampled();
  }

  @Override
  public @Nullable Boolean isProfileSampled() {
    return context.getProfileSampled();
  }

  @Override
  public @Nullable TracesSamplingDecision getSamplingDecision() {
    return context.getSamplingDecision();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScopes getScopes() {
    return scopes;
  }

  @Override
  public void addFeatureFlag(final @Nullable String flag, final @Nullable Boolean result) {
    context.addFeatureFlag(flag, result);
  }

  @Override
  public @NotNull Context storeInContext(Context context) {
    final @Nullable ReadWriteSpan otelSpan = getSpan();
    if (otelSpan != null) {
      return otelSpan.storeInContext(context);
    } else {
      return context;
    }
  }

  @SuppressWarnings("MustBeClosedChecker")
  @ApiStatus.Internal
  @Override
  public @NotNull IIchnoisCoreLifecycleToken makeCurrent() {
    final @Nullable Span otelSpan = getSpan();
    if (otelSpan != null) {
      final @NotNull Scope otelScope = otelSpan.makeCurrent();
      final @NotNull OtelSpanWrapperToken token = new OtelSpanWrapperToken(otelScope);
      // to iterate LIFO when closing
      tokensToCleanup.addFirst(token);
      return token;
    }
    return NoOpScopesLifecycleToken.getInstance();
  }

  private static final class OtelSpanWrapperToken implements IIchnoisCoreLifecycleToken {

    private final @NotNull Scope otelScope;

    OtelSpanWrapperToken(final @NotNull Scope otelScope) {
      this.otelScope = otelScope;
    }

    @Override
    public void close() {
      otelScope.close();
    }
  }
}
