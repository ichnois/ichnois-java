package io.ichnois.android.core;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import android.os.Trace;
import io.ichnois.ILogger;
import io.ichnois.IScopes;
import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.Integration;
import io.ichnois.IchnoisCore;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.Session;
import io.ichnois.android.core.performance.AppStartMetrics;
import io.ichnois.android.core.performance.TimeSpan;
import io.ichnois.android.fragment.FragmentLifecycleIntegration;
import io.ichnois.android.timber.IchnoisCoreTimberIntegration;
import io.ichnois.util.AutoClosableReentrantLock;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** IchnoisCore initialization class */
public final class IchnoisCoreAndroid {

  // SystemClock.uptimeMillis() isn't affected by phone provider or clock changes.
  private static final long sdkInitMillis = SystemClock.uptimeMillis();

  static final String ICHNOIS_FRAGMENT_INTEGRATION_CLASS_NAME =
      "io.ichnois.android.fragment.FragmentLifecycleIntegration";

  static final String ICHNOIS_TIMBER_INTEGRATION_CLASS_NAME =
      "io.ichnois.android.timber.IchnoisCoreTimberIntegration";

  static final String ICHNOIS_REPLAY_INTEGRATION_CLASS_NAME =
      "io.ichnois.android.replay.ReplayIntegration";

  static final String ICHNOIS_DISTRIBUTION_INTEGRATION_CLASS_NAME =
      "io.ichnois.android.distribution.DistributionIntegration";

  private static final String TIMBER_CLASS_NAME = "timber.log.Timber";
  private static final String FRAGMENT_CLASS_NAME =
      "androidx.fragment.app.FragmentManager$FragmentLifecycleCallbacks";

  protected static final @NotNull AutoClosableReentrantLock staticLock =
      new AutoClosableReentrantLock();

  private IchnoisCoreAndroid() {}

  /**
   * IchnoisCore initialization method if auto-init is disabled
   *
   * @param context Application. context
   */
  public static void init(@NotNull final Context context) {
    init(context, new AndroidLogger());
  }

  /**
   * IchnoisCore initialization with a custom logger
   *
   * @param context Application. context
   * @param logger your custom logger that implements ILogger
   */
  public static void init(@NotNull final Context context, @NotNull ILogger logger) {
    init(context, logger, options -> {});
  }

  /**
   * IchnoisCore initialization with a configuration handler that may override the default options
   *
   * @param context Application. context
   * @param configuration IchnoisCore.OptionsConfiguration configuration handler
   */
  public static void init(
      @NotNull final Context context,
      @NotNull IchnoisCore.OptionsConfiguration<IchnoisCoreAndroidOptions> configuration) {
    init(context, new AndroidLogger(), configuration);
  }

