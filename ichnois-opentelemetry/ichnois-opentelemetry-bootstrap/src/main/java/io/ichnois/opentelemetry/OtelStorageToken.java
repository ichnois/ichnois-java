package io.ichnois.opentelemetry;

import io.opentelemetry.context.Scope;
import io.ichnois.IIchnoisCoreLifecycleToken;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
final class OtelStorageToken implements IIchnoisCoreLifecycleToken {

  private final @NotNull Scope otelScope;

  OtelStorageToken(final @NotNull Scope otelScope) {
    this.otelScope = otelScope;
  }

  @Override
  public void close() {
    otelScope.close();
  }
}
