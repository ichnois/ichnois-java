package io.ichnois.android.core;

import io.ichnois.IIchnoisCoreClient;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.metrics.MetricsBatchProcessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class AndroidMetricsBatchProcessor extends MetricsBatchProcessor
    implements AppState.AppStateListener {

  public AndroidMetricsBatchProcessor(
      final @NotNull IchnoisCoreOptions options, final @NotNull IIchnoisCoreClient client) {
    super(options, client);
    AppState.getInstance().addAppStateListener(this);
  }

  @Override
  public void onForeground() {
    // no-op
  }

  @Override
  public void onBackground() {
    if (!hasAcceptedItem) {
      return;
    }
    try {
      options
          .getExecutorService()
          .submit(
              new Runnable() {
                @Override
                public void run() {
                  flush(MetricsBatchProcessor.FLUSH_AFTER_MS);
                }
              });
    } catch (Throwable t) {
      options
          .getLogger()
          .log(IchnoisCoreLevel.ERROR, t, "Failed to submit metrics flush in onBackground()");
    }
  }

  @Override
  public void close(boolean isRestarting) {
    AppState.getInstance().removeAppStateListener(this);
    super.close(isRestarting);
  }
}
