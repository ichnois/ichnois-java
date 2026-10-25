package io.ichnois.logger;

import io.ichnois.IchnoisCoreClient;
import io.ichnois.IchnoisCoreOptions;
import org.jetbrains.annotations.NotNull;

public interface ILoggerBatchProcessorFactory {

  @NotNull
  ILoggerBatchProcessor create(
      final @NotNull IchnoisCoreOptions options, final @NotNull IchnoisCoreClient client);
}
