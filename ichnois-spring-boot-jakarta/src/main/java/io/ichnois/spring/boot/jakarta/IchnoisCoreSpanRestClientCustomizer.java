package io.ichnois.spring.boot.jakarta;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.IScopes;
import io.ichnois.spring.jakarta.tracing.IchnoisCoreSpanClientHttpRequestInterceptor;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.web.client.RestClient;

@Open
class IchnoisCoreSpanRestClientCustomizer implements RestClientCustomizer {
  private final @NotNull IchnoisCoreSpanClientHttpRequestInterceptor interceptor;

  public IchnoisCoreSpanRestClientCustomizer(final @NotNull IScopes scopes) {
    this.interceptor = new IchnoisCoreSpanClientHttpRequestInterceptor(scopes, false);
  }

  @Override
  public void customize(final @NotNull RestClient.Builder restClientBuilder) {
    restClientBuilder.requestInterceptors(
        clientHttpRequestInterceptors -> {
          // As the IchnoisCoreSpanClientHttpRequestInterceptor is being created in this class, this
          // might not work
          // if somebody registers it from an outside.
          if (!clientHttpRequestInterceptors.contains(interceptor)) {
            clientHttpRequestInterceptors.add(interceptor);
          }
        });
  }
}
