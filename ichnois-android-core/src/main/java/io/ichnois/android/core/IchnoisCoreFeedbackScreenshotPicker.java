package io.ichnois.android.core;

import android.app.Activity;
import android.net.Uri;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Launches the androidx photo picker to attach an screenshot to user feedback. All
 * androidx.activity references are isolated in this class, so it must only be loaded once {@link
 * IchnoisCoreUserFeedbackForm#isScreenshotPickerAvailable} has returned true. That check deliberately
 * lives outside of this class, so that it can run without linking any androidx.activity type.
 */
final class IchnoisCoreFeedbackScreenshotPicker {

  interface OnScreenshotPicked {
    void onScreenshotPicked(@NotNull Uri uri);
  }

  private final @NotNull ActivityResultLauncher<PickVisualMediaRequest> launcher;

  private IchnoisCoreFeedbackScreenshotPicker(
      final @NotNull ActivityResultLauncher<PickVisualMediaRequest> launcher) {
    this.launcher = launcher;
  }

  static @Nullable IchnoisCoreFeedbackScreenshotPicker register(
      final @NotNull Activity activity,
      final @NotNull IchnoisCoreFeedbackScreenshotPicker.OnScreenshotPicked callback) {
    if (!(activity instanceof ComponentActivity)) {
      return null;
    }
    final @NotNull ActivityResultLauncher<PickVisualMediaRequest> launcher =
        ((ComponentActivity) activity)
            .getActivityResultRegistry()
            .register(
                "ichnois_user_feedback_screenshot_picker",
                new ActivityResultContracts.PickVisualMedia(),
                (@Nullable Uri uri) -> {
                  if (uri != null) {
                    callback.onScreenshotPicked(uri);
                  }
                });
    return new IchnoisCoreFeedbackScreenshotPicker(launcher);
  }

  void launch() {
    launcher.launch(
        new PickVisualMediaRequest.Builder()
            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
            .build());
  }

  void unregister() {
    launcher.unregister();
  }
}
