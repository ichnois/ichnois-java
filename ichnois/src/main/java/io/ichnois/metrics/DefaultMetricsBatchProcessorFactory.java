package io.ichnois.metrics;

import io.ichnois.IchnoisCoreClient;
import io.ichnois.IchnoisCoreOptions;
import org.jetbrains.annotations.NotNull;

public final class DefaultMetricsBatchProcessorFactory implements IMetricsBatchProcessorFactory {
  @Override
  public @NotNull IMetricsBatchProcessor create(
      final @NotNull IchnoisCoreOptions options, final @NotNull IchnoisCoreClient client) {
    return new MetricsBatchProcessor(options, client);
  }
}
