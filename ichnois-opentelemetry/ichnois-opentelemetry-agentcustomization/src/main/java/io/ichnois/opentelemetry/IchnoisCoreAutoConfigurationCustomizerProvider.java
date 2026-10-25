package io.ichnois.opentelemetry;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.ichnois.InitPriority;
import io.ichnois.IchnoisCore;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.internal.ManifestVersionReader;
import io.ichnois.protocol.SdkVersion;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IchnoisCoreAutoConfigurationCustomizerProvider
    implements AutoConfigurationCustomizerProvider {

  private static final Logger logger =
      Logger.getLogger(IchnoisCoreAutoConfigurationCustomizerProvider.class.getName());

  public static volatile boolean skipInit = false;

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    ensureIchnoisCoreOtelStorageIsInitialized();
    customizeOpenTelemetryDefaults();
    final @Nullable ManifestVersionReader.VersionInfoHolder versionInfoHolder =
        ManifestVersionReader.getInstance().readOpenTelemetryVersion();

    if (isIchnoisCoreAutoInitEnabled()) {
      IchnoisCore.init(
          options -> {
            options.setEnableExternalConfiguration(true);
            options.setInitPriority(InitPriority.HIGH);
            final @Nullable SdkVersion sdkVersion = createSdkVersion(options, versionInfoHolder);
            if (sdkVersion != null) {
              options.setSdkVersion(sdkVersion);
            }
          });
    }

    autoConfiguration
        .addTracerProviderCustomizer(this::configureSdkTracerProvider)
        .addPropertiesSupplier(this::getDefaultProperties);
  }

  private static void ensureIchnoisCoreOtelStorageIsInitialized() {
    /*
    accessing IchnoisCore.something will cause ScopesStorageFactory to run,
    which causes OtelContextScopesStorage.init to register IchnoisCoreContextStorage
    as a wrapper. The wrapper can only be set until storage has been initialized by OpenTelemetry.
    */
    IchnoisCore.getGlobalScope();
  }

  private void customizeOpenTelemetryDefaults() {
    try {
      if (System.getProperty("otel.instrumentation.graphql.add-operation-name-to-span-name.enabled")
          == null) {
        System.setProperty(
            "otel.instrumentation.graphql.add-operation-name-to-span-name.enabled", "true");
      }
    } catch (Exception e) {
      logger.log(Level.WARNING, "Unable to change OpenTelemetry defaults for use with IchnoisCore.", e);
    }
  }

  private boolean isIchnoisCoreAutoInitEnabled() {
    if (skipInit) {
      return false;
    }
    final @Nullable String ichnoisAutoInit = System.getenv("ICHNOIS_AUTO_INIT");

    if (ichnoisAutoInit != null) {
      return "true".equalsIgnoreCase(ichnoisAutoInit);
    } else {
      final @Nullable String ichnoisPropertiesFile = System.getenv("ICHNOIS_PROPERTIES_FILE");
      final @Nullable String ichnoisDsn = System.getenv("ICHNOIS_DSN");

      return ichnoisPropertiesFile != null || ichnoisDsn != null;
    }
  }

  private @Nullable SdkVersion createSdkVersion(
      final @NotNull IchnoisCoreOptions ichnoisOptions,
      final @Nullable ManifestVersionReader.VersionInfoHolder versionInfoHolder) {
    SdkVersion sdkVersion = ichnoisOptions.getSdkVersion();

    if (versionInfoHolder != null
        && versionInfoHolder.getSdkName() != null
        && versionInfoHolder.getSdkVersion() != null) {
      sdkVersion =
          SdkVersion.updateSdkVersion(
              sdkVersion, versionInfoHolder.getSdkName(), versionInfoHolder.getSdkVersion());
    }
    return sdkVersion;
  }

  private SdkTracerProviderBuilder configureSdkTracerProvider(
      SdkTracerProviderBuilder tracerProvider, ConfigProperties config) {
    return tracerProvider
        .setSampler(new IchnoisCoreSampler())
        .addSpanProcessor(new OtelIchnoisCoreSpanProcessor())
        .addSpanProcessor(BatchSpanProcessor.builder(new IchnoisCoreSpanExporter()).build());
  }

  private Map<String, String> getDefaultProperties() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.propagators", "ichnois");
    return properties;
  }
}
