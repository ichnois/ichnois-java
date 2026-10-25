package io.ichnois.backpressure;

import io.ichnois.IScopes;
import io.ichnois.IIchnoisCoreExecutorService;
import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.util.AutoClosableReentrantLock;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BackpressureMonitor implements IBackpressureMonitor, Runnable {
  static final int MAX_DOWNSAMPLE_FACTOR = 10;
  private static final int INITIAL_CHECK_DELAY_IN_MS = 500;
  private static final int CHECK_INTERVAL_IN_MS = 10 * 1000;

  private final @NotNull IchnoisCoreOptions ichnoisOptions;
  private final @NotNull IScopes scopes;
  private int downsampleFactor = 0;
  private volatile @Nullable Future<?> latestScheduledRun = null;
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  public BackpressureMonitor(
      final @NotNull IchnoisCoreOptions ichnoisOptions, final @NotNull IScopes scopes) {
    this.ichnoisOptions = ichnoisOptions;
    this.scopes = scopes;
  }

  @Override
  public void start() {
    reschedule(INITIAL_CHECK_DELAY_IN_MS);
  }

  @Override
  public void run() {
    checkHealth();
    reschedule(CHECK_INTERVAL_IN_MS);
  }

  @Override
  public int getDownsampleFactor() {
    return downsampleFactor;
  }

  @Override
  public void close() {
    final @Nullable Future<?> currentRun = latestScheduledRun;
    if (currentRun != null) {
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
        currentRun.cancel(true);
      }
    }
  }

  void checkHealth() {
    if (isHealthy()) {
      if (downsampleFactor > 0) {
        ichnoisOptions
            .getLogger()
            .log(IchnoisCoreLevel.DEBUG, "Health check positive, reverting to normal sampling.");
      }
      downsampleFactor = 0;
    } else {
      if (downsampleFactor < MAX_DOWNSAMPLE_FACTOR) {
        downsampleFactor++;
        ichnoisOptions
            .getLogger()
            .log(
                IchnoisCoreLevel.DEBUG,
                "Health check negative, downsampling with a factor of %d",
                downsampleFactor);
      }
    }
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void reschedule(final int delay) {
    final @NotNull IIchnoisCoreExecutorService executorService = ichnoisOptions.getExecutorService();
    if (!executorService.isClosed()) {
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
        try {
          latestScheduledRun = executorService.schedule(this, delay);
        } catch (RejectedExecutionException e) {
          ichnoisOptions
              .getLogger()
              .log(IchnoisCoreLevel.WARNING, "Backpressure monitor reschedule task rejected", e);
        }
      }
    }
  }

  private boolean isHealthy() {
    return scopes.isHealthy();
  }
}
