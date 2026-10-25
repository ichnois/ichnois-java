package io.ichnois.spring7.graphql;

import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import io.ichnois.IScopes;
import io.ichnois.IchnoisCoreIntegrationPackageStorage;
import io.ichnois.graphql.ExceptionReporter;
import io.ichnois.graphql.IchnoisCoreSubscriptionHandler;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;

public final class IchnoisCoreDgsSubscriptionHandler implements IchnoisCoreSubscriptionHandler {

  public IchnoisCoreDgsSubscriptionHandler() {
    IchnoisCoreIntegrationPackageStorage.getInstance().addIntegration("Spring7NetflixDGSGraphQL");
  }

  @Override
  public @NotNull Object onSubscriptionResult(
      final @NotNull Object result,
      final @NotNull IScopes scopes,
      final @NotNull ExceptionReporter exceptionReporter,
      final @NotNull InstrumentationFieldFetchParameters parameters) {
    if (result instanceof Flux) {
      final @NotNull Flux<?> flux = (Flux<?>) result;
      return flux.doOnError(
          throwable -> {
            final @NotNull ExceptionReporter.ExceptionDetails exceptionDetails =
                new ExceptionReporter.ExceptionDetails(scopes, parameters.getEnvironment(), true);
            exceptionReporter.captureThrowable(throwable, exceptionDetails, null);
          });
    }
    return result;
  }
}
