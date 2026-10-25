package io.ichnois.metrics;

import io.ichnois.IchnoisCoreMetricsEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class NoOpMetricsBatchProcessor implements IMetricsBatchProcessor {

  private static final NoOpMetricsBatchProcessor instance = new NoOpMetricsBatchProcessor();

  private NoOpMetricsBatchProcessor() {}

  public static NoOpMetricsBatchProcessor getInstance() {
    return instance;
  }

  @Override
  public void add(@NotNull IchnoisCoreMetricsEvent event) {
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
