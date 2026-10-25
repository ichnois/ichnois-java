package io.ichnois.spring7.webflux;

import static io.ichnois.TypeCheckHint.WEBFLUX_FILTER_REQUEST;
import static io.ichnois.TypeCheckHint.WEBFLUX_FILTER_RESPONSE;

import io.ichnois.BaggageHeader;
import io.ichnois.Breadcrumb;
import io.ichnois.CustomSamplingContext;
import io.ichnois.Hint;
import io.ichnois.IScope;
import io.ichnois.IScopes;
import io.ichnois.ITransaction;
import io.ichnois.NoOpScopes;
import io.ichnois.IchnoisCore;
import io.ichnois.IchnoisCoreTraceHeader;
import io.ichnois.SpanStatus;
import io.ichnois.TransactionContext;
import io.ichnois.TransactionOptions;
import io.ichnois.protocol.TransactionNameSource;
import io.ichnois.util.Objects;
import io.ichnois.util.SpanUtils;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;

/** Manages {@link IScope} in Webflux request processing. */
@ApiStatus.Experimental
public abstract class AbstractIchnoisCoreWebFilter implements WebFilter {
  private final @NotNull IchnoisCoreRequestResolver ichnoisRequestResolver;
  public static final String ICHNOIS_SCOPES_KEY = "ichnois-scopes";

  /**
   * @deprecated please use {@link AbstractIchnoisCoreWebFilter#ICHNOIS_SCOPES_KEY} instead.
   */
  @Deprecated public static final String ICHNOIS_HUB_KEY = ICHNOIS_SCOPES_KEY;

  private static final String TRANSACTION_OP = "http.server";

  public AbstractIchnoisCoreWebFilter(final @NotNull IScopes scopes) {
    Objects.requireNonNull(scopes, "scopes are required");
    this.ichnoisRequestResolver = new IchnoisCoreRequestResolver(scopes);
  }

  protected @Nullable ITransaction maybeStartTransaction(
      final @NotNull IScopes requestScopes,
      final @NotNull ServerHttpRequest request,
      final @NotNull String origin) {
    if (requestScopes.isEnabled() && !isIgnored(requestScopes, origin)) {
      final @NotNull HttpHeaders headers = request.getHeaders();
      final @Nullable String ichnoisTraceHeader =
          headers.getFirst(IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER);
      final @Nullable List<String> baggageHeaders = headers.get(BaggageHeader.BAGGAGE_HEADER);
      final @Nullable TransactionContext transactionContext =
          requestScopes.continueTrace(ichnoisTraceHeader, baggageHeaders);

      if (requestScopes.getOptions().isTracingEnabled()
          && shouldTraceRequest(requestScopes, request)) {
        return startTransaction(requestScopes, request, transactionContext, origin);
      }
    }

    return null;
  }

  private boolean isIgnored(final @NotNull IScopes scopes, final @NotNull String origin) {
    return SpanUtils.isIgnored(scopes.getOptions().getIgnoredSpanOrigins(), origin);
  }

  protected void doFinally(
      final @NotNull ServerWebExchange serverWebExchange,
      final @NotNull IScopes requestScopes,
      final @Nullable ITransaction transaction) {
    if (transaction != null) {
      finishTransaction(serverWebExchange, transaction);
    }
    IchnoisCore.setCurrentScopes(NoOpScopes.getInstance());
  }

  protected void doFirst(
      final @NotNull ServerWebExchange serverWebExchange, final @NotNull IScopes requestScopes) {
    if (requestScopes.isEnabled()) {
      serverWebExchange.getAttributes().put(ICHNOIS_SCOPES_KEY, requestScopes);
      final ServerHttpRequest request = serverWebExchange.getRequest();
      final ServerHttpResponse response = serverWebExchange.getResponse();

      final Hint hint = new Hint();
      hint.set(WEBFLUX_FILTER_REQUEST, request);
      hint.set(WEBFLUX_FILTER_RESPONSE, response);
      final String methodName =
          request.getMethod() != null ? request.getMethod().name() : "unknown";
      final @NotNull Breadcrumb breadcrumb =
          Breadcrumb.http(
              request.getURI().toString(),
              methodName,
              null,
              requestScopes.getOptions().getDataCollectionResolver());
      requestScopes.addBreadcrumb(breadcrumb, hint);
      requestScopes.configureScope(
          scope -> scope.setRequest(ichnoisRequestResolver.resolveIchnoisCoreRequest(request)));
    }
  }

  protected void doOnError(final @Nullable ITransaction transaction, final @NotNull Throwable e) {
    if (transaction != null) {
      transaction.setStatus(SpanStatus.INTERNAL_ERROR);
      transaction.setThrowable(e);
    }
  }

  protected boolean shouldTraceRequest(
      final @NotNull IScopes scopes, final @NotNull ServerHttpRequest request) {
    return scopes.getOptions().isTraceOptionsRequests()
        || !HttpMethod.OPTIONS.equals(request.getMethod());
  }

  private void finishTransaction(ServerWebExchange exchange, ITransaction transaction) {
    String transactionName = TransactionNameProvider.provideTransactionName(exchange);
    if (transactionName != null) {
      transaction.setName(transactionName, TransactionNameSource.ROUTE);
      transaction.setOperation(TRANSACTION_OP);
    }
    final @Nullable ServerHttpResponse response = exchange.getResponse();
    if (response != null) {
      final @Nullable HttpStatusCode statusCode = response.getStatusCode();
      if (statusCode != null) {
        transaction
            .getContexts()
            .withResponse(
                (ichnoisResponse) -> {
                  ichnoisResponse.setStatusCode(statusCode.value());
                });
        if (transaction.getStatus() == null) {
          transaction.setStatus(SpanStatus.fromHttpStatusCode(statusCode.value()));
        }
      }
    }
    transaction.finish();
  }

  protected @NotNull ITransaction startTransaction(
      final @NotNull IScopes scopes,
      final @NotNull ServerHttpRequest request,
      final @Nullable TransactionContext transactionContext,
      final @NotNull String origin) {
    final @NotNull String name = request.getMethod() + " " + request.getURI().getPath();
    final @NotNull CustomSamplingContext customSamplingContext = new CustomSamplingContext();
    customSamplingContext.set("request", request);

    final TransactionOptions transactionOptions = new TransactionOptions();
    transactionOptions.setCustomSamplingContext(customSamplingContext);
    transactionOptions.setBindToScope(true);
    transactionOptions.setOrigin(origin);

    if (transactionContext != null) {
      transactionContext.setName(name);
      transactionContext.setTransactionNameSource(TransactionNameSource.URL);
      transactionContext.setOperation(TRANSACTION_OP);

      return scopes.startTransaction(transactionContext, transactionOptions);
    }

    return scopes.startTransaction(
        new TransactionContext(name, TransactionNameSource.URL, TRANSACTION_OP),
        transactionOptions);
  }
}
