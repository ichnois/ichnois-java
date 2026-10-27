package io.ichnois.launchdarkly.android;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;
import com.launchdarkly.sdk.android.integrations.EvaluationSeriesContext;
import com.launchdarkly.sdk.android.integrations.Hook;
import io.ichnois.IScopes;
import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCoreIntegrationPackageStorage;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.util.IntegrationUtils;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IchnoisCoreLaunchDarklyAndroidHook extends Hook {
  private final IScopes scopes;

  static {
    IchnoisCoreIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.github.ichnois:ichnois-launchdarkly-android", BuildConfig.VERSION_NAME);
  }

  public IchnoisCoreLaunchDarklyAndroidHook() {
    this(ScopesAdapter.getInstance());
  }

  public IchnoisCoreLaunchDarklyAndroidHook(final @NotNull IScopes scopes) {
    super("IchnoisCoreLaunchDarklyAndroidHook");
    this.scopes = Objects.requireNonNull(scopes, "Scopes are required");
    addPackageAndIntegrationInfo();
  }

  private void addPackageAndIntegrationInfo() {
    IntegrationUtils.addIntegrationToSdkVersion("LaunchDarkly-Android");
  }

  @Override
  public Map<String, Object> afterEvaluation(
      final EvaluationSeriesContext seriesContext,
      final Map<String, Object> seriesData,
      final EvaluationDetail<LDValue> evaluationDetail) {
    if (evaluationDetail == null || seriesContext == null) {
      return seriesData;
    }

    try {
      final @Nullable String flagKey = seriesContext.flagKey;
      final @Nullable LDValue value = evaluationDetail.getValue();

      if (flagKey == null || value == null) {
        return seriesData;
      }

      if (LDValueType.BOOLEAN.equals(value.getType())) {
        final boolean flagValue = value.booleanValue();
        scopes.addFeatureFlag(flagKey, flagValue);
      }
    } catch (final Exception e) {
      scopes
          .getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.ERROR, "Failed to capture feature flag evaluation", e);
    }

    return seriesData;
  }
}