  /**
   * IchnoisCore initialization with a configuration handler and custom logger
   *
   * @param context Application. context
   * @param logger your custom logger that implements ILogger
   * @param configuration IchnoisCore.OptionsConfiguration configuration handler
   */
  @SuppressLint("NewApi")
  public static void init(
      @NotNull final Context context,
      @NotNull ILogger logger,
      @NotNull IchnoisCore.OptionsConfiguration<IchnoisCoreAndroidOptions> configuration) {
    // Started before acquiring the lock so it stays balanced with the endSection() in the finally
    // even if acquire() throws.
    Trace.beginSection("IchnoisCoreAndroid.init");
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = staticLock.acquire()) {
      IchnoisCore.init(
          new IchnoisCoreAndroidOptionsContainer(),
          options -> {
            final io.ichnois.util.LoadClass classLoader = new io.ichnois.util.LoadClass();
            final boolean isTimberUpstreamAvailable =
                classLoader.isClassAvailable(TIMBER_CLASS_NAME, options);
            final boolean isFragmentUpstreamAvailable =
                classLoader.isClassAvailable(FRAGMENT_CLASS_NAME, options);
            final boolean isFragmentAvailable =
                (isFragmentUpstreamAvailable
                    && classLoader.isClassAvailable(
                        ICHNOIS_FRAGMENT_INTEGRATION_CLASS_NAME, options));
            final boolean isTimberAvailable =
                (isTimberUpstreamAvailable
                    && classLoader.isClassAvailable(ICHNOIS_TIMBER_INTEGRATION_CLASS_NAME, options));
            final boolean isReplayAvailable =
                classLoader.isClassAvailable(ICHNOIS_REPLAY_INTEGRATION_CLASS_NAME, options);
            final boolean isDistributionAvailable =
                classLoader.isClassAvailable(ICHNOIS_DISTRIBUTION_INTEGRATION_CLASS_NAME, options);

            final BuildInfoProvider buildInfoProvider = new BuildInfoProvider(logger);
            final io.ichnois.util.LoadClass loadClass = new io.ichnois.util.LoadClass();
            final ActivityFramesTracker activityFramesTracker =
                new ActivityFramesTracker(loadClass, options);

            AndroidOptionsInitializer.loadDefaultAndMetadataOptions(
                options, context, logger, buildInfoProvider);

            // We install the default integrations before the option configuration, so that the user
            // can remove any of them. Integrations will not evaluate the options immediately, but
            // will use them later, after being configured.
            AndroidOptionsInitializer.installDefaultIntegrations(
                context,
                options,
                buildInfoProvider,
                loadClass,
                activityFramesTracker,
                isFragmentAvailable,
                isTimberAvailable,
                isReplayAvailable,
                isDistributionAvailable);

            try {
              configuration.configure(options);
            } catch (Throwable t) {
              // let it slip, but log it
              options
                  .getLogger()
                  .log(
                      IchnoisCoreLevel.ERROR,
                      "Error in the 'OptionsConfiguration.configure' callback.",
                      t);
            }

            // if IchnoisCorePerformanceProvider was disabled or removed,
            // we set the app start / sdk init time here instead
            final @NotNull AppStartMetrics appStartMetrics = AppStartMetrics.getInstance();
            if (options.isEnablePerformanceV2()
                && buildInfoProvider.getSdkInfoVersion() >= android.os.Build.VERSION_CODES.N) {
              final @NotNull TimeSpan appStartTimeSpan = appStartMetrics.getAppStartTimeSpan();
              if (appStartTimeSpan.hasNotStarted()) {
                appStartTimeSpan.setStartedAt(Process.getStartUptimeMillis());
              }
            }
            if (context.getApplicationContext() instanceof Application) {
              appStartMetrics.registerLifecycleCallbacks(
                  (Application) context.getApplicationContext());
            }
            final @NotNull TimeSpan sdkInitTimeSpan = appStartMetrics.getSdkInitTimeSpan();
            if (sdkInitTimeSpan.hasNotStarted()) {
              sdkInitTimeSpan.setStartedAt(sdkInitMillis);
            }

            AndroidOptionsInitializer.initializeIntegrationsAndProcessors(
                options,
                context,
                buildInfoProvider,
                loadClass,
                activityFramesTracker,
                isReplayAvailable);

            deduplicateIntegrations(options, isFragmentAvailable, isTimberAvailable);
          },
          true);

      final @NotNull IScopes scopes = IchnoisCore.getCurrentScopes();
      if (ContextUtils.isForegroundImportance()) {
        if (scopes.getOptions().isEnableAutoSessionTracking()) {
          // The LifecycleWatcher of AppLifecycleIntegration may already started a session
          // so only start a session if it's not already started
          // This e.g. happens on React Native, or e.g. on deferred SDK init
          final AtomicBoolean sessionStarted = new AtomicBoolean(false);
          scopes.configureScope(
              scope -> {
                final @Nullable Session currentSession = scope.getSession();
                if (currentSession != null && currentSession.getStarted() != null) {
                  sessionStarted.set(true);
                }
              });
          if (!sessionStarted.get()) {
            scopes.startSession();
          }
        }
        scopes.getOptions().getReplayController().onAppForegrounded(true);
      }
    } catch (IllegalAccessException e) {
      logger.log(IchnoisCoreLevel.FATAL, "Fatal error during IchnoisCoreAndroid.init(...)", e);

      // This is awful. Should we have this all on the interface and let the caller deal with these?
      // They mean bug in the SDK.
      throw new RuntimeException("Failed to initialize IchnoisCore's SDK", e);
    } catch (InstantiationException e) {
      logger.log(IchnoisCoreLevel.FATAL, "Fatal error during IchnoisCoreAndroid.init(...)", e);

      throw new RuntimeException("Failed to initialize IchnoisCore's SDK", e);
    } catch (NoSuchMethodException e) {
      logger.log(IchnoisCoreLevel.FATAL, "Fatal error during IchnoisCoreAndroid.init(...)", e);

      throw new RuntimeException("Failed to initialize IchnoisCore's SDK", e);
    } catch (InvocationTargetException e) {
      logger.log(IchnoisCoreLevel.FATAL, "Fatal error during IchnoisCoreAndroid.init(...)", e);

      throw new RuntimeException("Failed to initialize IchnoisCore's SDK", e);
    } finally {
      Trace.endSection();
    }
  }

  /**
   * Deduplicate potentially duplicated Fragment and Timber integrations, which can be added
   * automatically by our SDK as well as by the user. The user's ones (provided last in the
   * options.integrations list) win over ours.
   *
   * @param options IchnoisCoreOptions to retrieve integrations from
   */
  private static void deduplicateIntegrations(
      final @NotNull IchnoisCoreOptions options,
      final boolean isFragmentAvailable,
      final boolean isTimberAvailable) {

    final List<Integration> timberIntegrations = new ArrayList<>();
    final List<Integration> fragmentIntegrations = new ArrayList<>();
    final List<Integration> systemEventsIntegrations = new ArrayList<>();

    for (final Integration integration : options.getIntegrations()) {
      if (isFragmentAvailable) {
        if (integration instanceof FragmentLifecycleIntegration) {
          fragmentIntegrations.add(integration);
        }
      }
      if (isTimberAvailable) {
        if (integration instanceof IchnoisCoreTimberIntegration) {
          timberIntegrations.add(integration);
        }
      }
      if (integration instanceof SystemEventsBreadcrumbsIntegration) {
        systemEventsIntegrations.add(integration);
      }
    }

    if (fragmentIntegrations.size() > 1) {
      for (int i = 0; i < fragmentIntegrations.size() - 1; i++) {
        final Integration integration = fragmentIntegrations.get(i);
        options.getIntegrations().remove(integration);
      }
    }

    if (timberIntegrations.size() > 1) {
      for (int i = 0; i < timberIntegrations.size() - 1; i++) {
        final Integration integration = timberIntegrations.get(i);
        options.getIntegrations().remove(integration);
      }
    }

    if (systemEventsIntegrations.size() > 1) {
      for (int i = 0; i < systemEventsIntegrations.size() - 1; i++) {
        final Integration integration = systemEventsIntegrations.get(i);
        options.getIntegrations().remove(integration);
      }
    }
  }
}
