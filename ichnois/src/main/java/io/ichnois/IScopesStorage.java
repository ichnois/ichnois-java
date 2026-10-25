package io.ichnois;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface IScopesStorage {

  void init();

  @NotNull
  IIchnoisCoreLifecycleToken set(final @Nullable IScopes scopes);

  @Nullable
  IScopes get();

  void close();
}
