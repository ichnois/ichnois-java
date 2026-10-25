package io.ichnois.clientreport;

import io.ichnois.DataCategory;
import io.ichnois.IchnoisCoreEnvelope;
import io.ichnois.IchnoisCoreEnvelopeItem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NoOpClientReportRecorder implements IClientReportRecorder {
  @Override
  public void recordLostEnvelope(@NotNull DiscardReason reason, @Nullable IchnoisCoreEnvelope envelope) {
    // do nothing
  }

  @Override
  public void recordLostEnvelopeItem(
      @NotNull DiscardReason reason, @Nullable IchnoisCoreEnvelopeItem envelopeItem) {
    // do nothing
  }

  @Override
  public void recordLostEvent(@NotNull DiscardReason reason, @NotNull DataCategory category) {
    // do nothing
  }

  @Override
  public void recordLostEvent(
      @NotNull DiscardReason reason, @NotNull DataCategory category, long count) {
    // do nothing
  }

  @Override
  public @NotNull IchnoisCoreEnvelope attachReportToEnvelope(@NotNull IchnoisCoreEnvelope envelope) {
    return envelope;
  }
}
