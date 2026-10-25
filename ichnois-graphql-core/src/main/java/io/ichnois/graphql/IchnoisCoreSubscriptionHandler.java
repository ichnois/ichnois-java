package io.ichnois.graphql;

import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import io.ichnois.IScopes;
import org.jetbrains.annotations.NotNull;

public interface IchnoisCoreSubscriptionHandler {
  @NotNull
  Object onSubscriptionResult(
      @NotNull Object result,
      @NotNull IScopes scopes,
      @NotNull ExceptionReporter exceptionReporter,
      @NotNull InstrumentationFieldFetchParameters parameters);
}
