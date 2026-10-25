package io.ichnois;

import io.ichnois.util.Objects;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Resolves {@link RequestDetails}. */
@ApiStatus.Experimental
public final class RequestDetailsResolver {
  /** HTTP Header for the user agent. */
  private static final String USER_AGENT = "User-Agent";

  /** HTTP Header for the authentication to IchnoisCore. */
  private static final String ICHNOIS_AUTH = "X-Sentry-Auth";

  private final @NotNull Dsn dsn;
  private final @Nullable String ichnoisClientName;

  public RequestDetailsResolver(
      final @NotNull String dsn, final @Nullable String ichnoisClientName) {
    Objects.requireNonNull(dsn, "dsn is required");

    this.dsn = new Dsn(dsn);
    this.ichnoisClientName = ichnoisClientName;
  }

  @ApiStatus.Internal
  public RequestDetailsResolver(final @NotNull IchnoisCoreOptions options) {
    Objects.requireNonNull(options, "options is required");

    this.dsn = options.retrieveParsedDsn();
    this.ichnoisClientName = options.getIchnoisClientName();
  }

  @NotNull
  public RequestDetails resolve() {
    final URI ichnoisUri = dsn.getIchnoisCoreUri();
    final String envelopeUrl = ichnoisUri.resolve(ichnoisUri.getPath() + "/envelope/").toString();

    final String publicKey = dsn.getPublicKey();
    final String secretKey = dsn.getSecretKey();

    final String authHeader =
        "Sentry sentry_version="
            + IchnoisCoreClient.ICHNOIS_PROTOCOL_VERSION
            + ","
            + "sentry_client="
            + ichnoisClientName
            + ","
            + "sentry_key="
            + publicKey
            + (secretKey != null && secretKey.length() > 0 ? (",sentry_secret=" + secretKey) : "");

    final Map<String, String> headers = new HashMap<>();
    headers.put(USER_AGENT, ichnoisClientName);
    headers.put(ICHNOIS_AUTH, authHeader);

    return new RequestDetails(envelopeUrl, headers);
  }
}
