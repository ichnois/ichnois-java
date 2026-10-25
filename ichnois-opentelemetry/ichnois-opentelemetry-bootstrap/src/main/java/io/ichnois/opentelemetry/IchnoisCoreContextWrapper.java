package io.ichnois.opentelemetry;

import static io.ichnois.opentelemetry.IchnoisCoreOtelKeys.ICHNOIS_SCOPES_KEY;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.ichnois.IScopes;
import io.ichnois.IchnoisCore;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class IchnoisCoreContextWrapper implements Context {

  private final @NotNull Context delegate;

  private IchnoisCoreContextWrapper(final @NotNull Context delegate) {
    this.delegate = delegate;
  }

  @Override
  public <V> V get(final @NotNull ContextKey<V> contextKey) {
    return delegate.get(contextKey);
  }

  @Override
  public <V> Context with(final @NotNull ContextKey<V> contextKey, V v) {
    final @NotNull Context modifiedContext = delegate.with(contextKey, v);

    if (isOpentelemetrySpan(contextKey)) {
      return forkCurrentScope(modifiedContext);
    } else {
      return new IchnoisCoreContextWrapper(modifiedContext);
    }
  }

  private <V> boolean isOpentelemetrySpan(final @NotNull ContextKey<V> contextKey) {
    return "opentelemetry-trace-span-key".equals(contextKey.toString());
  }

  private static @NotNull Context forkCurrentScope(final @NotNull Context context) {
    final @Nullable IOtelSpanWrapper ichnoisSpan = getCurrentSpanFromGlobalStorage(context);
    final @Nullable IScopes spanScopes = ichnoisSpan == null ? null : ichnoisSpan.getScopes();
    final @NotNull IScopes forkedScopes = forkCurrentScopeInternal(context, spanScopes);
    if (ichnoisSpan != null) {
      forkedScopes.setActiveSpan(ichnoisSpan);
    }
    if (forkedScopes.isNoOp()) {
      return context;
    } else {
      return context.with(ICHNOIS_SCOPES_KEY, forkedScopes);
    }
  }

  private static @NotNull IScopes forkCurrentScopeInternal(
      final @NotNull Context context, final @Nullable IScopes spanScopes) {
    final @Nullable IScopes scopesInContext = context.get(ICHNOIS_SCOPES_KEY);

    if (scopesInContext != null && spanScopes != null) {
      if (scopesInContext.isAncestorOf(spanScopes)) {
        return spanScopes.forkedCurrentScope("contextwrapper.spanancestor");
      }
    }

    if (scopesInContext != null) {
      return scopesInContext.forkedCurrentScope("contextwrapper.scopeincontext");
    }

    if (spanScopes != null) {
      return spanScopes.forkedCurrentScope("contextwrapper.spanscope");
    }

    return IchnoisCore.forkedRootScopes("contextwrapper.fallback");
  }

  private static @Nullable IOtelSpanWrapper getCurrentSpanFromGlobalStorage(
      final @NotNull Context context) {
    @Nullable final Span span = Span.fromContextOrNull(context);

    if (span != null) {
      final @Nullable IOtelSpanWrapper ichnoisSpan =
          IchnoisCoreWeakSpanStorage.getInstance().getIchnoisCoreSpan(span.getSpanContext());
      return ichnoisSpan;
    }

    return null;
  }

  public static @NotNull IchnoisCoreContextWrapper wrap(final @NotNull Context context) {
    // we have to fork here because the first time we get to wrap a context it may already have a
    // span and a scope
    return new IchnoisCoreContextWrapper(forkCurrentScope(context));
  }

  @Override
  public String toString() {
    return delegate.toString();
  }
}
