package io.ichnois.opentelemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.ichnois.Baggage;
import io.ichnois.BaggageHeader;
import io.ichnois.IScopes;
import io.ichnois.ISpan;
import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreTraceHeader;
import io.ichnois.exception.InvalidIchnoisCoreTraceHeaderException;
import io.ichnois.util.TracingUtils;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated please use {@link OtelIchnoisCorePropagator} instead
 */
@Deprecated
public final class IchnoisCorePropagator implements TextMapPropagator {

  private static final @NotNull List<String> FIELDS =
      Arrays.asList(IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER, BaggageHeader.BAGGAGE_HEADER);

  @SuppressWarnings("deprecation")
  private final @NotNull io.ichnois.IchnoisCoreSpanStorage spanStorage =
      io.ichnois.IchnoisCoreSpanStorage.getInstance();

  private final @NotNull IScopes scopes;

  public IchnoisCorePropagator() {
    this(ScopesAdapter.getInstance());
  }

  IchnoisCorePropagator(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  @Override
  public Collection<String> fields() {
    return FIELDS;
  }

  @Override
  public <C> void inject(final Context context, final C carrier, final TextMapSetter<C> setter) {
    final @NotNull Span otelSpan = Span.fromContext(context);
    final @NotNull SpanContext otelSpanContext = otelSpan.getSpanContext();
    if (!otelSpanContext.isValid()) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Not injecting IchnoisCore tracing information for invalid OpenTelemetry span.");
      return;
    }
    final @Nullable ISpan ichnoisSpan = spanStorage.get(otelSpanContext.getSpanId());
    if (ichnoisSpan == null || ichnoisSpan.isNoOp()) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Not injecting IchnoisCore tracing information for span %s as no IchnoisCore span has been found or it is a NoOp (trace %s). This might simply mean this is a request to IchnoisCore.",
              otelSpanContext.getSpanId(),
              otelSpanContext.getTraceId());
      return;
    }

    final @NotNull IchnoisCoreTraceHeader ichnoisTraceHeader = ichnoisSpan.toIchnoisCoreTrace();
    setter.set(carrier, ichnoisTraceHeader.getName(), ichnoisTraceHeader.getValue());
    final @Nullable BaggageHeader baggageHeader =
        ichnoisSpan.toBaggageHeader(Collections.emptyList());
    if (baggageHeader != null) {
      setter.set(carrier, baggageHeader.getName(), baggageHeader.getValue());
    }
  }

  @Override
  public <C> Context extract(
      final Context context, final C carrier, final TextMapGetter<C> getter) {
    final @Nullable String ichnoisTraceString =
        getter.get(carrier, IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER);
    if (ichnoisTraceString == null) {
      return context;
    }

    try {
      IchnoisCoreTraceHeader ichnoisTraceHeader = new IchnoisCoreTraceHeader(ichnoisTraceString);

      final @Nullable String baggageString = getter.get(carrier, BaggageHeader.BAGGAGE_HEADER);
      Baggage baggage = Baggage.fromHeader(baggageString);
      if (!TracingUtils.shouldContinueTrace(scopes.getOptions(), baggage)) {
        scopes
            .getOptions()
            .getLogger()
            .log(
                IchnoisCoreLevel.DEBUG, "Not continuing trace due to strict org ID validation failure.");
        return context;
      }

      SpanContext otelSpanContext =
          SpanContext.createFromRemoteParent(
              ichnoisTraceHeader.getTraceId().toString(),
              ichnoisTraceHeader.getSpanId().toString(),
              TraceFlags.getSampled(),
              TraceState.getDefault());

      @NotNull
      Context modifiedContext = context.with(IchnoisCoreOtelKeys.ICHNOIS_TRACE_KEY, ichnoisTraceHeader);
      modifiedContext = modifiedContext.with(IchnoisCoreOtelKeys.ICHNOIS_BAGGAGE_KEY, baggage);

      Span wrappedSpan = Span.wrap(otelSpanContext);
      modifiedContext = modifiedContext.with(wrappedSpan);

      scopes
          .getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.DEBUG, "Continuing IchnoisCore trace %s", ichnoisTraceHeader.getTraceId());

      return modifiedContext;
    } catch (InvalidIchnoisCoreTraceHeaderException e) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.ERROR,
              "Unable to extract IchnoisCore tracing information from invalid header.",
              e);
      return context;
    }
  }
}
