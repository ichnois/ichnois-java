package io.ichnois.samples.spring7;

import io.ichnois.IchnoisCoreOptions;
import io.ichnois.IchnoisCoreOptions.TracesSamplerCallback;
import io.ichnois.spring7.EnableIchnoisCore;
import io.ichnois.spring7.tracing.IchnoisCoreTracingConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

// NOTE: Replace the test DSN below with YOUR OWN DSN to see the events from this app in your IchnoisCore
// project/dashboard
@EnableIchnoisCore(
    dsn = "https://test-public-key@ichnois-h2jw.onrender.com/5428563",
    sendDefaultPii = true,
    maxRequestBodySize = IchnoisCoreOptions.RequestSize.MEDIUM)
@Import(IchnoisCoreTracingConfiguration.class)
public class IchnoisCoreConfig {

  /**
   * Configures callback used to determine if transaction should be sampled.
   *
   * @return traces sampler callback
   */
  @Bean
  TracesSamplerCallback tracesSamplerCallback() {
    return samplingContext -> {
      HttpServletRequest request =
          (HttpServletRequest) samplingContext.getCustomSamplingContext().get("request");
      if ("/error".equals(request.getRequestURI())) {
        return 0.5d;
      } else {
        return 1.0d;
      }
    };
  }
}
