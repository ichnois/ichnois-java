package io.ichnois;

import io.ichnois.transport.ITransport;
import org.jetbrains.annotations.NotNull;

/** Creates instances of {@link ITransport}. */
public interface ITransportFactory {
  /**
   * Creates an instance of {@link ITransport}.
   *
   * @param options ichnois configuration that can be used to create transport
   * @param requestDetails http request properties that must be applied to http request invoked by
   *     the transport
   * @return the transport
   */
  @NotNull
  ITransport create(
      final @NotNull IchnoisCoreOptions options, final @NotNull RequestDetails requestDetails);
}
