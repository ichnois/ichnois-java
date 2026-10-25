package io.ichnois.android.core;

import io.ichnois.IchnoisCoreClient;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.metrics.IMetricsBatchProcessor;
import io.ichnois.metrics.IMetricsBatchProcessorFactory;
import org.jetbrains.annotations.NotNull;

public final class AndroidMetricsBatchProcessorFactory implements IMetricsBatchProcessorFactory {
  @Override
  public @NotNull IMetricsBatchProcessor create(
      final @NotNull IchnoisCoreOptions options, final @NotNull IchnoisCoreClient client) {
    return new AndroidMetricsBatchProcessor(options, client);
  }
}
