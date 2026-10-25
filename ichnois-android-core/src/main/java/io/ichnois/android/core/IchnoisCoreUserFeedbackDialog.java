package io.ichnois.android.core;

import android.content.Context;
import io.ichnois.IchnoisCoreFeedbackOptions;
import io.ichnois.protocol.IchnoisCoreId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated Use {@link IchnoisCoreUserFeedbackForm} instead.
 */
@Deprecated
public final class IchnoisCoreUserFeedbackDialog extends IchnoisCoreUserFeedbackForm {

  IchnoisCoreUserFeedbackDialog(
      final @NotNull Context context,
      final int themeResId,
      final @Nullable IchnoisCoreId associatedEventId,
      final @Nullable IchnoisCoreUserFeedbackForm.OptionsConfiguration configuration,
      final @Nullable IchnoisCoreFeedbackOptions.OptionsConfigurator configurator) {
    super(context, themeResId, associatedEventId, configuration, configurator);
  }

  /**
   * @deprecated Use {@link IchnoisCoreUserFeedbackForm.Builder} instead.
   */
  @Deprecated
  public static class Builder extends IchnoisCoreUserFeedbackForm.Builder {

    /**
     * Creates a builder for a {@link IchnoisCoreUserFeedbackDialog} that uses the default alert dialog
     * theme.
     *
     * @param context the parent context
     * @deprecated Use {@link IchnoisCoreUserFeedbackForm.Builder#Builder(Context)} instead.
     */
    @Deprecated
    public Builder(final @NotNull Context context) {
      super(context);
    }

    /**
     * Creates a builder for a {@link IchnoisCoreUserFeedbackDialog} that uses an explicit theme
     * resource.
     *
     * @param context the parent context
     * @param themeResId the resource ID of the theme
     * @deprecated Use {@link IchnoisCoreUserFeedbackForm.Builder#Builder(Context, int)} instead.
     */
    @Deprecated
    public Builder(Context context, int themeResId) {
      super(context, themeResId);
    }

    /**
     * Creates a builder for a {@link IchnoisCoreUserFeedbackDialog} with a configuration.
     *
     * @param context the parent context
     * @param configuration the configuration for the feedback options
     * @deprecated Use {@link IchnoisCoreUserFeedbackForm.Builder#Builder(Context,
     *     IchnoisCoreUserFeedbackForm.OptionsConfiguration)} instead.
     */
    @Deprecated
    public Builder(
        final @NotNull Context context, final @Nullable OptionsConfiguration configuration) {
      super(context, configuration);
    }

    /**
     * Creates a builder for a {@link IchnoisCoreUserFeedbackDialog} with a theme and configuration.
     *
     * @param context the parent context
     * @param themeResId the resource ID of the theme
     * @param configuration the configuration for the feedback options
     * @deprecated Use {@link IchnoisCoreUserFeedbackForm.Builder#Builder(Context, int,
     *     IchnoisCoreUserFeedbackForm.OptionsConfiguration)} instead.
     */
    @Deprecated
    public Builder(
        final @NotNull Context context,
        final int themeResId,
        final @Nullable OptionsConfiguration configuration) {
      super(context, themeResId, configuration);
    }

    @Deprecated
    @Override
    public Builder configurator(
        final @Nullable IchnoisCoreFeedbackOptions.OptionsConfigurator configurator) {
      super.configurator(configurator);
      return this;
    }

    @Deprecated
    @Override
    public Builder associatedEventId(final @Nullable IchnoisCoreId associatedEventId) {
      super.associatedEventId(associatedEventId);
      return this;
    }

    @Deprecated
    @Override
    public IchnoisCoreUserFeedbackDialog create() {
      return new IchnoisCoreUserFeedbackDialog(
          context, themeResId, associatedEventId, configuration, configurator);
    }
  }

  /**
   * @deprecated Use {@link IchnoisCoreUserFeedbackForm.OptionsConfiguration} instead.
   */
  @Deprecated
  public interface OptionsConfiguration extends IchnoisCoreUserFeedbackForm.OptionsConfiguration {}
}
