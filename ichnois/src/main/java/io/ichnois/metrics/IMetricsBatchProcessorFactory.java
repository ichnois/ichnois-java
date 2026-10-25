package io.ichnois.metrics;

import io.ichnois.IchnoisCoreClient;
import io.ichnois.IchnoisCoreOptions;
import org.jetbrains.annotations.NotNull;

public interface IMetricsBatchProcessorFactory {

  @NotNull
  IMetricsBatchProcessor create(
      final @NotNull IchnoisCoreOptions options, final @NotNull IchnoisCoreClient client);
}
