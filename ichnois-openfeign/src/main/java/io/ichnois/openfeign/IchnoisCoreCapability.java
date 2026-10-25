package io.ichnois.openfeign;

import feign.Capability;
import feign.Client;
import io.ichnois.IScopes;
import io.ichnois.ScopesAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Adds IchnoisCore tracing capability to Feign clients. */
public final class IchnoisCoreCapability implements Capability {

  private final @NotNull IScopes scopes;
  private final @Nullable IchnoisCoreFeignClient.BeforeSpanCallback beforeSpan;

  public IchnoisCoreCapability(
      final @NotNull IScopes scopes,
      final @Nullable IchnoisCoreFeignClient.BeforeSpanCallback beforeSpan) {
    this.scopes = scopes;
    this.beforeSpan = beforeSpan;
  }

  public IchnoisCoreCapability(final @Nullable IchnoisCoreFeignClient.BeforeSpanCallback beforeSpan) {
    this(ScopesAdapter.getInstance(), beforeSpan);
  }

  public IchnoisCoreCapability() {
    this(ScopesAdapter.getInstance(), null);
  }

  @Override
  public @NotNull Client enrich(final @NotNull Client client) {
    return new IchnoisCoreFeignClient(client, scopes, beforeSpan);
  }
}
