package io.ichnois;

import java.net.URI;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class DsnUtil {

  /**
   * Returns the host of the given DSN, parsed the same way as the DSN the SDK sends envelopes to,
   * or {@code null} if no host can be found. Unlike {@link IchnoisCoreOptions#retrieveParsedDsn()}, this
   * doesn't create a {@link URI}, so it's cheap enough to call on the main thread.
   */
  public static @Nullable String getHost(final @Nullable String dsn) {
    return Dsn.parseHost(dsn);
  }

  public static boolean urlContainsDsnHost(@Nullable IchnoisCoreOptions options, @Nullable String url) {
    if (options == null) {
      return false;
    }

    if (url == null) {
      return false;
    }

    final @Nullable String dsnString = options.getDsn();
    if (dsnString == null) {
      return false;
    }

    final @NotNull Dsn dsn = options.retrieveParsedDsn();
    final @NotNull URI ichnoisUri = dsn.getIchnoisCoreUri();
    final @Nullable String dsnHost = ichnoisUri.getHost();

    if (dsnHost == null) {
      return false;
    }

    final @NotNull String lowerCaseHost = dsnHost.toLowerCase(Locale.ROOT);
    final int dsnPort = ichnoisUri.getPort();

    if (dsnPort > 0) {
      return url.toLowerCase(Locale.ROOT).contains(lowerCaseHost + ":" + dsnPort);
    } else {
      return url.toLowerCase(Locale.ROOT).contains(lowerCaseHost);
    }
  }
}
