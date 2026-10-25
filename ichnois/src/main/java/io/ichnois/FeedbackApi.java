package io.ichnois;

import io.ichnois.protocol.Feedback;
import io.ichnois.protocol.IchnoisCoreId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class FeedbackApi implements IFeedbackApi {

  private final @NotNull IScopes scopes;

  FeedbackApi(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  @Override
  public void show() {
    show(null, null);
  }

  @Override
  public void show(final @Nullable IchnoisCoreFeedbackOptions.OptionsConfigurator configurator) {
    show(null, configurator);
  }

  @Override
  public void show(
      final @Nullable IchnoisCoreId associatedEventId,
      final @Nullable IchnoisCoreFeedbackOptions.OptionsConfigurator configurator) {
    final @NotNull IchnoisCoreOptions options = scopes.getOptions();
    options.getFeedbackOptions().getFormHandler().showForm(associatedEventId, configurator);
  }

  @Override
  public void enableOnShake() {
    scopes.getOptions().getFeedbackOptions().getShakeController().enableOnShake();
  }

  @Override
  public void disableOnShake() {
    scopes.getOptions().getFeedbackOptions().getShakeController().disableOnShake();
  }

  @Override
  public boolean isOnShakeEnabled() {
    return scopes.getOptions().getFeedbackOptions().getShakeController().isOnShakeEnabled();
  }

  @Override
  public @NotNull IchnoisCoreId capture(final @NotNull Feedback feedback) {
    return scopes.captureFeedback(feedback);
  }

  @Override
  public @NotNull IchnoisCoreId capture(final @NotNull Feedback feedback, final @Nullable Hint hint) {
    return scopes.captureFeedback(feedback, hint);
  }

  @Override
  public @NotNull IchnoisCoreId capture(
      final @NotNull Feedback feedback,
      final @Nullable Hint hint,
      final @Nullable ScopeCallback callback) {
    return scopes.captureFeedback(feedback, hint, callback);
  }
}
