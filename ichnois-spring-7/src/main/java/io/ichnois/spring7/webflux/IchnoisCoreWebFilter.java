package io.ichnois.spring7.webflux;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.IScope;
import io.ichnois.IScopes;
import io.ichnois.ITransaction;
import io.ichnois.IchnoisCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Manages {@link IScope} in Webflux request processing. */
@ApiStatus.Experimental
@Open
public class IchnoisCoreWebFilter extends AbstractIchnoisCoreWebFilter {

  private static final String TRACE_ORIGIN = "auto.spring7.webflux";

  public IchnoisCoreWebFilter(final @NotNull IScopes scopes) {
    super(scopes);
  }

  @Override
  public Mono<Void> filter(
      final @NotNull ServerWebExchange serverWebExchange,
      final @NotNull WebFilterChain webFilterChain) {
    @NotNull IScopes requestScopes = IchnoisCore.forkedRootScopes("request.webflux");
    final ServerHttpRequest request = serverWebExchange.getRequest();
    final @Nullable ITransaction transaction =
        maybeStartTransaction(requestScopes, request, TRACE_ORIGIN);
    return webFilterChain
        .filter(serverWebExchange)
        .doFinally(__ -> doFinally(serverWebExchange, requestScopes, transaction))
        .doOnError(e -> doOnError(transaction, e))
        .doFirst(
            () -> {
              IchnoisCore.setCurrentScopes(requestScopes);
              doFirst(serverWebExchange, requestScopes);
            });
  }
}
