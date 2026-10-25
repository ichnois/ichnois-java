package io.ichnois.logger;

import io.ichnois.IchnoisCoreClient;
import io.ichnois.IchnoisCoreOptions;
import org.jetbrains.annotations.NotNull;

public final class DefaultLoggerBatchProcessorFactory implements ILoggerBatchProcessorFactory {
  @Override
  public @NotNull ILoggerBatchProcessor create(
      @NotNull IchnoisCoreOptions options, @NotNull IchnoisCoreClient client) {
    return new LoggerBatchProcessor(options, client);
  }
}
