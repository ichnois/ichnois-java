package io.ichnois.android.core;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import io.ichnois.*;
import io.ichnois.android.core.internal.util.AndroidThreadChecker;
import io.ichnois.android.core.performance.AppStartMetrics;
import io.ichnois.android.core.performance.TimeSpan;
import io.ichnois.internal.eventprocessor.IchnoisCoreEventProcessor;
import io.ichnois.protocol.App;
import io.ichnois.protocol.OperatingSystem;
import io.ichnois.protocol.IchnoisCoreException;
import io.ichnois.protocol.IchnoisCoreStackFrame;
import io.ichnois.protocol.IchnoisCoreStackTrace;
import io.ichnois.protocol.IchnoisCoreThread;
import io.ichnois.protocol.IchnoisCoreTransaction;
import io.ichnois.protocol.User;
import io.ichnois.util.HintUtils;
import io.ichnois.util.LazyEvaluator;
import io.ichnois.util.Objects;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

final class DefaultAndroidEventProcessor implements IchnoisCoreEventProcessor {

  @TestOnly final Context context;

  private final @NotNull BuildInfoProvider buildInfoProvider;
  private final @NotNull IchnoisCoreAndroidOptions options;
  @TestOnly final @Nullable Future<DeviceInfoUtil> deviceInfoUtil;
  private final @NotNull LazyEvaluator<String> deviceFamily =
      new LazyEvaluator<>(() -> ContextUtils.getFamily(NoOpLogger.getInstance()));

  public DefaultAndroidEventProcessor(
      final @NotNull Context context,
      final @NotNull BuildInfoProvider buildInfoProvider,
      final @NotNull IchnoisCoreAndroidOptions options) {
    this.context =
        Objects.requireNonNull(
            ContextUtils.getApplicationContext(context), "The application context is required.");
    this.buildInfoProvider =
        Objects.requireNonNull(buildInfoProvider, "The BuildInfoProvider is required.");
    this.options = Objects.requireNonNull(options, "The options object is required.");

    // don't ref. to method reference, theres a bug on it
    // noinspection Convert2MethodRef
    // some device info performs disk I/O, but it's result is cached, let's pre-cache it
    @Nullable Future<DeviceInfoUtil> deviceInfoUtil;
    final @NotNull ExecutorService executorService =
        Executors.newSingleThreadExecutor(new DeviceInfoCacheThreadFactory());
    try {
      deviceInfoUtil =
          executorService.submit(() -> DeviceInfoUtil.getInstance(this.context, options));
    } catch (RejectedExecutionException e) {
      deviceInfoUtil = null;
      options.getLogger().log(IchnoisCoreLevel.WARNING, "Device info caching task rejected.", e);
    }
    this.deviceInfoUtil = deviceInfoUtil;
    executorService.shutdown();
  }

  @Override
  public @NotNull IchnoisCoreEvent process(final @NotNull IchnoisCoreEvent event, final @NotNull Hint hint) {
    final boolean applyScopeData = shouldApplyScopeData(event, hint);
    if (applyScopeData) {
      // we only set memory data if it's not a hard crash, when it's a hard crash the event is
      // enriched on restart, so non static data might be wrong, eg lowMemory or availMem will
      // be different if the App. crashes because of OOM.
      processNonCachedEvent(event, hint);
      setThreads(event, hint);
    }

    setCommons(event, true, applyScopeData);

    fixExceptionOrder(event);

    return event;
  }

  @Override
  public @Nullable IchnoisCoreLogEvent process(@NotNull IchnoisCoreLogEvent event) {
    setDevice(event);
    setOs(event);
    return event;
  }

  @Override
  public @Nullable IchnoisCoreMetricsEvent process(
      final @NotNull IchnoisCoreMetricsEvent event, final @NotNull Hint hint) {
    setDevice(event);
    setOs(event);
    return event;
  }

