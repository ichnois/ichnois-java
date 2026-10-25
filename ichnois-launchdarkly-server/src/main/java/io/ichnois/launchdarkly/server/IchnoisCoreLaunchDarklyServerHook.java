package io.ichnois.launchdarkly.server;

import static io.ichnois.util.IntegrationUtils.addIntegrationToSdkVersion;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.LDValueType;
import com.launchdarkly.sdk.server.integrations.EvaluationSeriesContext;
import com.launchdarkly.sdk.server.integrations.Hook;
import io.ichnois.IScopes;
import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCoreIntegrationPackageStorage;
import io.ichnois.IchnoisCoreLevel;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public final class IchnoisCoreLaunchDarklyServerHook extends Hook {
  private final IScopes scopes;

  static {
    IchnoisCoreIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.ichnois:ichnois-launchdarkly-server", BuildConfig.VERSION_NAME);
  }

  public IchnoisCoreLaunchDarklyServerHook() {
    this(ScopesAdapter.getInstance());
  }

  @VisibleForTesting
  IchnoisCoreLaunchDarklyServerHook(@NotNull IScopes scopes) {
    super("IchnoisCoreLaunchDarklyServerHook");
    this.scopes = Objects.requireNonNull(scopes, "Scopes are required");
    addPackageAndIntegrationInfo();
  }

  private void addPackageAndIntegrationInfo() {
    addIntegrationToSdkVersion("LaunchDarkly-Server");
  }

  @Override
  public Map<String, Object> afterEvaluation(
      EvaluationSeriesContext seriesContext,
      Map<String, Object> seriesData,
      EvaluationDetail<LDValue> evaluationDetail) {
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
    } catch (Exception e) {
      scopes
          .getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.ERROR, "Failed to capture feature flag evaluation", e);
    }

    return seriesData;
  }
}
