package io.ichnois.spring.boot4;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.IScopes;
import io.ichnois.spring7.tracing.IchnoisCoreSpanClientHttpRequestInterceptor;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.restclient.RestClientCustomizer;
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
