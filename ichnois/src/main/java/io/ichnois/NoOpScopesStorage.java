package io.ichnois;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NoOpScopesStorage implements IScopesStorage {
  private static final NoOpScopesStorage instance = new NoOpScopesStorage();

  private NoOpScopesStorage() {}

  public static NoOpScopesStorage getInstance() {
    return instance;
  }

  @Override
  public void init() {}

  @Override
  public IIchnoisCoreLifecycleToken set(@Nullable IScopes scopes) {
    return NoOpScopesLifecycleToken.getInstance();
  }

  @Override
  public @Nullable IScopes get() {
    return NoOpScopes.getInstance();
  }

  @Override
  public void close() {}
}
