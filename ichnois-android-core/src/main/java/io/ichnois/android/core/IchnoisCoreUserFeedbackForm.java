package io.ichnois.android.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import io.ichnois.Attachment;
import io.ichnois.Hint;
import io.ichnois.IScopes;
import io.ichnois.IchnoisCore;
import io.ichnois.IchnoisCoreFeedbackOptions;
import io.ichnois.IchnoisCoreIntegrationPackageStorage;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.protocol.Feedback;
import io.ichnois.protocol.IchnoisCoreId;
import io.ichnois.protocol.User;
import io.ichnois.util.ExceptionUtils;
import io.ichnois.util.FileUtils;
import io.ichnois.util.LoadClass;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IchnoisCoreUserFeedbackForm extends AlertDialog {

  private boolean isCancelable = false;
  private @Nullable IchnoisCoreId currentReplayId;
  private final @Nullable IchnoisCoreId associatedEventId;
  private @Nullable OnDismissListener delegate;

  private final @NotNull IchnoisCoreFeedbackOptions resolvedFeedbackOptions;

  private @Nullable IchnoisCoreShakeDetector shakeDetector;
  private @Nullable Application.ActivityLifecycleCallbacks shakeLifecycleCallbacks;

  private @Nullable IchnoisCoreFeedbackScreenshotPicker screenshotPicker;
  private @Nullable Uri selectedImageUri;

  IchnoisCoreUserFeedbackForm(
      final @NotNull Context context,
      final int themeResId,
      final @Nullable IchnoisCoreId associatedEventId,
      final @Nullable OptionsConfiguration configuration,
      final @Nullable IchnoisCoreFeedbackOptions.OptionsConfigurator configurator) {
    super(context, themeResId);
    this.associatedEventId = associatedEventId;
    final @NotNull IchnoisCoreOptions options = IchnoisCore.getCurrentScopes().getOptions();
    @NotNull
    IchnoisCoreFeedbackOptions feedbackOptions = new IchnoisCoreFeedbackOptions(options.getFeedbackOptions());
    if (configuration != null) {
      final @NotNull IchnoisCoreFeedbackOptions configuredOptions =
          new IchnoisCoreFeedbackOptions(feedbackOptions);
      try {
        configuration.configure(context, configuredOptions);
        feedbackOptions = configuredOptions;
      } catch (Throwable e) {
        ExceptionUtils.rethrowIfFatal(e);
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.ERROR, "Feedback OptionsConfiguration callback threw an exception.", e);
      }
    }
    if (configurator != null) {
      final @NotNull IchnoisCoreFeedbackOptions configuredOptions =
          new IchnoisCoreFeedbackOptions(feedbackOptions);
      try {
        configurator.configure(configuredOptions);
        feedbackOptions = configuredOptions;
      } catch (Throwable e) {
        ExceptionUtils.rethrowIfFatal(e);
        options
            .getLogger()
            .log(IchnoisCoreLevel.ERROR, "Feedback OptionsConfigurator callback threw an exception.", e);
      }
    }
    this.resolvedFeedbackOptions = feedbackOptions;
    IchnoisCoreIntegrationPackageStorage.getInstance().addIntegration("UserFeedbackWidget");
    maybeStartShakeDetection(context);
  }

  private void maybeStartShakeDetection(final @NotNull Context context) {
    // Only start shake detection if it's enabled within the options,
    // and not already running globally
    final @NotNull IchnoisCoreFeedbackOptions globalFeedbackOptions =
        IchnoisCore.getCurrentScopes().getOptions().getFeedbackOptions();
    if (!resolvedFeedbackOptions.isUseShakeGesture()
        || globalFeedbackOptions.getShakeController().isOnShakeEnabled()) {
      return;
    }
    final @Nullable Activity activity = getActivity(context);
    if (activity == null) {
      return;
    }
    final @NotNull IchnoisCoreOptions options = IchnoisCore.getCurrentScopes().getOptions();
    shakeDetector = new IchnoisCoreShakeDetector(options.getLogger());
    final @NotNull WeakReference<Activity> activityRef = new WeakReference<>(activity);
    shakeDetector.start(activity, shakeListener(activityRef));
    final @NotNull Application app = activity.getApplication();
    shakeLifecycleCallbacks = new ShakeLifecycleCallbacks(activityRef);
    app.registerActivityLifecycleCallbacks(shakeLifecycleCallbacks);
  }

  private void stopShakeDetection() {
    if (shakeDetector != null) {
      shakeDetector.close();
      shakeDetector = null;
    }
    if (shakeLifecycleCallbacks != null) {
      final @Nullable Activity activity = getActivity(getContext());
      if (activity != null) {
        activity.getApplication().unregisterActivityLifecycleCallbacks(shakeLifecycleCallbacks);
      }
      shakeLifecycleCallbacks = null;
    }
  }

  private @NotNull IchnoisCoreShakeDetector.Listener shakeListener(
      final @NotNull WeakReference<Activity> activityRef) {
    return () -> {
      // If shake-to-report got enabled globally in the meantime, FeedbackShakeIntegration
      // reacts to the same shake — don't show a second dialog for it.
      if (IchnoisCore.getCurrentScopes()
          .getOptions()
          .getFeedbackOptions()
          .getShakeController()
          .isOnShakeEnabled()) {
        return;
      }
      final @Nullable Activity active = activityRef.get();
      if (active != null && !active.isFinishing() && !active.isDestroyed()) {
        active.runOnUiThread(
            () -> {
              if (!active.isFinishing() && !active.isDestroyed()) {
                show();
              }
            });
      }
    };
  }

  private static @Nullable Activity getActivity(final @NotNull Context context) {
    Context current = context;
    while (current instanceof ContextWrapper) {
      if (current instanceof Activity) {
        return (Activity) current;
      }
      current = ((ContextWrapper) current).getBaseContext();
    }
    return null;
  }

  private class ShakeLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
    private final @NotNull WeakReference<Activity> activityRef;

    ShakeLifecycleCallbacks(final @NotNull WeakReference<Activity> activityRef) {
      this.activityRef = activityRef;
    }

    @Override
    public void onActivityResumed(final @NotNull Activity activity) {
      if (activity == activityRef.get() && shakeDetector != null) {
        shakeDetector.start(activity, shakeListener(activityRef));
      }
    }

    @Override
    public void onActivityPaused(final @NotNull Activity activity) {
      if (activity == activityRef.get() && shakeDetector != null) {
        shakeDetector.stop();
      }
    }

    @Override
    public void onActivityDestroyed(final @NotNull Activity activity) {
      if (activity == activityRef.get()) {
        stopShakeDetection();
      }
    }

    @Override
    public void onActivityCreated(
        final @NotNull Activity activity, final @Nullable Bundle savedInstanceState) {}

    @Override
    public void onActivityStarted(final @NotNull Activity activity) {}

    @Override
    public void onActivityStopped(final @NotNull Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(
        final @NotNull Activity activity, final @NotNull Bundle outState) {}
  }

  @Override
  public void setCancelable(boolean cancelable) {
    super.setCancelable(cancelable);
    isCancelable = cancelable;
  }

  @Override
  @SuppressWarnings("deprecation")
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.ichnois_dialog_user_feedback);
    final @Nullable Window window = getWindow();
    if (window != null) {
      window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
    }
    setCancelable(isCancelable);

    final @NotNull IchnoisCoreFeedbackOptions feedbackOptions = resolvedFeedbackOptions;
    final @NotNull TextView lblTitle = findViewById(R.id.ichnois_dialog_user_feedback_title);
    final @NotNull ImageView imgLogo = findViewById(R.id.ichnois_dialog_user_feedback_logo);
    final @NotNull TextView lblName = findViewById(R.id.ichnois_dialog_user_feedback_txt_name);
    final @NotNull EditText edtName = findViewById(R.id.ichnois_dialog_user_feedback_edt_name);
    final @NotNull TextView lblEmail = findViewById(R.id.ichnois_dialog_user_feedback_txt_email);
    final @NotNull EditText edtEmail = findViewById(R.id.ichnois_dialog_user_feedback_edt_email);
    final @NotNull TextView lblMessage =
        findViewById(R.id.ichnois_dialog_user_feedback_txt_description);
    final @NotNull EditText edtMessage =
        findViewById(R.id.ichnois_dialog_user_feedback_edt_description);
    final @NotNull Button btnSend = findViewById(R.id.ichnois_dialog_user_feedback_btn_send);
    final @NotNull Button btnCancel = findViewById(R.id.ichnois_dialog_user_feedback_btn_cancel);
    final @NotNull Button btnAddScreenshot =
        findViewById(R.id.ichnois_dialog_user_feedback_btn_add_screenshot);

    // The button is made visible in onStart, once the screenshot picker is registered successfully
    btnAddScreenshot.setVisibility(View.GONE);
    btnAddScreenshot.setOnClickListener(
        v -> {
          if (selectedImageUri == null) {
            if (screenshotPicker != null) {
              try {
                screenshotPicker.launch();
              } catch (LinkageError e) {
                // androidx.activity is compileOnly, so the version in the app's apk may not have
                // the photo picker APIs this was compiled against
                IchnoisCore.getCurrentScopes()
                    .getOptions()
                    .getLogger()
                    .log(IchnoisCoreLevel.ERROR, "Failed to launch the screenshot picker.", e);
              } catch (Throwable t) {
                ExceptionUtils.rethrowIfFatal(t);
                // e.g. no photo picker on the device, or the launcher is no longer registered
                IchnoisCore.getCurrentScopes()
                    .getOptions()
                    .getLogger()
                    .log(IchnoisCoreLevel.ERROR, "Failed to launch the screenshot picker.", t);
              }
            }
          } else {
            selectedImageUri = null;
            btnAddScreenshot.setText(feedbackOptions.getAddScreenshotButtonLabel());
          }
        });

    if (feedbackOptions.isShowBranding()) {
      imgLogo.setVisibility(View.VISIBLE);
    } else {
      imgLogo.setVisibility(View.GONE);
    }

    // If name is required, ignore showName flag
    if (!feedbackOptions.isShowName() && !feedbackOptions.isNameRequired()) {
      lblName.setVisibility(View.GONE);
      edtName.setVisibility(View.GONE);
    } else {
      lblName.setVisibility(View.VISIBLE);
      edtName.setVisibility(View.VISIBLE);
      lblName.setText(feedbackOptions.getNameLabel());
      edtName.setHint(feedbackOptions.getNamePlaceholder());
      if (feedbackOptions.isNameRequired()) {
        lblName.append(feedbackOptions.getIsRequiredLabel());
      }
    }

    // If email is required, ignore showEmail flag
    if (!feedbackOptions.isShowEmail() && !feedbackOptions.isEmailRequired()) {
      lblEmail.setVisibility(View.GONE);
      edtEmail.setVisibility(View.GONE);
    } else {
      lblEmail.setVisibility(View.VISIBLE);
      edtEmail.setVisibility(View.VISIBLE);
      lblEmail.setText(feedbackOptions.getEmailLabel());
      edtEmail.setHint(feedbackOptions.getEmailPlaceholder());
      if (feedbackOptions.isEmailRequired()) {
        lblEmail.append(feedbackOptions.getIsRequiredLabel());
      }
    }

    // If IchnoisCore user is set, and useIchnoisCoreUser is true, populate the name and email
    if (feedbackOptions.isUseIchnoisCoreUser()) {
      final @Nullable User user = IchnoisCore.getCurrentScopes().getScope().getUser();
      if (user != null) {
        edtName.setText(user.getUsername());
        edtEmail.setText(user.getEmail());
      }
    }

    lblMessage.setText(feedbackOptions.getMessageLabel());
    lblMessage.append(feedbackOptions.getIsRequiredLabel());
    edtMessage.setHint(feedbackOptions.getMessagePlaceholder());
    lblTitle.setText(feedbackOptions.getFormTitle());

    btnSend.setText(feedbackOptions.getSubmitButtonLabel());
    btnSend.setOnClickListener(
        v -> {
          // Gather fields and trim them
          final @NotNull String name = edtName.getText().toString().trim();
          final @NotNull String email = edtEmail.getText().toString().trim();
          final @NotNull String message = edtMessage.getText().toString().trim();

          // If a required field is missing, shows the error label
          if (name.isEmpty() && feedbackOptions.isNameRequired()) {
            edtName.setError(lblName.getText());
            return;
          }

          if (email.isEmpty() && feedbackOptions.isEmailRequired()) {
            edtEmail.setError(lblEmail.getText());
            return;
          }

          if (message.isEmpty()) {
            edtMessage.setError(lblMessage.getText());
            return;
          }

          // Create the feedback object
          final @NotNull Feedback feedback = new Feedback(message);
          feedback.setName(name);
          feedback.setContactEmail(email);
          if (associatedEventId != null) {
            feedback.setAssociatedEventId(associatedEventId);
          }
          if (currentReplayId != null) {
            feedback.setReplayId(currentReplayId);
          }

          // Capture the feedback. If the ID is empty, it means that the feedback was not sent
          final @NotNull Hint hint = new Hint();
          maybeAddImageAttachment(hint);
          final @NotNull IchnoisCoreId id = IchnoisCore.feedback().capture(feedback, hint);
          if (!id.equals(IchnoisCoreId.EMPTY_ID)) {
            Toast.makeText(
                    getContext(), feedbackOptions.getSuccessMessageText(), Toast.LENGTH_SHORT)
                .show();
            final @Nullable IchnoisCoreFeedbackOptions.IchnoisCoreFeedbackCallback onSubmitSuccess =
                feedbackOptions.getOnSubmitSuccess();
            if (onSubmitSuccess != null) {
              try {
                onSubmitSuccess.call(feedback);
              } catch (Exception e) {
                IchnoisCore.getCurrentScopes()
                    .getOptions()
                    .getLogger()
                    .log(IchnoisCoreLevel.ERROR, "onSubmitSuccess callback threw an exception.", e);
              }
            }
          } else {
            final @Nullable IchnoisCoreFeedbackOptions.IchnoisCoreFeedbackCallback onSubmitError =
                feedbackOptions.getOnSubmitError();
            if (onSubmitError != null) {
              try {
                onSubmitError.call(feedback);
              } catch (Exception e) {
                IchnoisCore.getCurrentScopes()
                    .getOptions()
                    .getLogger()
                    .log(IchnoisCoreLevel.ERROR, "onSubmitError callback threw an exception.", e);
              }
            }
          }
          cancel();
        });

    btnCancel.setText(feedbackOptions.getCancelButtonLabel());
    btnCancel.setOnClickListener(v -> cancel());
    setOnDismissListener(delegate);
  }

  @Override
  public void setOnDismissListener(final @Nullable OnDismissListener listener) {
    delegate = listener;
    // If the user set a custom onDismissListener, we ensure it doesn't override the onFormClose
    final @NotNull IchnoisCoreOptions options = IchnoisCore.getCurrentScopes().getOptions();
    final @Nullable Runnable onFormClose = options.getFeedbackOptions().getOnFormClose();
    if (onFormClose != null) {
      super.setOnDismissListener(
          dialog -> {
            // User-provided callback: a crash in it must not take down the app or skip the
            // cleanup and the user's own dismiss listener below
            try {
              onFormClose.run();
            } catch (Exception e) {
              options
                  .getLogger()
                  .log(IchnoisCoreLevel.ERROR, "onFormClose callback threw an exception.", e);
            }
            currentReplayId = null;
            if (delegate != null) {
              delegate.onDismiss(dialog);
            }
          });
    } else {
      super.setOnDismissListener(delegate);
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    // Clear the message field so subsequent show() calls start with a fresh dialog
    final @NotNull EditText edtMessage =
        findViewById(R.id.ichnois_dialog_user_feedback_edt_description);
    edtMessage.getText().clear();
    edtMessage.setError(null);

    final @NotNull IchnoisCoreOptions options = IchnoisCore.getCurrentScopes().getOptions();
    maybeRegisterScreenshotPicker(options);
    final @NotNull IchnoisCoreFeedbackOptions feedbackOptions = options.getFeedbackOptions();
    // Pause shake-to-report on this dialog's activity while it is visible, so a shake can't stack
    // a second dialog on top of it
    final @Nullable FeedbackShakeIntegration integration = getFeedbackShakeIntegration();
    final @Nullable Activity activity = getActivity(getContext());
    if (integration != null && activity != null) {
      integration.onDialogVisible(activity, this);
    }
    final @Nullable Runnable onFormOpen = feedbackOptions.getOnFormOpen();
    if (onFormOpen != null) {
      try {
        onFormOpen.run();
      } catch (Exception e) {
        options.getLogger().log(IchnoisCoreLevel.ERROR, "onFormOpen callback threw an exception.", e);
      }
    }
    options.getReplayController().captureReplay(false);
    currentReplayId = options.getReplayController().getReplayId();
  }

  @Override
  protected void onStop() {
    super.onStop();
    if (screenshotPicker != null) {
      screenshotPicker.unregister();
      screenshotPicker = null;
    }
    final @Nullable FeedbackShakeIntegration integration = getFeedbackShakeIntegration();
    if (integration != null) {
      integration.onDialogGone(this);
    }
  }

  private void maybeRegisterScreenshotPicker(final @NotNull IchnoisCoreOptions options) {
    // Clear any previously selected image so subsequent show() calls start with a fresh form
    final @NotNull Button btnAddScreenshot =
        findViewById(R.id.ichnois_dialog_user_feedback_btn_add_screenshot);
    selectedImageUri = null;
    btnAddScreenshot.setText(resolvedFeedbackOptions.getAddScreenshotButtonLabel());

    if (!resolvedFeedbackOptions.isEnableAttachScreenshot()) {
      return;
    }
    final @Nullable Activity activity = getActivity(getContext());
    if (activity != null && isScreenshotPickerAvailable(options)) {
      try {
        screenshotPicker =
            IchnoisCoreFeedbackScreenshotPicker.register(
                activity, uri -> onScreenshotPicked(options, btnAddScreenshot, uri));
      } catch (LinkageError e) {
        // This is where androidx.activity is linked for the first time. It is a compileOnly
        // dependency, so the version in the app's apk may be older than the one we compiled
        // against, or missing the photo picker APIs entirely.
        options
            .getLogger()
            .log(IchnoisCoreLevel.INFO, "androidx.activity is too old for the screenshot picker.", e);
      } catch (Throwable t) {
        ExceptionUtils.rethrowIfFatal(t);
        options.getLogger().log(IchnoisCoreLevel.ERROR, "Failed to register the screenshot picker.", t);
      }
    }
    if (screenshotPicker != null) {
      btnAddScreenshot.setVisibility(View.VISIBLE);
    } else {
      btnAddScreenshot.setVisibility(View.GONE);
      options
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Feedback screenshot button won't be shown. It requires the androidx.activity "
                  + "dependency and the feedback form being shown from a ComponentActivity.");
    }
  }

  /**
   * Must be called before {@link IchnoisCoreFeedbackScreenshotPicker} is touched for the first time, as
   * that class links against androidx.activity types.
   */
  private boolean isScreenshotPickerAvailable(final @NotNull IchnoisCoreOptions options) {
    final @NotNull LoadClass loadClass = resolvedFeedbackOptions.getLoadClass();
    return loadClass.isClassAvailable("androidx.activity.ComponentActivity", options)
        && loadClass.isClassAvailable(
            "androidx.activity.result.contract.ActivityResultContracts$PickVisualMedia", options);
  }

  private void onScreenshotPicked(
      final @NotNull IchnoisCoreOptions options,
      final @NotNull Button btnAddScreenshot,
      final @NotNull Uri uri) {
    final long size = getUriSize(options, getContext().getContentResolver(), uri);
    if (size > options.getMaxAttachmentSize()) {
      options
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Selected screenshot is larger than the maxAttachmentSize of %d bytes, dropping it.",
              options.getMaxAttachmentSize());
      Toast.makeText(
              getContext(),
              resolvedFeedbackOptions.getScreenshotTooLargeMessageText(),
              Toast.LENGTH_SHORT)
          .show();
      return;
    }
    selectedImageUri = uri;
    btnAddScreenshot.setText(resolvedFeedbackOptions.getRemoveScreenshotButtonLabel());
  }

  private void maybeAddImageAttachment(final @NotNull Hint hint) {
    final @Nullable Uri imageUri = selectedImageUri;
    if (imageUri == null) {
      return;
    }
    try {
      final @NotNull ContentResolver resolver = getContext().getContentResolver();
      final @Nullable String resolvedMime = resolver.getType(imageUri);
      final @NotNull String mime = resolvedMime != null ? resolvedMime : "image/png";
      final @Nullable String resolvedExt =
          MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
      final @NotNull String ext = resolvedExt != null ? resolvedExt : "png";
      hint.addAttachment(
          new Attachment(
              () ->
                  readUriBytes(
                      resolver,
                      imageUri,
                      IchnoisCore.getCurrentScopes().getOptions().getMaxAttachmentSize()),
              "screenshot." + ext,
              mime,
              "event.attachment",
              false));
    } catch (Throwable t) {
      ExceptionUtils.rethrowIfFatal(t);
      // The ContentResolver call crosses into the provider's process, which can fail in any number
      // of ways, e.g. a SecurityException once the read permission for the picked Uri was revoked
      IchnoisCore.getCurrentScopes()
          .getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.ERROR, "Failed to attach image to feedback.", t);
    }
  }

  private static long getUriSize(
      final @NotNull IchnoisCoreOptions options,
      final @NotNull ContentResolver resolver,
      final @NotNull Uri uri) {
    try (final @Nullable Cursor cursor = resolver.query(uri, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        final int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
          return cursor.getLong(sizeIndex);
        }
      }
    } catch (Throwable t) {
      ExceptionUtils.rethrowIfFatal(t);
      options
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Unable to determine the size of the selected screenshot, the attachment size limit "
                  + "is applied when the feedback is captured.",
              t);
    }
    return -1;
  }

  private static byte[] readUriBytes(
      final @NotNull ContentResolver resolver, final @NotNull Uri uri, final long maxSize)
      throws IOException {
    final @Nullable InputStream inputStream = resolver.openInputStream(uri);
    if (inputStream == null) {
      throw new IOException("Unable to open image attachment: " + uri);
    }
    // inputStreamToByteArray closes the stream
    return FileUtils.inputStreamToByteArray(inputStream, maxSize);
  }

  @Override
  public void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    // Runs on every teardown: on dismiss the decor view is removed before onStop(), and when the
    // host activity is destroyed with the dialog still showing this is the only callback that
    // fires. onDialogGone is idempotent, so reporting from both here and onStop() is safe.
    final @Nullable FeedbackShakeIntegration integration = getFeedbackShakeIntegration();
    if (integration != null) {
      integration.onDialogGone(this);
    }
  }

  /**
   * The shake integration to report this dialog's visibility to, or null when shake-to-report isn't
   * available (non-Android controller, or the integration was never installed).
   */
  private @Nullable FeedbackShakeIntegration getFeedbackShakeIntegration() {
    final @NotNull IchnoisCoreFeedbackOptions.IShakeController controller =
        IchnoisCore.getCurrentScopes().getOptions().getFeedbackOptions().getShakeController();
    return controller instanceof FeedbackShakeIntegration
        ? (FeedbackShakeIntegration) controller
        : null;
  }

  @Override
  public void show() {
    // If IchnoisCore is disabled, don't show the dialog, but log a warning
    final @NotNull IScopes scopes = IchnoisCore.getCurrentScopes();
    final @NotNull IchnoisCoreOptions options = scopes.getOptions();
    if (!scopes.isEnabled() || !options.isEnabled()) {
      options
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "IchnoisCore is disabled. Feedback dialog won't be shown.");
      return;
    }
    // Otherwise, show the dialog
    super.show();
  }

  public static class Builder {

    @Nullable OptionsConfiguration configuration;
    @Nullable IchnoisCoreFeedbackOptions.OptionsConfigurator configurator;
    @Nullable IchnoisCoreId associatedEventId;
    final @NotNull Context context;
    final int themeResId;

    /**
     * Creates a builder for a {@link IchnoisCoreUserFeedbackForm} that uses the default alert dialog
     * theme.
     *
     * <p>The default alert dialog theme is defined by {@link android.R.attr#alertDialogTheme}
     * within the parent {@code context}'s theme.
     *
     * @param context the parent context
     */
    public Builder(final @NotNull Context context) {
      this(context, 0);
    }

    /**
     * Creates a builder for a {@link IchnoisCoreUserFeedbackForm} that uses an explicit theme resource.
     *
     * <p>The specified theme resource ({@code themeResId}) is applied on top of the parent {@code
     * context}'s theme. It may be specified as a style resource containing a fully-populated theme,
     * such as {@link android.R.style#Theme_Material_Dialog}, to replace all attributes in the
     * parent {@code context}'s theme including primary and accent colors.
     *
     * <p>To preserve attributes such as primary and accent colors, the {@code themeResId} may
     * instead be specified as an overlay theme such as {@link
     * android.R.style#ThemeOverlay_Material_Dialog}. This will override only the window attributes
     * necessary to style the alert window as a dialog.
     *
     * <p>Alternatively, the {@code themeResId} may be specified as {@code 0} to use the parent
     * {@code context}'s resolved value for {@link android.R.attr#alertDialogTheme}.
     *
     * @param context the parent context
     * @param themeResId the resource ID of the theme against which to inflate this dialog, or
     *     {@code 0} to use the parent {@code context}'s default alert dialog theme
     */
    public Builder(Context context, int themeResId) {
      this(context, themeResId, null);
    }

    /**
     * Creates a builder for a {@link IchnoisCoreUserFeedbackForm} that uses the default alert dialog
     * theme. The {@code configuration} can be used to configure the feedback options for this
     * specific dialog.
     *
     * <p>The default alert dialog theme is defined by {@link android.R.attr#alertDialogTheme}
     * within the parent {@code context}'s theme.
     *
     * @param context the parent context
     * @param configuration the configuration for the feedback options, can be {@code null} to use
     *     the global feedback options.
     */
    public Builder(
        final @NotNull Context context, final @Nullable OptionsConfiguration configuration) {
      this(context, 0, configuration);
    }

    /**
     * Creates a builder for a {@link IchnoisCoreUserFeedbackForm} that uses an explicit theme resource.
     * The {@code configuration} can be used to configure the feedback options for this specific
     * dialog.
     *
     * <p>The specified theme resource ({@code themeResId}) is applied on top of the parent {@code
     * context}'s theme. It may be specified as a style resource containing a fully-populated theme,
     * such as {@link android.R.style#Theme_Material_Dialog}, to replace all attributes in the
     * parent {@code context}'s theme including primary and accent colors.
     *
     * <p>To preserve attributes such as primary and accent colors, the {@code themeResId} may
     * instead be specified as an overlay theme such as {@link
     * android.R.style#ThemeOverlay_Material_Dialog}. This will override only the window attributes
     * necessary to style the alert window as a dialog.
     *
     * <p>Alternatively, the {@code themeResId} may be specified as {@code 0} to use the parent
     * {@code context}'s resolved value for {@link android.R.attr#alertDialogTheme}.
     *
     * @param context the parent context
     * @param themeResId the resource ID of the theme against which to inflate this dialog, or
     *     {@code 0} to use the parent {@code context}'s default alert dialog theme
     * @param configuration the configuration for the feedback options, can be {@code null} to use
     *     the global feedback options.
     */
    public Builder(
        final @NotNull Context context,
        final int themeResId,
        final @Nullable OptionsConfiguration configuration) {
      this.context = context;
      this.themeResId = themeResId;
      this.configuration = configuration;
    }

    /**
     * Sets the configuration for the feedback options.
     *
     * @param configurator the configuration for the feedback options, can be {@code null} to use
     *     the global feedback options.
     */
    public Builder configurator(
        final @Nullable IchnoisCoreFeedbackOptions.OptionsConfigurator configurator) {
      this.configurator = configurator;
      return this;
    }

    /**
     * Sets the associated event ID for the feedback.
     *
     * @param associatedEventId the associated event ID for the feedback, can be {@code null} to
     *     avoid associating the feedback to an event.
     */
    public Builder associatedEventId(final @Nullable IchnoisCoreId associatedEventId) {
      this.associatedEventId = associatedEventId;
      return this;
    }

    /**
     * Builds a new {@link IchnoisCoreUserFeedbackForm} with the specified context, theme, and
     * configuration.
     *
     * @return a new instance of {@link IchnoisCoreUserFeedbackForm}
     */
    public IchnoisCoreUserFeedbackForm create() {
      return new IchnoisCoreUserFeedbackForm(
          context, themeResId, associatedEventId, configuration, configurator);
    }
  }

  /** Configuration callback for feedback options. */
  public interface OptionsConfiguration {

    /**
     * configure the feedback options
     *
     * @param context the context of the feedback dialog
     * @param options the feedback options
     */
    void configure(final @NotNull Context context, final @NotNull IchnoisCoreFeedbackOptions options);
  }
}
