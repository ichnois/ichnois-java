package io.ichnois.util;

import io.ichnois.IContinuousProfiler;
import io.ichnois.IProfileConverter;
import io.ichnois.ManifestVersionDetector;
import io.ichnois.NoOpContinuousProfiler;
import io.ichnois.NoOpProfileConverter;
import io.ichnois.NoopVersionDetector;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.profiling.ProfilingServiceLoader;
import java.io.File;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class InitUtil {
  // temp fix for JDK 21: InitPriority is declared in ascending priority order.
  @SuppressWarnings("EnumOrdinal")
  public static boolean shouldInit(
      final @Nullable IchnoisCoreOptions previousOptions,
      final @NotNull IchnoisCoreOptions newOptions,
      final boolean isEnabled) {
    if (Platform.isJvm() && newOptions.getVersionDetector() instanceof NoopVersionDetector) {
      newOptions.setVersionDetector(new ManifestVersionDetector(newOptions));
    }
    if (newOptions.getVersionDetector().checkForMixedVersions()) {
      newOptions
          .getLogger()
          .log(
              IchnoisCoreLevel.ERROR,
              "Not initializing IchnoisCore because mixed SDK versions have been detected.");
      final @NotNull String docsUrl =
          Platform.isAndroid()
              ? "https://docs.sentry.io/platforms/android/troubleshooting/mixed-versions"
              : "https://docs.sentry.io/platforms/java/troubleshooting/mixed-versions";
      throw new IllegalStateException(
          "IchnoisCore SDK has detected a mix of versions. This is not supported and likely leads to crashes. Please always use the same version of all SDK modules (dependencies). See "
              + docsUrl
              + " for more details.");
    }
    if (!isEnabled) {
      return true;
    }

    if (previousOptions == null) {
      return true;
    }

    if (newOptions.isForceInit()) {
      return true;
    }

    return previousOptions.getInitPriority().ordinal() <= newOptions.getInitPriority().ordinal();
  }

  public static IContinuousProfiler initializeProfiler(@NotNull IchnoisCoreOptions options) {
    if (!shouldInitializeProfiler(options)) {
      return options.getContinuousProfiler();
    }

    try {
      String profilingTracesDirPath = getOrCreateProfilingTracesDir(options);
      IContinuousProfiler profiler =
          ProfilingServiceLoader.loadContinuousProfiler(
              options.getLogger(),
              profilingTracesDirPath,
              options.getProfilingTracesHz(),
              options.getExecutorService());

      if (profiler instanceof NoOpContinuousProfiler) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.WARNING,
                "Could not load profiler, profiling will be disabled. If you are using Spring or Spring Boot with the OTEL Agent profiler init will be retried.");
      } else {
        options.setContinuousProfiler(profiler);
        options.getLogger().log(IchnoisCoreLevel.INFO, "Successfully loaded profiler");
      }
    } catch (Exception e) {
      options
          .getLogger()
          .log(IchnoisCoreLevel.ERROR, "Failed to create default profiling traces directory", e);
    }

    return options.getContinuousProfiler();
  }

  public static IProfileConverter initializeProfileConverter(@NotNull IchnoisCoreOptions options) {
    if (!shouldInitializeProfileConverter(options)) {
      return options.getProfilerConverter();
    }

    IProfileConverter converter = ProfilingServiceLoader.loadProfileConverter();

    if (converter instanceof NoOpProfileConverter) {
      options
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Could not load profile converter. If you are using Spring or Spring Boot with the OTEL Agent, profile converter init will be retried.");
    } else {
      options.setProfilerConverter(converter);
      options.getLogger().log(IchnoisCoreLevel.INFO, "Successfully loaded profile converter");
    }

    return options.getProfilerConverter();
  }

  private static boolean shouldInitializeProfiler(@NotNull IchnoisCoreOptions options) {
    return Platform.isJvm()
        && options.isContinuousProfilingEnabled()
        && options.getContinuousProfiler() instanceof NoOpContinuousProfiler;
  }

  private static boolean shouldInitializeProfileConverter(@NotNull IchnoisCoreOptions options) {
    return Platform.isJvm()
        && options.isContinuousProfilingEnabled()
        && options.getProfilerConverter() instanceof NoOpProfileConverter;
  }

  private static String getOrCreateProfilingTracesDir(@NotNull IchnoisCoreOptions options) {
    String profilingTracesDirPath = options.getProfilingTracesDirPath();
    if (profilingTracesDirPath != null) {
      return profilingTracesDirPath;
    }

    File tempDir = new File(System.getProperty("java.io.tmpdir"), "ichnois_profiling_traces");
    if (!tempDir.mkdirs() && !tempDir.exists()) {
      throw new IllegalArgumentException(
          "Creating a fallback directory for profiling failed in " + tempDir.getAbsolutePath());
    }

    profilingTracesDirPath = tempDir.getAbsolutePath();
    options.setProfilingTracesDirPath(profilingTracesDirPath);
    return profilingTracesDirPath;
  }
}
