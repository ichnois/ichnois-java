package io.ichnois.spring.jakarta;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.IContinuousProfiler;
import io.ichnois.IProfileConverter;
import io.ichnois.NoOpContinuousProfiler;
import io.ichnois.NoOpProfileConverter;
import io.ichnois.IchnoisCore;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.util.InitUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Handles late initialization of the profiler if the application is run with the OTEL Agent in
 * auto-init mode. In that case the agent cannot initialize the profiler yet and falls back to No-Op
 * implementations. This Configuration sets the profiler and converter on the options if that was
 * the case.
 */
@Configuration(proxyBeanMethods = false)
@Open
public class IchnoisCoreProfilerConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "ichnoisOpenTelemetryProfilerConfiguration")
  public IContinuousProfiler ichnoisOpenTelemetryProfilerConfiguration() {
    IchnoisCoreOptions options = IchnoisCore.getGlobalScope().getOptions();
    IContinuousProfiler profiler = NoOpContinuousProfiler.getInstance();

    if (IchnoisCore.isEnabled()) {
      return InitUtil.initializeProfiler(options);
    } else {
      return profiler;
    }
  }

  @Bean
  @ConditionalOnMissingBean(name = "ichnoisOpenTelemetryProfilerConverterConfiguration")
  public IProfileConverter ichnoisOpenTelemetryProfilerConverterConfiguration() {
    IchnoisCoreOptions options = IchnoisCore.getGlobalScope().getOptions();
    IProfileConverter converter = NoOpProfileConverter.getInstance();

    if (IchnoisCore.isEnabled()) {
      return InitUtil.initializeProfileConverter(options);
    } else {
      return converter;
    }
  }
}
