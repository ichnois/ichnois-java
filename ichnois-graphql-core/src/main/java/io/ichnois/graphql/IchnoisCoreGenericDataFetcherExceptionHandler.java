package io.ichnois.graphql;

import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import io.ichnois.IScopes;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Captures exceptions that occur during data fetching, passes them to IchnoisCore and invokes a delegate
 * exception handler.
 */
public final class IchnoisCoreGenericDataFetcherExceptionHandler implements DataFetcherExceptionHandler {
  private final @NotNull IchnoisCoreGraphqlExceptionHandler handler;

  public IchnoisCoreGenericDataFetcherExceptionHandler(
      final @Nullable IScopes scopes, final @NotNull DataFetcherExceptionHandler delegate) {
    this.handler = new IchnoisCoreGraphqlExceptionHandler(delegate);
  }

  public IchnoisCoreGenericDataFetcherExceptionHandler(
      final @NotNull DataFetcherExceptionHandler delegate) {
    this(null, delegate);
  }

  @SuppressWarnings("deprecation")
  public @Nullable DataFetcherExceptionHandlerResult onException(
      final @NotNull DataFetcherExceptionHandlerParameters handlerParameters) {
    CompletableFuture<DataFetcherExceptionHandlerResult> futureResult =
        handleException(handlerParameters);

    if (futureResult != null) {
      try {
        return futureResult.get();
      } catch (InterruptedException | ExecutionException e) {
        return null;
      }
    } else {
      return null;
    }
  }

  @Override
  public @Nullable CompletableFuture<DataFetcherExceptionHandlerResult> handleException(
      DataFetcherExceptionHandlerParameters handlerParameters) {
    return handler.handleException(
        handlerParameters.getException(),
        handlerParameters.getDataFetchingEnvironment(),
        handlerParameters);
  }
}
