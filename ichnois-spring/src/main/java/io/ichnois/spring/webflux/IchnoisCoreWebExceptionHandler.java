package io.ichnois.spring.webflux;

import static io.ichnois.TypeCheckHint.WEBFLUX_EXCEPTION_HANDLER_EXCHANGE;
import static io.ichnois.TypeCheckHint.WEBFLUX_EXCEPTION_HANDLER_REQUEST;
import static io.ichnois.TypeCheckHint.WEBFLUX_EXCEPTION_HANDLER_RESPONSE;

import io.ichnois.Hint;
import io.ichnois.IScopes;
import io.ichnois.IchnoisCoreEvent;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.exception.ExceptionMechanismException;
import io.ichnois.protocol.Mechanism;
import io.ichnois.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/** Handles unhandled exceptions in Spring WebFlux integration. */
@Order(
    -2) // the DefaultErrorWebExceptionHandler provided by Spring Boot for error handling is ordered
// at -1
@ApiStatus.Experimental
public final class IchnoisCoreWebExceptionHandler implements WebExceptionHandler {
  public static final String MECHANISM_TYPE = "Spring5WebFluxExceptionResolver";
  private final @NotNull IScopes scopes;

  public IchnoisCoreWebExceptionHandler(final @NotNull IScopes scopes) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
  }

  @Override
  public @NotNull Mono<Void> handle(
      final @NotNull ServerWebExchange serverWebExchange, final @NotNull Throwable ex) {
    if (!(ex instanceof ResponseStatusException)) {
      final Mechanism mechanism = new Mechanism();
      mechanism.setType(MECHANISM_TYPE);
      mechanism.setHandled(false);
      final Throwable throwable =
          new ExceptionMechanismException(mechanism, ex, Thread.currentThread());
      final IchnoisCoreEvent event = new IchnoisCoreEvent(throwable);
      event.setLevel(IchnoisCoreLevel.FATAL);
      event.setTransaction(TransactionNameProvider.provideTransactionName(serverWebExchange));

      final Hint hint = new Hint();
      hint.set(WEBFLUX_EXCEPTION_HANDLER_REQUEST, serverWebExchange.getRequest());
      hint.set(WEBFLUX_EXCEPTION_HANDLER_RESPONSE, serverWebExchange.getResponse());
      hint.set(WEBFLUX_EXCEPTION_HANDLER_EXCHANGE, serverWebExchange);

      scopes.captureEvent(event, hint);
    }
    return Mono.error(ex);
  }
}
