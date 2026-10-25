package io.ichnois.android.core;

import io.ichnois.IConnectionStatusProvider;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.transport.ITransportGate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

final class AndroidTransportGate implements ITransportGate {

  private final @NotNull IchnoisCoreOptions options;

  AndroidTransportGate(final @NotNull IchnoisCoreOptions options) {
    this.options = options;
  }

  @Override
  public boolean isConnected() {
    return isConnected(options.getConnectionStatusProvider().getConnectionStatus());
  }

  @TestOnly
  boolean isConnected(final @NotNull IConnectionStatusProvider.ConnectionStatus status) {
    // let's assume its connected if there's no permission or something as we can't really know
    // whether is online or not.
    switch (status) {
      case CONNECTED:
      case UNKNOWN:
      case NO_PERMISSION:
        return true;
      default:
        return false;
    }
  }
}
