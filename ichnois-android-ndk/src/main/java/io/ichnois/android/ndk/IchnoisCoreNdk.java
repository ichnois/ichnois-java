package io.ichnois.android.ndk;

import io.ichnois.android.core.NdkHandlerStrategy;
import io.ichnois.android.core.IchnoisCoreAndroidOptions;
import io.ichnois.ndk.NativeModuleListLoader;
import io.ichnois.ndk.NdkOptions;
import io.ichnois.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class IchnoisCoreNdk {

  private static final @NotNull CountDownLatch loadLibraryLatch = new CountDownLatch(1);

  private IchnoisCoreNdk() {}

  static {
    new Thread(
            () -> {
              try {
                //noinspection UnstableApiUsage
                io.ichnois.ndk.IchnoisCoreNdk.loadNativeLibraries();
              } catch (Throwable t) {
                // ignored
                // if loadLibrary() fails, the later init() will throw an exception anyway
              } finally {
                loadLibraryLatch.countDown();
              }
            },
            "IchnoisCoreNdkLoadLibs")
        .start();
  }

  /**
   * Init the NDK integration
   *
   * @param options the IchnoisCoreAndroidOptions
   */
  public static void init(@NotNull final IchnoisCoreAndroidOptions options) {
    IchnoisCoreNdkUtil.addPackage(options.getSdkVersion());
    try {
      if (loadLibraryLatch.await(2000, TimeUnit.MILLISECONDS)) {
        final @NotNull NdkOptions ndkOptions =
            new NdkOptions(
                Objects.requireNonNull(options.getDsn(), "DSN is required for ichnois-ndk"),
                options.isDebug(),
                Objects.requireNonNull(
                    options.getOutboxPath(), "outbox path is required for ichnois-ndk"),
                options.getRelease(),
                options.getEnvironment(),
                options.getDist(),
                options.getMaxBreadcrumbs(),
                options.getNativeSdkName());

        final int handlerStrategy = options.getNdkHandlerStrategy();
        if (handlerStrategy == NdkHandlerStrategy.ICHNOIS_HANDLER_STRATEGY_DEFAULT.getValue()) {
          ndkOptions.setNdkHandlerStrategy(
              io.ichnois.ndk.NdkHandlerStrategy.ICHNOIS_HANDLER_STRATEGY_DEFAULT);
        } else if (handlerStrategy
            == NdkHandlerStrategy.ICHNOIS_HANDLER_STRATEGY_CHAIN_AT_START.getValue()) {
          ndkOptions.setNdkHandlerStrategy(
              io.ichnois.ndk.NdkHandlerStrategy.ICHNOIS_HANDLER_STRATEGY_CHAIN_AT_START);
        }

        final @Nullable Double tracesSampleRate = options.getTracesSampleRate();
        if (tracesSampleRate == null) {
          ndkOptions.setTracesSampleRate(0.0f);
        } else {
          ndkOptions.setTracesSampleRate(tracesSampleRate.floatValue());
        }

        ndkOptions.setEnableAppHangTracking(options.isEnableNdkAppHangTracking());
        ndkOptions.setAppHangTimeoutMillis(options.getNdkAppHangTimeoutIntervalMillis());

        //noinspection UnstableApiUsage
        io.ichnois.ndk.IchnoisCoreNdk.init(ndkOptions);

        // only add scope sync observer if the scope sync is enabled.
        if (options.isEnableScopeSync()) {
          options.addScopeObserver(new NdkScopeObserver(options));
        }

        options.setDebugImagesLoader(new DebugImagesLoader(options, new NativeModuleListLoader()));
      } else {
        throw new IllegalStateException("Timeout waiting for IchnoisCore NDK library to load");
      }
    } catch (InterruptedException e) {
      throw new IllegalStateException(
          "Thread interrupted while waiting for NDK libs to be loaded", e);
    }
  }

  /** Closes the NDK integration */
  public static void close() {
    try {
      if (loadLibraryLatch.await(2000, TimeUnit.MILLISECONDS)) {
        //noinspection UnstableApiUsage
        io.ichnois.ndk.IchnoisCoreNdk.close();
      } else {
        throw new IllegalStateException("Timeout waiting for IchnoisCore NDK library to load");
      }
    } catch (InterruptedException e) {
      throw new IllegalStateException(
          "Thread interrupted while waiting for NDK libs to be loaded", e);
    }
  }
}
