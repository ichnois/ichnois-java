package io.ichnois.spring7.opentelemetry;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.IchnoisCore;
import io.ichnois.IchnoisCoreIntegrationPackageStorage;
import io.ichnois.IchnoisCoreOpenTelemetryMode;
import io.ichnois.IchnoisCoreOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@Open
public class IchnoisCoreOpenTelemetryAgentWithoutAutoInitConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "ichnoisOpenTelemetryOptionsConfiguration")
  public @NotNull IchnoisCore.OptionsConfiguration<IchnoisCoreOptions>
      ichnoisOpenTelemetryOptionsConfiguration() {
    return options -> {
      IchnoisCoreIntegrationPackageStorage.getInstance()
          .addIntegration("SpringBoot4OpenTelemetryAgentWithoutAutoInit");
      options.setOpenTelemetryMode(IchnoisCoreOpenTelemetryMode.AGENT);
    };
  }
}
