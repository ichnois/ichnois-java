package io.ichnois.opentelemetry;

import static io.ichnois.opentelemetry.IchnoisCoreOtelKeys.ICHNOIS_SCOPES_KEY;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.ichnois.IScopes;
import io.ichnois.IScopesStorage;
import io.ichnois.IIchnoisCoreLifecycleToken;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
@SuppressWarnings("MustBeClosedChecker")
public final class OtelContextScopesStorage implements IScopesStorage {

  @Override
  public void init() {
    /*
     * We're currently overriding the storage mechanism to allow for cleanup of non closed OTel
     * scopes. These happen when using e.g. IchnoisCore static API due to getCurrentScopes() invoking
     * Context.makeCurrent and then ignoring the returned lifecycle token (OTel Scope). After fixing
     * the classloader problem (ichnois bootstrap dependency is currently in agent classloader) we
     * can revisit and try again to set the storage instead of overriding it in the wrapper. We
     * should try to use OTels StorageProvider mechanism instead.
     */
    //    ContextStorage.addWrapper((storage) -> new IchnoisCoreContextStorage(storage));
    //    ContextStorage.addWrapper(
    //        (storage) -> new IchnoisCoreContextStorage(new IchnoisCoreOtelThreadLocalStorage()));
  }

  @Override
  public @NotNull IIchnoisCoreLifecycleToken set(@Nullable IScopes scopes) {
    final Context context = Context.current();
    final @NotNull Scope otelScope = context.with(ICHNOIS_SCOPES_KEY, scopes).makeCurrent();
    return new OtelStorageToken(otelScope);
  }

  @Override
  public @Nullable IScopes get() {
    final Context context = Context.current();
    return context.get(ICHNOIS_SCOPES_KEY);
  }

  @Override
  public void close() {}
}
