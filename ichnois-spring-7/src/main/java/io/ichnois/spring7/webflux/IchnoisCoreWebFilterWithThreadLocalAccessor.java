package io.ichnois.spring7.webflux;

import io.ichnois.IScope;
import io.ichnois.IScopes;
import io.ichnois.ITransaction;
import io.ichnois.IchnoisCore;
import io.ichnois.reactor.IchnoisCoreReactorUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/** Manages {@link IScope} in Webflux request processing. */
@ApiStatus.Experimental
public final class IchnoisCoreWebFilterWithThreadLocalAccessor extends AbstractIchnoisCoreWebFilter {

  public static final String TRACE_ORIGIN = "auto.spring7.webflux";

  public IchnoisCoreWebFilterWithThreadLocalAccessor(final @NotNull IScopes scopes) {
    super(scopes);
  }

  @Override
  public Mono<Void> filter(
      final @NotNull ServerWebExchange serverWebExchange,
      final @NotNull WebFilterChain webFilterChain) {
    final @NotNull TransactionContainer transactionContainer = new TransactionContainer();
    return IchnoisCoreReactorUtils.withIchnoisCoreForkedRoots(
        webFilterChain
            .filter(serverWebExchange)
            .doFinally(
                __ ->
                    doFinally(
                        serverWebExchange,
                        IchnoisCore.getCurrentScopes(),
                        transactionContainer.transaction))
            .doOnError(e -> doOnError(transactionContainer.transaction, e))
            .doFirst(
                () -> {
                  doFirst(serverWebExchange, IchnoisCore.getCurrentScopes());
                  final ITransaction transaction =
                      maybeStartTransaction(
                          IchnoisCore.getCurrentScopes(), serverWebExchange.getRequest(), TRACE_ORIGIN);
                  transactionContainer.transaction = transaction;
                }));
  }

  private static class TransactionContainer {
    private volatile @Nullable ITransaction transaction;
  }
}
