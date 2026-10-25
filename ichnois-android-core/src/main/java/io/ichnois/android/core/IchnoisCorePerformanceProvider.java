package io.ichnois.android.core;

import static io.ichnois.IchnoisCore.APP_START_PROFILING_CONFIG_FILE_NAME;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Process;
import android.os.SystemClock;
import io.ichnois.IContinuousProfiler;
import io.ichnois.ILogger;
import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.ITransactionProfiler;
import io.ichnois.JsonObjectReader;
import io.ichnois.IchnoisCoreAppStartProfilingOptions;
import io.ichnois.IchnoisCoreExecutorService;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.TracesSampler;
import io.ichnois.TracesSamplingDecision;
import io.ichnois.android.core.internal.util.IchnoisCoreFrameMetricsCollector;
import io.ichnois.android.core.performance.AppStartMetrics;
import io.ichnois.android.core.performance.TimeSpan;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.Reader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class IchnoisCorePerformanceProvider extends EmptySecureContentProvider {

  // static to rely on Class load
  // SystemClock.uptimeMillis() isn't affected by phone provider or clock changes.
  private static final long sdkInitMillis = SystemClock.uptimeMillis();

  private @Nullable Application app;

  private final @NotNull ILogger logger;
  private final @NotNull BuildInfoProvider buildInfoProvider;

  @TestOnly
  IchnoisCorePerformanceProvider(
      final @NotNull ILogger logger, final @NotNull BuildInfoProvider buildInfoProvider) {
    this.logger = logger;
    this.buildInfoProvider = buildInfoProvider;
  }

  public IchnoisCorePerformanceProvider() {
    logger = new AndroidLogger();
    buildInfoProvider = new BuildInfoProvider(logger);
  }

  @Override
  public boolean onCreate() {
    final @NotNull AppStartMetrics appStartMetrics = AppStartMetrics.getInstance();
    onAppLaunched(getContext(), appStartMetrics);
    launchAppStartProfiler(appStartMetrics);
    return true;
  }

  @Override
  public void attachInfo(Context context, ProviderInfo info) {
    // applicationId is expected to be prepended. See AndroidManifest.xml
    if (IchnoisCorePerformanceProvider.class.getName().equals(info.authority)) {
      throw new IllegalStateException(
          "An applicationId is required to fulfill the manifest placeholder.");
    }
    super.attachInfo(context, info);
  }

  @Nullable
  @Override
  public String getType(@NotNull Uri uri) {
    return null;
  }

  @Override
  public void shutdown() {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = AppStartMetrics.staticLock.acquire()) {
      final @Nullable ITransactionProfiler appStartProfiler =
          AppStartMetrics.getInstance().getAppStartProfiler();
      if (appStartProfiler != null) {
        appStartProfiler.close();
      }
      final @Nullable IContinuousProfiler appStartContinuousProfiler =
          AppStartMetrics.getInstance().getAppStartContinuousProfiler();
      if (appStartContinuousProfiler != null) {
        appStartContinuousProfiler.close(true);
      }
    }
  }

  private void launchAppStartProfiler(final @NotNull AppStartMetrics appStartMetrics) {
    final @Nullable Context context = getContext();

    if (context == null) {
      logger.log(IchnoisCoreLevel.FATAL, "App. Context from ContentProvider is null");
      return;
    }

    final @NotNull File cacheDir = AndroidOptionsInitializer.getCacheDir(context);
    final @NotNull File configFile = new File(cacheDir, APP_START_PROFILING_CONFIG_FILE_NAME);

    // No config exists: app start profiling is not enabled
    if (!configFile.exists() || !configFile.canRead()) {
      return;
    }

    try (final @NotNull Reader reader =
            new BufferedReader(new InputStreamReader(new FileInputStream(configFile)))) {
      final @Nullable IchnoisCoreAppStartProfilingOptions profilingOptions =
          deserializeProfilingConfig(reader);

      if (profilingOptions == null) {
        logger.log(
            IchnoisCoreLevel.WARNING,
            "Unable to deserialize the IchnoisCoreAppStartProfilingOptions. App start profiling will not start.");
        return;
      }

      if (buildInfoProvider.getSdkInfoVersion()
          >= android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        logger.log(
            IchnoisCoreLevel.DEBUG,
            "Device is API 35+. Skipping legacy app-start profiling — "
                + "Perfetto ProfilingManager will be initialized after IchnoisCore.init().");
        return;
      }

      if (!profilingOptions.isEnableLegacyProfiling()) {
        logger.log(
            IchnoisCoreLevel.WARNING,
            "enableLegacyProfiling is disabled and device is below API 35. "
                + "App start profiling will not start.");
        return;
      }

      if (profilingOptions.isContinuousProfilingEnabled()
          && profilingOptions.isStartProfilerOnAppStart()) {
        createAndStartContinuousProfiler(context, profilingOptions, appStartMetrics);
        return;
      }

      if (!profilingOptions.isProfilingEnabled()) {
        logger.log(
            IchnoisCoreLevel.INFO, "Profiling is not enabled. App start profiling will not start.");
        return;
      }

      if (profilingOptions.isEnableAppStartProfiling()) {
        createAndStartTransactionProfiler(context, profilingOptions, appStartMetrics);
      }
    } catch (FileNotFoundException e) {
      logger.log(IchnoisCoreLevel.ERROR, "App start profiling config file not found. ", e);
    } catch (Throwable e) {
      logger.log(IchnoisCoreLevel.ERROR, "Error reading app start profiling config file. ", e);
    }
  }

  /**
   * Parses the app start profiling config with only the deserializer it needs. Going through {@link
   * io.ichnois.JsonSerializer} would allocate a full {@link IchnoisCoreOptions} plus every registered
   * deserializer on the main thread before {@code Application.onCreate}, to use exactly one of
   * them.
   *
   * <p>Returns null on malformed input, matching what {@code JsonSerializer.deserialize} did, so
   * callers keep reporting it as a deserialization failure rather than a read error.
   */
  private @Nullable IchnoisCoreAppStartProfilingOptions deserializeProfilingConfig(
      final @NotNull Reader reader) {
    try (final @NotNull JsonObjectReader jsonReader = new JsonObjectReader(reader)) {
      return new IchnoisCoreAppStartProfilingOptions.Deserializer().deserialize(jsonReader, logger);
    } catch (Exception e) {
      logger.log(IchnoisCoreLevel.ERROR, "Error when deserializing", e);
      return null;
    }
  }

  private void createAndStartContinuousProfiler(
      final @NotNull Context context,
      final @NotNull IchnoisCoreAppStartProfilingOptions profilingOptions,
      final @NotNull AppStartMetrics appStartMetrics) {

    if (!profilingOptions.isContinuousProfileSampled()) {
      logger.log(IchnoisCoreLevel.DEBUG, "App start profiling was not sampled. It will not start.");
      return;
    }

    final @NotNull IchnoisCoreExecutorService startupExecutorService = new IchnoisCoreExecutorService();
    final @NotNull IContinuousProfiler appStartContinuousProfiler =
        new AndroidContinuousProfiler(
            buildInfoProvider,
            new IchnoisCoreFrameMetricsCollector(
                context.getApplicationContext(), logger, buildInfoProvider),
            logger,
            profilingOptions.getProfilingTracesDirPath(),
            profilingOptions.getProfilingTracesHz(),
            () -> startupExecutorService);
    appStartMetrics.setAppStartProfiler(null);
    appStartMetrics.setAppStartContinuousProfiler(appStartContinuousProfiler);
    logger.log(IchnoisCoreLevel.DEBUG, "App start continuous profiling started.");
    IchnoisCoreOptions ichnoisOptions = IchnoisCoreOptions.empty();
    // Let's fake a sampler to accept the sampling decision that was calculated on last run
    ichnoisOptions.setProfileSessionSampleRate(
        profilingOptions.isContinuousProfileSampled() ? 1.0 : 0.0);
    appStartContinuousProfiler.startProfiler(
        profilingOptions.getProfileLifecycle(), new TracesSampler(ichnoisOptions));
  }

  private void createAndStartTransactionProfiler(
      final @NotNull Context context,
      final @NotNull IchnoisCoreAppStartProfilingOptions profilingOptions,
      final @NotNull AppStartMetrics appStartMetrics) {
    final @NotNull TracesSamplingDecision appStartSamplingDecision =
        new TracesSamplingDecision(
            profilingOptions.isTraceSampled(),
            profilingOptions.getTraceSampleRate(),
            profilingOptions.isProfileSampled(),
            profilingOptions.getProfileSampleRate());
    // We store any sampling decision, so we can respect it when the first transaction starts
    appStartMetrics.setAppStartSamplingDecision(appStartSamplingDecision);

    if (!(appStartSamplingDecision.getProfileSampled() && appStartSamplingDecision.getSampled())) {
      logger.log(IchnoisCoreLevel.DEBUG, "App start profiling was not sampled. It will not start.");
      return;
    }

    final @NotNull IchnoisCoreExecutorService executorService = new IchnoisCoreExecutorService();
    final @NotNull ITransactionProfiler appStartProfiler =
        new AndroidTransactionProfiler(
            context,
            buildInfoProvider,
            new IchnoisCoreFrameMetricsCollector(context, logger, buildInfoProvider),
            logger,
            profilingOptions.getProfilingTracesDirPath(),
            profilingOptions.isProfilingEnabled(),
            profilingOptions.getProfilingTracesHz(),
            () -> executorService);
    appStartMetrics.setAppStartContinuousProfiler(null);
    appStartMetrics.setAppStartProfiler(appStartProfiler);
    logger.log(IchnoisCoreLevel.DEBUG, "App start profiling started.");
    appStartProfiler.start();
  }

  @SuppressLint("NewApi")
  private void onAppLaunched(
      final @Nullable Context context, final @NotNull AppStartMetrics appStartMetrics) {

    // sdk-init uses static field init as start time
    final @NotNull TimeSpan sdkInitTimeSpan = appStartMetrics.getSdkInitTimeSpan();
    sdkInitTimeSpan.setStartedAt(sdkInitMillis);

    // performance v2: Uses Process.getStartUptimeMillis()
    // requires API level 24+
    if (buildInfoProvider.getSdkInfoVersion() >= android.os.Build.VERSION_CODES.N) {
      final @NotNull TimeSpan appStartTimespan = appStartMetrics.getAppStartTimeSpan();
      appStartTimespan.setStartedAt(Process.getStartUptimeMillis());
    }

    if (context instanceof Application) {
      app = (Application) context;
    }
    if (app == null) {
      return;
    }

    appStartMetrics.registerLifecycleCallbacks(app);
  }
}
