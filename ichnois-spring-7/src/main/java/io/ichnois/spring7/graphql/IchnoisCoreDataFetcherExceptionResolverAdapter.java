package io.ichnois.spring7.graphql;

import graphql.GraphQLError;
import graphql.execution.DataFetcherExceptionHandlerResult;
import graphql.schema.DataFetchingEnvironment;
import io.ichnois.graphql.IchnoisCoreGraphqlExceptionHandler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;

@ApiStatus.Internal
public final class IchnoisCoreDataFetcherExceptionResolverAdapter
    extends DataFetcherExceptionResolverAdapter {
  private final @NotNull IchnoisCoreGraphqlExceptionHandler handler;

  public IchnoisCoreDataFetcherExceptionResolverAdapter() {
    this.handler = new IchnoisCoreGraphqlExceptionHandler(null);
  }

  @Override
  public boolean isThreadLocalContextAware() {
    return true;
  }

  @Override
  protected @Nullable GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
    List<GraphQLError> errors = resolveToMultipleErrors(ex, env);
    if (errors != null && !errors.isEmpty()) {
      return errors.get(0);
    }
    return null;
  }

  @Override
  protected @Nullable List<GraphQLError> resolveToMultipleErrors(
      Throwable ex, DataFetchingEnvironment env) {
    @Nullable
    CompletableFuture<DataFetcherExceptionHandlerResult> result =
        handler.handleException(ex, env, null);
    if (result != null) {
      return result.join().getErrors();
    }
    return null;
  }
}
