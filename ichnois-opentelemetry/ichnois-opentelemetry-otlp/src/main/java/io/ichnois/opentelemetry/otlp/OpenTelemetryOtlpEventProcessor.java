package io.ichnois.opentelemetry.otlp;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceId;
import io.ichnois.Hint;
import io.ichnois.IScopes;
import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCoreEvent;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreLogEvent;
import io.ichnois.IchnoisCoreMetricsEvent;
import io.ichnois.SpanContext;
import io.ichnois.internal.eventprocessor.IchnoisCoreEventProcessor;
import io.ichnois.protocol.IchnoisCoreId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class OpenTelemetryOtlpEventProcessor implements IchnoisCoreEventProcessor {

  private final @NotNull IScopes scopes;

  public OpenTelemetryOtlpEventProcessor() {
    this(ScopesAdapter.getInstance());
  }

  @TestOnly
  OpenTelemetryOtlpEventProcessor(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  @Override
  public @Nullable IchnoisCoreEvent process(final @NotNull IchnoisCoreEvent event, final @NotNull Hint hint) {
    @NotNull final Span otelSpan = Span.current();
    @NotNull final String traceId = otelSpan.getSpanContext().getTraceId();
    @NotNull final String spanId = otelSpan.getSpanContext().getSpanId();

    if (TraceId.isValid(traceId) && SpanId.isValid(spanId)) {
      final @NotNull SpanContext spanContext =
          new SpanContext(
              new IchnoisCoreId(traceId), new io.ichnois.SpanId(spanId), "opentelemetry", null, null);

      event.getContexts().setTrace(spanContext);
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Linking IchnoisCore event %s to span %s created via OpenTelemetry (trace %s).",
              event.getEventId(),
              spanId,
              traceId);
    } else {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Not linking IchnoisCore event %s to any transaction created via OpenTelemetry as traceId %s or spanId %s are invalid.",
              event.getEventId(),
              traceId,
              spanId);
    }

    return event;
  }

  @Override
  public @Nullable IchnoisCoreLogEvent process(@NotNull IchnoisCoreLogEvent event) {
    @NotNull final Span otelSpan = Span.current();
    @NotNull final String traceId = otelSpan.getSpanContext().getTraceId();
    @NotNull final String spanId = otelSpan.getSpanContext().getSpanId();

    if (TraceId.isValid(traceId) && SpanId.isValid(spanId)) {
      event.setTraceId(new IchnoisCoreId(traceId));
      event.setSpanId(new io.ichnois.SpanId(spanId));
    } else {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Not linking IchnoisCore event to any transaction created via OpenTelemetry as traceId %s or spanId %s are invalid.",
              traceId,
              spanId);
    }

    return event;
  }

  @Override
  public @Nullable IchnoisCoreMetricsEvent process(
      @NotNull IchnoisCoreMetricsEvent event, @NotNull Hint hint) {
    @NotNull final Span otelSpan = Span.current();
    @NotNull final String traceId = otelSpan.getSpanContext().getTraceId();
    @NotNull final String spanId = otelSpan.getSpanContext().getSpanId();

    if (TraceId.isValid(traceId) && SpanId.isValid(spanId)) {
      event.setTraceId(new IchnoisCoreId(traceId));
      event.setSpanId(new io.ichnois.SpanId(spanId));
    } else {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Not linking IchnoisCore event to any transaction created via OpenTelemetry as traceId %s or spanId %s are invalid.",
              traceId,
              spanId);
    }

    return event;
  }

  @Override
  public @Nullable Long getOrder() {
    return 6000L;
  }
}
