package io.ichnois.spring.boot4;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.IScopes;
import io.ichnois.spring7.tracing.IchnoisCoreSpanClientWebRequestFilter;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.web.reactive.function.client.WebClient;

@Open
class IchnoisCoreSpanWebClientCustomizer implements WebClientCustomizer {
  private final @NotNull IchnoisCoreSpanClientWebRequestFilter filter;

  public IchnoisCoreSpanWebClientCustomizer(final @NotNull IScopes scopes) {
    this.filter = new IchnoisCoreSpanClientWebRequestFilter(scopes);
  }

  @Override
  public void customize(WebClient.Builder webClientBuilder) {
    webClientBuilder.filter(this.filter);
  }
}
