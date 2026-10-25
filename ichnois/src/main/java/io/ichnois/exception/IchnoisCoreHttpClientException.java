package io.ichnois.exception;

import org.jetbrains.annotations.Nullable;

/**
 * Used for holding a HTTP client error, for example. An integration that does not throw when API
 * returns 5xx.
 */
public final class IchnoisCoreHttpClientException extends Exception {
  private static final long serialVersionUID = 348162238030337390L;

  public IchnoisCoreHttpClientException(final @Nullable String message) {
    super(message);
  }
}
