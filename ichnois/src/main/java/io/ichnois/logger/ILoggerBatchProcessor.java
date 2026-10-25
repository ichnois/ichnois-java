package io.ichnois.logger;

import io.ichnois.IchnoisCoreLogEvent;
import org.jetbrains.annotations.NotNull;

public interface ILoggerBatchProcessor {
  void add(@NotNull IchnoisCoreLogEvent event);

  void close(boolean isRestarting);

  /**
   * Flushes log events.
   *
   * @param timeoutMillis time in milliseconds
   */
  void flush(long timeoutMillis);
}
