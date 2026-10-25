package io.ichnois;

import io.ichnois.exception.InvalidIchnoisCoreTraceHeaderException;
import io.ichnois.protocol.IchnoisCoreId;
import io.ichnois.util.TracingUtils;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class PropagationContext {

  public static PropagationContext fromHeaders(
      final @NotNull ILogger logger,
      final @Nullable String ichnoisTraceHeader,
      final @Nullable String baggageHeader,
      final @Nullable IchnoisCoreOptions options) {
    return fromHeaders(logger, ichnoisTraceHeader, Arrays.asList(baggageHeader), options);
  }

  public static @NotNull PropagationContext fromHeaders(
      final @NotNull ILogger logger,
      final @Nullable String ichnoisTraceHeaderString,
      final @Nullable List<String> baggageHeaderStrings,
      final @Nullable IchnoisCoreOptions options) {
    if (ichnoisTraceHeaderString == null) {
      return new PropagationContext();
    }

    try {
      final @NotNull IchnoisCoreTraceHeader traceHeader = new IchnoisCoreTraceHeader(ichnoisTraceHeaderString);
      final @NotNull Baggage baggage = Baggage.fromHeader(baggageHeaderStrings, logger);

      return fromHeaders(traceHeader, baggage, null, options);
    } catch (InvalidIchnoisCoreTraceHeaderException e) {
      logger.log(IchnoisCoreLevel.DEBUG, e, "Failed to parse IchnoisCore trace header: %s", e.getMessage());
      return new PropagationContext();
    }
  }

  public static @NotNull PropagationContext fromHeaders(
      final @NotNull IchnoisCoreTraceHeader ichnoisTraceHeader,
      final @Nullable Baggage baggage,
      final @Nullable SpanId spanId,
      final @Nullable IchnoisCoreOptions options) {
    if (options != null && !TracingUtils.shouldContinueTrace(options, baggage)) {
      options
          .getLogger()
          .log(IchnoisCoreLevel.DEBUG, "Not continuing trace due to strict org ID validation failure.");
      return new PropagationContext();
    }

    final @NotNull SpanId spanIdToUse = spanId == null ? new SpanId() : spanId;

    return new PropagationContext(
        ichnoisTraceHeader.getTraceId(),
        spanIdToUse,
        ichnoisTraceHeader.getSpanId(),
        baggage,
        ichnoisTraceHeader.isSampled());
  }

  public static @NotNull PropagationContext fromExistingTrace(
      final @NotNull String traceId,
      final @NotNull String spanId,
      final @Nullable Double decisionSampleRate,
      final @Nullable Double decisionSampleRand) {
    return new PropagationContext(
        new IchnoisCoreId(traceId),
        new SpanId(),
        new SpanId(spanId),
        TracingUtils.ensureBaggage(null, null, decisionSampleRate, decisionSampleRand),
        null);
  }

  private @NotNull IchnoisCoreId traceId;
  private @NotNull SpanId spanId;
  private @Nullable SpanId parentSpanId;

  private @Nullable Boolean sampled;

  private final @NotNull Baggage baggage;

  public PropagationContext() {
    this(new IchnoisCoreId(), new SpanId(), null, null, null);
  }

  public PropagationContext(final @NotNull PropagationContext propagationContext) {
    this(
        propagationContext.getTraceId(),
        propagationContext.getSpanId(),
        propagationContext.getParentSpanId(),
        propagationContext.getBaggage(),
        propagationContext.isSampled());
  }

  public PropagationContext(
      final @NotNull IchnoisCoreId traceId,
      final @NotNull SpanId spanId,
      final @Nullable SpanId parentSpanId,
      final @Nullable Baggage baggage,
      final @Nullable Boolean sampled) {
    this.traceId = traceId;
    this.spanId = spanId;
    this.parentSpanId = parentSpanId;
    this.baggage = TracingUtils.ensureBaggage(baggage, sampled, null, null);
    this.sampled = sampled;
  }

  public @NotNull IchnoisCoreId getTraceId() {
    return traceId;
  }

  public void setTraceId(final @NotNull IchnoisCoreId traceId) {
    this.traceId = traceId;
  }

  public @NotNull SpanId getSpanId() {
    return spanId;
  }

  public void setSpanId(final @NotNull SpanId spanId) {
    this.spanId = spanId;
  }

  public @Nullable SpanId getParentSpanId() {
    return parentSpanId;
  }

  public void setParentSpanId(final @Nullable SpanId parentSpanId) {
    this.parentSpanId = parentSpanId;
  }

  public @NotNull Baggage getBaggage() {
    return baggage;
  }

  public @Nullable Boolean isSampled() {
    return sampled;
  }

  public void setSampled(final @Nullable Boolean sampled) {
    this.sampled = sampled;
  }

  public @Nullable TraceContext traceContext() {
    return baggage.toTraceContext();
  }

  public @NotNull SpanContext toSpanContext() {
    final SpanContext spanContext = new SpanContext(traceId, spanId, "default", null, null);
    spanContext.setOrigin("auto");
    return spanContext;
  }

  public @NotNull Double getSampleRand() {
    final @Nullable Double sampleRand = baggage.getSampleRand();
    // should never be null since we ensure it in ctor
    return sampleRand == null ? 0.0 : sampleRand;
  }
}
