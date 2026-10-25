package io.ichnois.profiling;

import io.ichnois.IContinuousProfiler;
import io.ichnois.ILogger;
import io.ichnois.IProfileConverter;
import io.ichnois.IIchnoisCoreExecutorService;
import io.ichnois.NoOpContinuousProfiler;
import io.ichnois.NoOpProfileConverter;
import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCoreLevel;
import java.util.Iterator;
import java.util.ServiceLoader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ProfilingServiceLoader {

  public static @NotNull IContinuousProfiler loadContinuousProfiler(
      ILogger logger,
      String profilingTracesDirPath,
      int profilingTracesHz,
      IIchnoisCoreExecutorService executorService) {
    try {
      JavaContinuousProfilerProvider provider =
          loadSingleProvider(JavaContinuousProfilerProvider.class);

      if (provider != null) {
        logger.log(
            IchnoisCoreLevel.DEBUG,
            "Loaded continuous profiler from provider: %s",
            provider.getClass().getName());
        return provider.getContinuousProfiler(
            logger, profilingTracesDirPath, profilingTracesHz, executorService);
      }

      logger.log(
          IchnoisCoreLevel.DEBUG, "No continuous profiler provider found, using NoOpContinuousProfiler");
      return NoOpContinuousProfiler.getInstance();
    } catch (Throwable t) {
      logger.log(
          IchnoisCoreLevel.ERROR,
          "Failed to load continuous profiler provider, using NoOpContinuousProfiler",
          t);
      return NoOpContinuousProfiler.getInstance();
    }
  }

  /**
   * Loads a profile converter using ServiceLoader discovery pattern.
   *
   * @return an IProfileConverter instance or null if no provider is found
   */
  public static @NotNull IProfileConverter loadProfileConverter() {
    ILogger logger = ScopesAdapter.getInstance().getGlobalScope().getOptions().getLogger();
    try {
      JavaProfileConverterProvider provider =
          loadSingleProvider(JavaProfileConverterProvider.class);
      if (provider != null) {
        logger.log(
            IchnoisCoreLevel.DEBUG,
            "Loaded profile converter from provider: %s",
            provider.getClass().getName());
        return provider.getProfileConverter();
      } else {
        logger.log(
            IchnoisCoreLevel.DEBUG, "No profile converter provider found, using NoOpProfileConverter");
        return NoOpProfileConverter.getInstance();
      }
    } catch (Throwable t) {
      logger.log(
          IchnoisCoreLevel.ERROR,
          "Failed to load profile converter provider, using NoOpProfileConverter",
          t);
      return NoOpProfileConverter.getInstance();
    }
  }

  // TODO: Verify caching behaviour of ServiceLoader
  private static @Nullable <T> T loadSingleProvider(Class<T> clazz) {
    final ServiceLoader<T> serviceLoader = ServiceLoader.load(clazz);
    final Iterator<T> iterator = serviceLoader.iterator();

    if (iterator.hasNext()) {
      return iterator.next();
    } else {
      return null;
    }
  }
}
