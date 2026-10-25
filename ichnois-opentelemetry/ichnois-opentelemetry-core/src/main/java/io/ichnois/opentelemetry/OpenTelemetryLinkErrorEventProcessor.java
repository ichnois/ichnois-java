package io.ichnois.opentelemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceId;
import io.ichnois.Hint;
import io.ichnois.IScopes;
import io.ichnois.ISpan;
import io.ichnois.Instrumenter;
import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCoreEvent;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.SpanContext;
import io.ichnois.internal.eventprocessor.IchnoisCoreEventProcessor;
import io.ichnois.protocol.IchnoisCoreId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * @deprecated this is no longer needed for the latest version of our OpenTelemetry integration.
 */
@Deprecated
public final class OpenTelemetryLinkErrorEventProcessor implements IchnoisCoreEventProcessor {

  private final @NotNull IScopes scopes;

  @SuppressWarnings("deprecation")
  private final @NotNull io.ichnois.IchnoisCoreSpanStorage spanStorage =
      io.ichnois.IchnoisCoreSpanStorage.getInstance();

  public OpenTelemetryLinkErrorEventProcessor() {
    this(ScopesAdapter.getInstance());
  }

  @TestOnly
  OpenTelemetryLinkErrorEventProcessor(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  @Override
  public @Nullable IchnoisCoreEvent process(final @NotNull IchnoisCoreEvent event, final @NotNull Hint hint) {
    final @NotNull Instrumenter instrumenter = scopes.getOptions().getInstrumenter();
    if (Instrumenter.OTEL.equals(instrumenter)) {
      @NotNull final Span otelSpan = Span.current();
      @NotNull final String traceId = otelSpan.getSpanContext().getTraceId();
      @NotNull final String spanId = otelSpan.getSpanContext().getSpanId();

      if (TraceId.isValid(traceId) && SpanId.isValid(spanId)) {
        final @Nullable ISpan ichnoisSpan = spanStorage.get(spanId);
        if (ichnoisSpan != null) {
          final @NotNull SpanContext ichnoisSpanSpanContext = ichnoisSpan.getSpanContext();
          final @NotNull String operation = ichnoisSpanSpanContext.getOperation();
          final @Nullable io.ichnois.SpanId parentSpanId = ichnoisSpanSpanContext.getParentSpanId();
          final @NotNull SpanContext spanContext =
              new SpanContext(
                  new IchnoisCoreId(traceId),
                  new io.ichnois.SpanId(spanId),
                  operation,
                  parentSpanId,
                  null);

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
                  "Not linking IchnoisCore event %s to any transaction created via OpenTelemetry as none has been found for span %s (trace %s).",
                  event.getEventId(),
                  spanId,
                  traceId);
        }
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
    } else {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Not linking IchnoisCore event %s to any transaction created via OpenTelemetry as instrumenter is set to %s.",
              event.getEventId(),
              instrumenter);
    }

    return event;
  }

  @Override
  public @Nullable Long getOrder() {
    return 6000L;
  }
}
