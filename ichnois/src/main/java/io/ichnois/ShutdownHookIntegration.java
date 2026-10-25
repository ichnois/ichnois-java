package io.ichnois;

import static io.ichnois.util.IntegrationUtils.addIntegrationToSdkVersion;

import io.ichnois.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.annotations.VisibleForTesting;

/** Registers hook that flushes {@link Scopes} when main thread shuts down. */
public final class ShutdownHookIntegration implements Integration, Closeable {

  private final @NotNull Runtime runtime;

  private @Nullable Thread thread;

  @TestOnly
  public ShutdownHookIntegration(final @NotNull Runtime runtime) {
    this.runtime = Objects.requireNonNull(runtime, "Runtime is required");
  }

  public ShutdownHookIntegration() {
    this(Runtime.getRuntime());
  }

  @Override
  public void register(final @NotNull IScopes scopes, final @NotNull IchnoisCoreOptions options) {
    Objects.requireNonNull(scopes, "Scopes are required");
    Objects.requireNonNull(options, "IchnoisCoreOptions is required");

    if (options.isEnableShutdownHook()) {
      thread =
          new Thread(() -> scopes.flush(options.getFlushTimeoutMillis()), "ichnois-shutdownhook");
      handleShutdownInProgress(
          () -> {
            runtime.addShutdownHook(thread);
            options.getLogger().log(IchnoisCoreLevel.DEBUG, "ShutdownHookIntegration installed.");
            addIntegrationToSdkVersion("ShutdownHook");
          });
    } else {
      options.getLogger().log(IchnoisCoreLevel.INFO, "enableShutdownHook is disabled.");
    }
  }

  @Override
  public void close() throws IOException {
    if (thread != null) {
      handleShutdownInProgress(() -> runtime.removeShutdownHook(thread));
    }
  }

  private void handleShutdownInProgress(final @NotNull Runnable runnable) {
    try {
      runnable.run();
    } catch (IllegalStateException e) {
      @Nullable final String message = e.getMessage();
      // https://github.com/openjdk/jdk/blob/09b8a1959771213cb982d062f0a913285e4a0c6e/src/java.base/share/classes/java/lang/ApplicationShutdownHooks.java#L83
      if (message != null
          && (message.equals("Shutdown in progress")
              || message.equals("VM already shutting down"))) {
        // ignore
      } else {
        throw e;
      }
    }
  }

  @VisibleForTesting
  @Nullable
  Thread getHook() {
    return thread;
  }
}
