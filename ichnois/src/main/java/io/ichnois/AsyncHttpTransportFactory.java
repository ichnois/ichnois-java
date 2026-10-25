package io.ichnois;

import io.ichnois.transport.AsyncHttpTransport;
import io.ichnois.transport.ITransport;
import io.ichnois.transport.RateLimiter;
import io.ichnois.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/** Creates {@link AsyncHttpTransport}. */
@ApiStatus.Internal
public final class AsyncHttpTransportFactory implements ITransportFactory {

  @Override
  public @NotNull ITransport create(
      final @NotNull IchnoisCoreOptions options, final @NotNull RequestDetails requestDetails) {
    Objects.requireNonNull(options, "options is required");
    Objects.requireNonNull(requestDetails, "requestDetails is required");

    return new AsyncHttpTransport(
        options,
        RateLimiter.create(options.getMonotonicTicker(), options),
        options.getTransportGate(),
        requestDetails);
  }
}
