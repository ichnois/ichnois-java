package io.ichnois;

import io.ichnois.protocol.Feedback;
import io.ichnois.protocol.IchnoisCoreId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IFeedbackApi {

  void show();

  void show(final @Nullable IchnoisCoreFeedbackOptions.OptionsConfigurator configurator);

  void show(
      final @Nullable IchnoisCoreId associatedEventId,
      final @Nullable IchnoisCoreFeedbackOptions.OptionsConfigurator configurator);

  /**
   * Enables showing the feedback form when a shake gesture is detected, overriding {@link
   * IchnoisCoreFeedbackOptions#isUseShakeGesture()}. Only supported on Android; no-op on other
   * platforms.
   */
  void enableOnShake();

  /**
   * Disables showing the feedback form when a shake gesture is detected, overriding {@link
   * IchnoisCoreFeedbackOptions#isUseShakeGesture()}. Only supported on Android; no-op on other
   * platforms.
   *
   * <p>This turns off the SDK-wide shake detection only. A feedback form whose own options enable
   * the shake gesture still runs its own detection while it is showing and while its host activity
   * is alive, so shaking can re-open that form.
   */
  void disableOnShake();

  /**
   * Whether showing the feedback form on a shake gesture is currently enabled. Always {@code false}
   * on non-Android platforms. Reflects the SDK-wide shake detection only; a form running its own
   * shake detection is not covered by this.
   *
   * @return true if the feedback form is shown when a shake gesture is detected
   */
  boolean isOnShakeEnabled();

  @NotNull
  IchnoisCoreId capture(final @NotNull Feedback feedback);

  @NotNull
  IchnoisCoreId capture(final @NotNull Feedback feedback, final @Nullable Hint hint);

  @NotNull
  IchnoisCoreId capture(
      final @NotNull Feedback feedback,
      final @Nullable Hint hint,
      final @Nullable ScopeCallback callback);
}