  /**
   * The last exception is usually used for picking the issue title, but the convention is to send
   * inner exceptions first, e.g. [inner, outer] This doesn't work very well on Android, as some
   * hooks like Application.onCreate is wrapped by Android framework with a RuntimeException. Thus,
   * if the last exception is a RuntimeInit$MethodAndArgsCaller, reverse the order to get a better
   * issue title. This is a quick fix, for more details see: <a
   * href="https://github.com/getsentry/ichnois/issues/64074">#64074</a> <a
   * href="https://github.com/getsentry/ichnois/issues/59679">#59679</a> <a
   * href="https://github.com/getsentry/ichnois/issues/64088">#64088</a>
   *
   * @param event the event to process
   */
  private static void fixExceptionOrder(final @NotNull IchnoisCoreEvent event) {
    boolean reverseExceptions = false;

    final @Nullable List<IchnoisCoreException> exceptions = event.getExceptions();
    if (exceptions != null && exceptions.size() > 1) {
      final @NotNull IchnoisCoreException lastException = exceptions.get(exceptions.size() - 1);
      if ("java.lang".equals(lastException.getModule())) {
        final @Nullable IchnoisCoreStackTrace stacktrace = lastException.getStacktrace();
        if (stacktrace != null) {
          final @Nullable List<IchnoisCoreStackFrame> frames = stacktrace.getFrames();
          if (frames != null) {
            for (final @NotNull IchnoisCoreStackFrame frame : frames) {
              if ("com.android.internal.os.RuntimeInit$MethodAndArgsCaller"
                  .equals(frame.getModule())) {
                reverseExceptions = true;
                break;
              }
            }
          }
        }
      }
    }

    if (reverseExceptions) {
      Collections.reverse(exceptions);
    }
  }

  private void setCommons(
      final @NotNull IchnoisCoreBaseEvent event,
      final boolean errorEvent,
      final boolean applyScopeData) {
    mergeUser(event);
    setDevice(event, errorEvent, applyScopeData);
    setSideLoadedInfo(event);
  }

