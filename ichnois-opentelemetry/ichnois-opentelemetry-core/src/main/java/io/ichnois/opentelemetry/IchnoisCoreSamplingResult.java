package io.ichnois.opentelemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.trace.samplers.SamplingDecision;
import io.opentelemetry.sdk.trace.samplers.SamplingResult;
import io.ichnois.TracesSamplingDecision;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class IchnoisCoreSamplingResult implements SamplingResult {
  private final TracesSamplingDecision ichnoisDecision;

  public IchnoisCoreSamplingResult(final @NotNull TracesSamplingDecision ichnoisDecision) {
    this.ichnoisDecision = ichnoisDecision;
  }

  @Override
  public SamplingDecision getDecision() {
    if (ichnoisDecision.getSampled()) {
      return SamplingDecision.RECORD_AND_SAMPLE;
    } else {
      return SamplingDecision.RECORD_ONLY;
    }
  }

  @Override
  public Attributes getAttributes() {
    return Attributes.builder()
        .put(InternalSemanticAttributes.SAMPLED, ichnoisDecision.getSampled())
        .put(InternalSemanticAttributes.SAMPLE_RATE, ichnoisDecision.getSampleRate())
        .put(InternalSemanticAttributes.SAMPLE_RAND, ichnoisDecision.getSampleRand())
        .put(InternalSemanticAttributes.PROFILE_SAMPLED, ichnoisDecision.getProfileSampled())
        .put(InternalSemanticAttributes.PROFILE_SAMPLE_RATE, ichnoisDecision.getProfileSampleRate())
        .build();
  }

  public TracesSamplingDecision getIchnoisCoreDecision() {
    return ichnoisDecision;
  }
}
