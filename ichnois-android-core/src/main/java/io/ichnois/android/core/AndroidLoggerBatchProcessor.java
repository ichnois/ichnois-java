package io.ichnois.android.core;

import io.ichnois.IIchnoisCoreClient;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.logger.LoggerBatchProcessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class AndroidLoggerBatchProcessor extends LoggerBatchProcessor
    implements AppState.AppStateListener {

  public AndroidLoggerBatchProcessor(
      @NotNull IchnoisCoreOptions options, @NotNull IIchnoisCoreClient client) {
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
                  flush(LoggerBatchProcessor.FLUSH_AFTER_MS);
                }
              });
    } catch (Throwable t) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, t, "Failed to submit log flush in onBackground()");
    }
  }

  @Override
  public void close(boolean isRestarting) {
    AppState.getInstance().removeAppStateListener(this);
    super.close(isRestarting);
  }
}
