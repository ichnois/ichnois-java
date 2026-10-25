package io.ichnois.clientreport;

import io.ichnois.DataCategory;
import io.ichnois.IchnoisCoreEnvelope;
import io.ichnois.IchnoisCoreEnvelopeItem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface IClientReportRecorder {
  void recordLostEnvelope(@NotNull DiscardReason reason, @Nullable IchnoisCoreEnvelope envelope);

  void recordLostEnvelopeItem(
      @NotNull DiscardReason reason, @Nullable IchnoisCoreEnvelopeItem envelopeItem);

  void recordLostEvent(@NotNull DiscardReason reason, @NotNull DataCategory category);

  void recordLostEvent(@NotNull DiscardReason reason, @NotNull DataCategory category, long count);

  @NotNull
  IchnoisCoreEnvelope attachReportToEnvelope(@NotNull IchnoisCoreEnvelope envelope);
}