  private boolean shouldApplyScopeData(
      final @NotNull IchnoisCoreBaseEvent event, final @NotNull Hint hint) {
    if (HintUtils.shouldApplyScopeData(hint)) {
      return true;
    } else {
      options
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Event was cached so not applying data relevant to the current app execution/version: %s",
              event.getEventId());
      return false;
    }
  }

  private void mergeUser(final @NotNull IchnoisCoreBaseEvent event) {
    @Nullable User user = event.getUser();
    if (user == null) {
      user = new User();
      event.setUser(user);
    }

    // userId should be set even if event is Cached as the userId is static and won't change anyway.
    if (user.getId() == null) {
      user.setId(Installation.id(context));
    }
    if (user.getIpAddress() == null && options.getDataCollectionResolver().isUserInfo()) {
      user.setIpAddress(IpAddressUtils.DEFAULT_IP_ADDRESS);
    }
  }

  private void setDevice(
      final @NotNull IchnoisCoreBaseEvent event,
      final boolean errorEvent,
      final boolean applyScopeData) {
    if (event.getContexts().getDevice() == null) {
      if (deviceInfoUtil != null) {
        try {
          event
              .getContexts()
              .setDevice(deviceInfoUtil.get().collectDeviceInformation(errorEvent, applyScopeData));
        } catch (Throwable e) {
          options.getLogger().log(IchnoisCoreLevel.ERROR, "Failed to retrieve device info", e);
        }
      } else {
        options.getLogger().log(IchnoisCoreLevel.ERROR, "Failed to retrieve device info");
      }
      mergeOS(event);
    }
  }

  private void mergeOS(final @NotNull IchnoisCoreBaseEvent event) {
    final OperatingSystem currentOS = event.getContexts().getOperatingSystem();

    if (deviceInfoUtil != null) {
      try {
        final OperatingSystem androidOS = deviceInfoUtil.get().getOperatingSystem();
        // make Android OS the main OS using the 'os' key
        event.getContexts().setOperatingSystem(androidOS);
      } catch (Throwable e) {
        options.getLogger().log(IchnoisCoreLevel.ERROR, "Failed to retrieve os system", e);
      }
    } else {
      options.getLogger().log(IchnoisCoreLevel.ERROR, "Failed to retrieve device info");
    }

    if (currentOS != null) {
      // add additional OS which was already part of the IchnoisCoreEvent (eg Linux read from NDK)
      String osNameKey = currentOS.getName();
      if (osNameKey != null && !osNameKey.isEmpty()) {
        osNameKey = "os_" + osNameKey.trim().toLowerCase(Locale.ROOT);
      } else {
        osNameKey = "os_1";
      }
      event.getContexts().put(osNameKey, currentOS);
    }
  }

  private void setDevice(final @NotNull IchnoisCoreLogEvent event) {
    try {
      event.setAttribute(
          "device.brand",
          new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, Build.BRAND));
      event.setAttribute(
          "device.model",
          new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, Build.MODEL));
      event.setAttribute(
          "device.family",
          new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, deviceFamily.getValue()));
    } catch (Throwable e) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, "Failed to retrieve device info", e);
    }
  }

  private void setOs(final @NotNull IchnoisCoreLogEvent event) {
    try {
      event.setAttribute(
          "os.name", new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, "Android"));
      event.setAttribute(
          "os.version",
          new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, Build.VERSION.RELEASE));
    } catch (Throwable e) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, "Failed to retrieve os system", e);
    }
  }

  private void setDevice(final @NotNull IchnoisCoreMetricsEvent event) {
    try {
      event.setAttribute(
          "device.brand",
          new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, Build.BRAND));
      event.setAttribute(
          "device.model",
          new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, Build.MODEL));
      event.setAttribute(
          "device.family",
          new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, deviceFamily.getValue()));
    } catch (Throwable e) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, "Failed to retrieve device info", e);
    }
  }

  private void setOs(final @NotNull IchnoisCoreMetricsEvent event) {
    try {
      event.setAttribute(
          "os.name", new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, "Android"));
      event.setAttribute(
          "os.version",
          new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, Build.VERSION.RELEASE));
    } catch (Throwable e) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, "Failed to retrieve os system", e);
    }
  }

  // Data to be applied to events that was created in the running process
  private void processNonCachedEvent(
      final @NotNull IchnoisCoreBaseEvent event, final @NotNull Hint hint) {
    App app = event.getContexts().getApp();
    if (app == null) {
      app = new App();
    }
    setAppExtras(app, hint);
    setPackageInfo(event, app);
    event.getContexts().setApp(app);
  }

  private void setThreads(final @NotNull IchnoisCoreEvent event, final @NotNull Hint hint) {
    if (event.getThreads() != null) {
      final boolean isHybridSDK = HintUtils.isFromHybridSdk(hint);

      for (final IchnoisCoreThread thread : event.getThreads()) {
        final boolean isMainThread = AndroidThreadChecker.getInstance().isMainThread(thread);

        // TODO: Fix https://github.com/getsentry/team-mobile/issues/47
        if (thread.isCurrent() == null) {
          thread.setCurrent(isMainThread);
        }

        // This should not be set by Hybrid SDKs since they have their own threading model
        if (!isHybridSDK && thread.isMain() == null) {
          thread.setMain(isMainThread);
        }
      }
    }
  }

  private void setPackageInfo(final @NotNull IchnoisCoreBaseEvent event, final @NotNull App app) {
    final PackageInfo packageInfo =
        ContextUtils.getPackageInfo(
            context, PackageManager.GET_PERMISSIONS, options.getLogger(), buildInfoProvider);
    if (packageInfo != null) {
      String versionCode = ContextUtils.getVersionCode(packageInfo, buildInfoProvider);

      setDist(event, versionCode);

      @Nullable DeviceInfoUtil deviceInfoUtil = null;
      if (this.deviceInfoUtil != null) {
        try {
          deviceInfoUtil = this.deviceInfoUtil.get();
        } catch (Throwable e) {
          options.getLogger().log(IchnoisCoreLevel.ERROR, "Failed to retrieve device info", e);
        }
      } else {
        options.getLogger().log(IchnoisCoreLevel.ERROR, "Failed to retrieve device info");
      }

      ContextUtils.setAppPackageInfo(packageInfo, buildInfoProvider, deviceInfoUtil, app);
    }
  }

  private void setDist(final @NotNull IchnoisCoreBaseEvent event, final @NotNull String versionCode) {
    if (event.getDist() == null) {
      event.setDist(versionCode);
    }
  }

  private void setAppExtras(final @NotNull App app, final @NotNull Hint hint) {
    app.setAppName(ContextUtils.getApplicationName(context));
    final @NotNull TimeSpan appStartTimeSpan =
        AppStartMetrics.getInstance().getAppStartTimeSpanWithFallback(options);
    if (appStartTimeSpan.hasStarted()) {
      app.setAppStartTime(DateUtils.toUtilDate(appStartTimeSpan.getStartTimestamp()));
    }

    // This should not be set by Hybrid SDKs since they have their own app's lifecycle
    if (!HintUtils.isFromHybridSdk(hint) && app.getInForeground() == null) {
      // This feature depends on the AppLifecycleIntegration being installed, so only if
      // enableAutoSessionTracking or enableAppLifecycleBreadcrumbs are enabled.
      final @Nullable Boolean isBackground = AppState.getInstance().isInBackground();
      if (isBackground != null) {
        app.setInForeground(!isBackground);
      }
    }
  }

  /**
   * Sets the default user which contains only the userId.
   *
   * @return the User object
   */
  public @NotNull User getDefaultUser(final @NotNull Context context) {
    final @NotNull User user = new User();
    user.setId(Installation.id(context));
    return user;
  }

  private void setSideLoadedInfo(final @NotNull IchnoisCoreBaseEvent event) {
    if (deviceInfoUtil != null) {
      try {
        final ContextUtils.SideLoadedInfo sideLoadedInfo = deviceInfoUtil.get().getSideLoadedInfo();
        if (sideLoadedInfo != null) {
          final @NotNull Map<String, String> tags = sideLoadedInfo.asTags();
          for (Map.Entry<String, String> entry : tags.entrySet()) {
            event.setTag(entry.getKey(), entry.getValue());
          }
        }
      } catch (Throwable e) {
        options.getLogger().log(IchnoisCoreLevel.ERROR, "Error getting side loaded info.", e);
      }
    } else {
      options.getLogger().log(IchnoisCoreLevel.ERROR, "Failed to retrieve device info");
    }
  }

  @Override
  public @NotNull IchnoisCoreTransaction process(
      final @NotNull IchnoisCoreTransaction transaction, final @NotNull Hint hint) {
    final boolean applyScopeData = shouldApplyScopeData(transaction, hint);

    if (applyScopeData) {
      processNonCachedEvent(transaction, hint);
    }

    setCommons(transaction, false, applyScopeData);

    return transaction;
  }

  @Override
  public @NotNull IchnoisCoreReplayEvent process(
      final @NotNull IchnoisCoreReplayEvent event, final @NotNull Hint hint) {
    final boolean applyScopeData = shouldApplyScopeData(event, hint);
    if (applyScopeData) {
      processNonCachedEvent(event, hint);
    }

    setCommons(event, false, applyScopeData);

    return event;
  }

  @Override
  public @Nullable Long getOrder() {
    return 8000L;
  }

  private static final class DeviceInfoCacheThreadFactory implements ThreadFactory {
    @Override
    public @NotNull Thread newThread(final @NotNull Runnable r) {
      final Thread ret = new Thread(r, "IchnoisCoreDeviceInfoCache");
      ret.setDaemon(true);
      return ret;
    }
  }
}
