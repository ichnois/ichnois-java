package io.ichnois;

import io.ichnois.protocol.Feedback;
import io.ichnois.protocol.IchnoisCoreId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NoOpFeedbackApi implements IFeedbackApi {

  private static final NoOpFeedbackApi instance = new NoOpFeedbackApi();

  private NoOpFeedbackApi() {}

  public static NoOpFeedbackApi getInstance() {
    return instance;
  }

  @Override
  public void show() {}

  @Override
  public void show(final @Nullable IchnoisCoreFeedbackOptions.OptionsConfigurator configurator) {}

  @Override
  public void show(
      final @Nullable IchnoisCoreId associatedEventId,
      final @Nullable IchnoisCoreFeedbackOptions.OptionsConfigurator configurator) {}

  @Override
  public void enableOnShake() {}

  @Override
  public void disableOnShake() {}

  @Override
  public boolean isOnShakeEnabled() {
    return false;
  }

  @Override
  public @NotNull IchnoisCoreId capture(final @NotNull Feedback feedback) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull IchnoisCoreId capture(final @NotNull Feedback feedback, final @Nullable Hint hint) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull IchnoisCoreId capture(
      final @NotNull Feedback feedback,
      final @Nullable Hint hint,
      final @Nullable ScopeCallback callback) {
    return IchnoisCoreId.EMPTY_ID;
  }
}
