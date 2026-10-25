package io.ichnois.asyncprofiler.provider;

import io.ichnois.IContinuousProfiler;
import io.ichnois.ILogger;
import io.ichnois.IIchnoisCoreExecutorService;
import io.ichnois.NoOpContinuousProfiler;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.asyncprofiler.profiling.JavaContinuousProfiler;
import io.ichnois.profiling.JavaContinuousProfilerProvider;
import io.ichnois.profiling.JavaProfileConverterProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * AsyncProfiler implementation of {@link JavaContinuousProfilerProvider} and {@link
 * JavaProfileConverterProvider}. This provider integrates AsyncProfiler with IchnoisCore's continuous
 * profiling system and provides profile conversion functionality.
 */
@ApiStatus.Internal
public final class AsyncProfilerContinuousProfilerProvider
    implements JavaContinuousProfilerProvider {

  @Override
  public @NotNull IContinuousProfiler getContinuousProfiler(
      ILogger logger,
      String profilingTracesDirPath,
      int profilingTracesHz,
      IIchnoisCoreExecutorService executorService) {
    try {
      return new JavaContinuousProfiler(
          logger, profilingTracesDirPath, profilingTracesHz, executorService);
    } catch (Exception e) {
      logger.log(
          IchnoisCoreLevel.WARNING,
          "Failed to initialize AsyncProfiler. Profiling will be disabled.",
          e);
      return NoOpContinuousProfiler.getInstance();
    }
  }
}
