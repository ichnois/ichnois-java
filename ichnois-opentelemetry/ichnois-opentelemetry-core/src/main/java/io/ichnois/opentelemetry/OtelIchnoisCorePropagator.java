package io.ichnois.opentelemetry;

import io.opentelemetry.api.common.Attributes;
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
import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.IchnoisCoreTraceHeader;
import io.ichnois.exception.InvalidIchnoisCoreTraceHeaderException;
import io.ichnois.util.TracingUtils;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class OtelIchnoisCorePropagator implements TextMapPropagator {

  private static final @NotNull List<String> FIELDS =
      Arrays.asList(IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER, BaggageHeader.BAGGAGE_HEADER);
  private final @NotNull IchnoisCoreWeakSpanStorage spanStorage = IchnoisCoreWeakSpanStorage.getInstance();
  private final @NotNull IScopes scopes;
  private final @NotNull OpenTelemetryAttributesExtractor attributesExtractor =
      new OpenTelemetryAttributesExtractor();

  public OtelIchnoisCorePropagator() {
    this(ScopesAdapter.getInstance());
  }

  OtelIchnoisCorePropagator(final @NotNull IScopes scopes) {
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

    final @Nullable IOtelSpanWrapper ichnoisSpan = spanStorage.getIchnoisCoreSpan(otelSpanContext);
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

    final @Nullable String url = getUrl(ichnoisSpan, scopes.getOptions());
    final @Nullable TracingUtils.TracingHeaders tracingHeaders =
        url == null
            ? TracingUtils.trace(scopes, Collections.emptyList(), ichnoisSpan)
            : TracingUtils.traceIfAllowed(scopes, url, Collections.emptyList(), ichnoisSpan);

    if (tracingHeaders != null) {
      final @NotNull IchnoisCoreTraceHeader ichnoisTraceHeader = tracingHeaders.getIchnoisCoreTraceHeader();
      setter.set(carrier, ichnoisTraceHeader.getName(), ichnoisTraceHeader.getValue());
      final @Nullable BaggageHeader baggageHeader = tracingHeaders.getBaggageHeader();
      if (baggageHeader != null) {
        setter.set(carrier, baggageHeader.getName(), baggageHeader.getValue());
      }
    }
  }

  private @Nullable String getUrl(
      final @NotNull IOtelSpanWrapper ichnoisSpan, final @NotNull IchnoisCoreOptions options) {
    final @Nullable Attributes attributes = ichnoisSpan.getOpenTelemetrySpanAttributes();
    if (attributes == null) {
      return null;
    }
    return attributesExtractor.extractUrl(attributes, options);
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
      final Baggage baggage = Baggage.fromHeader(baggageString);
      if (!TracingUtils.shouldContinueTrace(scopes.getOptions(), baggage)) {
        scopes
            .getOptions()
            .getLogger()
            .log(
                IchnoisCoreLevel.DEBUG, "Not continuing trace due to strict org ID validation failure.");
        return context;
      }
      final @NotNull TraceState traceState = TraceState.getDefault();

      SpanContext otelSpanContext =
          SpanContext.createFromRemoteParent(
              ichnoisTraceHeader.getTraceId().toString(),
              ichnoisTraceHeader.getSpanId().toString(),
              TraceFlags.getSampled(),
              traceState);

      Span wrappedSpan = Span.wrap(otelSpanContext);

      final @NotNull Context modifiedContext =
          context
              .with(wrappedSpan)
              .with(IchnoisCoreOtelKeys.ICHNOIS_TRACE_KEY, ichnoisTraceHeader)
              .with(IchnoisCoreOtelKeys.ICHNOIS_BAGGAGE_KEY, baggage);

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
