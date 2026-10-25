package io.ichnois.logger;

import io.ichnois.IchnoisCoreLogEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class NoOpLoggerBatchProcessor implements ILoggerBatchProcessor {

  private static final NoOpLoggerBatchProcessor instance = new NoOpLoggerBatchProcessor();

  private NoOpLoggerBatchProcessor() {}

  public static NoOpLoggerBatchProcessor getInstance() {
    return instance;
  }

  @Override
  public void add(@NotNull IchnoisCoreLogEvent event) {
    // do nothing
  }

  @Override
  public void close(final boolean isRestarting) {
    // do nothing
  }

  @Override
  public void flush(long timeoutMillis) {
    // do nothing
  }
}
