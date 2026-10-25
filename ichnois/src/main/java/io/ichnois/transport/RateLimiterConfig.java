package io.ichnois.transport;

import io.ichnois.ILogger;
import io.ichnois.IIchnoisCoreExecutorService;
import io.ichnois.clientreport.IClientReportRecorder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * The configuration {@link RateLimiter} reads. Declared next to its consumer rather than alongside
 * the implementation, so that the collaborators a rate limiter actually touches are three lines to
 * read instead of three hundred, and a test can supply them without building a {@link
 * io.ichnois.IchnoisCoreOptions}.
 *
 * <p>Implementations are expected to delegate to live configuration rather than snapshot it, so
 * that a logger or executor replaced after {@code IchnoisCore.init} is still picked up.
 */
@ApiStatus.Internal
public interface RateLimiterConfig {

  @NotNull
  ILogger getLogger();

  @NotNull
  IClientReportRecorder getClientReportRecorder();

  @NotNull
  IIchnoisCoreExecutorService getTimerExecutorService();
}
