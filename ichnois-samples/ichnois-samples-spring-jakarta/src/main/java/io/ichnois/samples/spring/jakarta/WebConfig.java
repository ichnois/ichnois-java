package io.ichnois.samples.spring.jakarta;

import io.ichnois.IScopes;
import io.ichnois.spring.jakarta.tracing.IchnoisCoreSpanClientHttpRequestInterceptor;
import java.util.Collections;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
@ComponentScan("io.ichnois.samples.spring.jakarta")
@EnableWebMvc
public class WebConfig {

  /**
   * Creates a {@link RestTemplate} which calls are intercepted with {@link
   * IchnoisCoreSpanClientHttpRequestInterceptor} to create spans around HTTP calls.
   *
   * @param scopes - ichnois scopes
   * @return RestTemplate
   */
  @Bean
  RestTemplate restTemplate(IScopes scopes) {
    RestTemplate restTemplate = new RestTemplate();
    IchnoisCoreSpanClientHttpRequestInterceptor ichnoisRestTemplateInterceptor =
        new IchnoisCoreSpanClientHttpRequestInterceptor(scopes);
    restTemplate.setInterceptors(Collections.singletonList(ichnoisRestTemplateInterceptor));
    return restTemplate;
  }
}
