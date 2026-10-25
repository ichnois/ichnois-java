package io.ichnois.spring.boot.jakarta;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.IScopes;
import io.ichnois.spring.jakarta.tracing.IchnoisCoreSpanClientHttpRequestInterceptor;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestTemplate;

@Open
class IchnoisCoreSpanRestTemplateCustomizer implements RestTemplateCustomizer {
  private final @NotNull IchnoisCoreSpanClientHttpRequestInterceptor interceptor;

  public IchnoisCoreSpanRestTemplateCustomizer(final @NotNull IScopes scopes) {
    this.interceptor = new IchnoisCoreSpanClientHttpRequestInterceptor(scopes);
  }

  @Override
  public void customize(final @NotNull RestTemplate restTemplate) {
    final List<ClientHttpRequestInterceptor> existingInterceptors = restTemplate.getInterceptors();
    if (!existingInterceptors.contains(this.interceptor)) {
      final List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
      interceptors.add(this.interceptor);
      interceptors.addAll(existingInterceptors);
      restTemplate.setInterceptors(interceptors);
    }
  }
}
