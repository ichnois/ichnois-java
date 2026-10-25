package io.ichnois.spring.boot;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.IScopes;
import io.ichnois.spring.tracing.IchnoisCoreSpanClientWebRequestFilter;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
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
