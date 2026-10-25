package io.ichnois.opentelemetry;

import static io.ichnois.opentelemetry.OtelInternalSpanDetectionUtil.isIchnoisCoreRequest;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import io.ichnois.Baggage;
import io.ichnois.DataCategory;
import io.ichnois.IScopes;
import io.ichnois.PropagationContext;
import io.ichnois.SamplingContext;
import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreTraceHeader;
import io.ichnois.SpanId;
import io.ichnois.TracesSamplingDecision;
import io.ichnois.TransactionContext;
import io.ichnois.clientreport.DiscardReason;
import io.ichnois.protocol.IchnoisCoreId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IchnoisCoreSampler implements Sampler {

  private final @NotNull IchnoisCoreWeakSpanStorage spanStorage = IchnoisCoreWeakSpanStorage.getInstance();
  private final @NotNull IScopes scopes;

  public IchnoisCoreSampler(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  public IchnoisCoreSampler() {
    this(ScopesAdapter.getInstance());
  }

  @Override
  public SamplingResult shouldSample(
      final @NotNull Context parentContext,
      final @NotNull String traceId,
      final @NotNull String name,
      final @NotNull SpanKind spanKind,
      final @NotNull Attributes attributes,
      final @NotNull List<LinkData> parentLinks) {
    if (isIchnoisCoreRequest(scopes, spanKind, attributes)) {
      return SamplingResult.drop();
    }
    // note: parentLinks seems to usually be empty
    final @Nullable Span parentOtelSpan = Span.fromContextOrNull(parentContext);
    final @Nullable IOtelSpanWrapper parentIchnoisCoreSpan =
        parentOtelSpan != null ? spanStorage.getIchnoisCoreSpan(parentOtelSpan.getSpanContext()) : null;

    if (parentIchnoisCoreSpan != null) {
      return copyParentIchnoisCoreDecision(parentIchnoisCoreSpan);
    } else {
      final @Nullable TracesSamplingDecision samplingDecision =
          OtelSamplingUtil.extractSamplingDecision(attributes);
      if (samplingDecision != null) {
        return new IchnoisCoreSamplingResult(samplingDecision);
      } else {
        return handleRootOtelSpan(traceId, parentContext, attributes);
      }
    }
  }

  private @NotNull SamplingResult handleRootOtelSpan(
      final @NotNull String traceId,
      final @NotNull Context parentContext,
      final @NotNull Attributes attributes) {
    if (!scopes.getOptions().isTracingEnabled()) {
      return SamplingResult.create(SamplingDecision.RECORD_ONLY);
    }
    @Nullable Baggage baggage = null;
    @Nullable
    IchnoisCoreTraceHeader ichnoisTraceHeader = parentContext.get(IchnoisCoreOtelKeys.ICHNOIS_TRACE_KEY);
    @Nullable Baggage baggageFromContext = parentContext.get(IchnoisCoreOtelKeys.ICHNOIS_BAGGAGE_KEY);
    if (ichnoisTraceHeader != null) {
      baggage = baggageFromContext;
    }

    // there's no way to get the span id here, so we just use a random id for sampling
    SpanId randomSpanId = new SpanId();
    final @NotNull PropagationContext propagationContext =
        ichnoisTraceHeader == null
            ? new PropagationContext(new IchnoisCoreId(traceId), randomSpanId, null, baggage, null)
            : PropagationContext.fromHeaders(
                ichnoisTraceHeader, baggage, randomSpanId, scopes.getOptions());

    final @NotNull TransactionContext transactionContext =
        TransactionContext.fromPropagationContext(propagationContext);
    final @NotNull TracesSamplingDecision ichnoisDecision =
        scopes
            .getOptions()
            .getInternalTracesSampler()
            .sample(
                new SamplingContext(
                    transactionContext,
                    null,
                    propagationContext.getSampleRand(),
                    toMapWithStringKeys(attributes)));

    if (!ichnoisDecision.getSampled()) {
      scopes
          .getOptions()
          .getClientReportRecorder()
          .recordLostEvent(DiscardReason.SAMPLE_RATE, DataCategory.Transaction);
      scopes
          .getOptions()
          .getClientReportRecorder()
          .recordLostEvent(DiscardReason.SAMPLE_RATE, DataCategory.Span);
    }

    return new IchnoisCoreSamplingResult(ichnoisDecision);
  }

  private @NotNull IchnoisCoreSamplingResult copyParentIchnoisCoreDecision(
      final @NotNull IOtelSpanWrapper parentIchnoisCoreSpan) {
    final @Nullable TracesSamplingDecision parentSamplingDecision =
        parentIchnoisCoreSpan.getSamplingDecision();
    if (parentSamplingDecision != null) {
      if (!parentSamplingDecision.getSampled()) {
        scopes
            .getOptions()
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.SAMPLE_RATE, DataCategory.Span);
      }
      return new IchnoisCoreSamplingResult(parentSamplingDecision);
    } else {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Encountered a missing parent sampling decision where one was expected.");
      return new IchnoisCoreSamplingResult(new TracesSamplingDecision(true));
    }
  }

  private @NotNull Map<String, Object> toMapWithStringKeys(final @NotNull Attributes attributes) {
    final @NotNull Map<String, Object> mapWithStringKeys = new HashMap<>(attributes.size());

    if (attributes != null) {
      attributes.forEach(
          (key, value) -> {
            if (key != null) {
              final @NotNull String stringKey = key.getKey();
              mapWithStringKeys.put(stringKey, value);
            }
          });
    }

    return mapWithStringKeys;
  }

  @Override
  public String getDescription() {
    return "IchnoisCoreSampler";
  }
}
