package io.ichnois.spring7.opentelemetry;

import com.jakewharton.nopen.annotation.Open;
import io.opentelemetry.api.OpenTelemetry;
import io.ichnois.ISpanFactory;
import io.ichnois.IchnoisCore;
import io.ichnois.IchnoisCoreIntegrationPackageStorage;
import io.ichnois.IchnoisCoreOpenTelemetryMode;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.opentelemetry.OtelSpanFactory;
import io.ichnois.opentelemetry.IchnoisCoreAutoConfigurationCustomizerProvider;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@Open
public class IchnoisCoreOpenTelemetryNoAgentConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public static ISpanFactory openTelemetrySpanFactory(OpenTelemetry openTelemetry) {
    return new OtelSpanFactory(openTelemetry);
  }

  @Bean
  @ConditionalOnMissingBean(name = "ichnoisOpenTelemetryOptionsConfiguration")
  public @NotNull IchnoisCore.OptionsConfiguration<IchnoisCoreOptions>
      ichnoisOpenTelemetryOptionsConfiguration() {
    return options -> {
      IchnoisCoreIntegrationPackageStorage.getInstance()
          .addIntegration("SpringBoot4OpenTelemetryNoAgent");
      IchnoisCoreAutoConfigurationCustomizerProvider.skipInit = true;
      options.setOpenTelemetryMode(IchnoisCoreOpenTelemetryMode.AGENTLESS_SPRING);
    };
  }
}
