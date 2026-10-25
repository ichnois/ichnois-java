package io.ichnois.metrics;

import io.ichnois.IchnoisCoreMetricsEvent;
import org.jetbrains.annotations.NotNull;

public interface IMetricsBatchProcessor {
  void add(@NotNull IchnoisCoreMetricsEvent event);

  void close(boolean isRestarting);

  /**
   * Flushes metrics.
   *
   * @param timeoutMillis time in milliseconds
   */
  void flush(long timeoutMillis);
}
