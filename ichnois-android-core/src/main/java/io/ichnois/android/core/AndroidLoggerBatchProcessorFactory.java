package io.ichnois.android.core;

import io.ichnois.IchnoisCoreClient;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.logger.ILoggerBatchProcessor;
import io.ichnois.logger.ILoggerBatchProcessorFactory;
import org.jetbrains.annotations.NotNull;

public final class AndroidLoggerBatchProcessorFactory implements ILoggerBatchProcessorFactory {
  @Override
  public @NotNull ILoggerBatchProcessor create(
      @NotNull IchnoisCoreOptions options, @NotNull IchnoisCoreClient client) {
    return new AndroidLoggerBatchProcessor(options, client);
  }
}
