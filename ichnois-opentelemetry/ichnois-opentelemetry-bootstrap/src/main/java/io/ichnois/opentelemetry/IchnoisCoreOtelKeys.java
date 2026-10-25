package io.ichnois.opentelemetry;

import io.opentelemetry.context.ContextKey;
import io.ichnois.Baggage;
import io.ichnois.IScopes;
import io.ichnois.IchnoisCoreTraceHeader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class IchnoisCoreOtelKeys {

  public static final @NotNull ContextKey<IchnoisCoreTraceHeader> ICHNOIS_TRACE_KEY =
      ContextKey.named("ichnois.trace");
  public static final @NotNull ContextKey<Baggage> ICHNOIS_BAGGAGE_KEY =
      ContextKey.named("ichnois.baggage");
  public static final @NotNull ContextKey<IScopes> ICHNOIS_SCOPES_KEY =
      ContextKey.named("ichnois.scopes");
}
