package io.ichnois.exception;

import io.ichnois.IchnoisCoreTraceHeader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thrown when {@link IchnoisCoreTraceHeader} fails to create because of the invalid value of the
 * "ichnois-trace" header field.
 */
public final class InvalidIchnoisCoreTraceHeaderException extends Exception {
  private static final long serialVersionUID = -8353316997083420940L;
  private final @NotNull String ichnoisTraceHeader;

  public InvalidIchnoisCoreTraceHeaderException(final @NotNull String ichnoisTraceHeader) {
    this(ichnoisTraceHeader, null);
  }

  public InvalidIchnoisCoreTraceHeaderException(
      final @NotNull String ichnoisTraceHeader, final @Nullable Throwable cause) {
    super("ichnois-trace header does not conform to expected format: " + ichnoisTraceHeader, cause);
    this.ichnoisTraceHeader = ichnoisTraceHeader;
  }

  public @NotNull String getIchnoisCoreTraceHeader() {
    return ichnoisTraceHeader;
  }
}
