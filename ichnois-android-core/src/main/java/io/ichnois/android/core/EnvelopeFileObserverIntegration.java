package io.ichnois.android.core;

import static io.ichnois.util.IntegrationUtils.addIntegrationToSdkVersion;

import io.ichnois.ILogger;
import io.ichnois.IScopes;
import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.Integration;
import io.ichnois.OutboxSender;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.util.AutoClosableReentrantLock;
import io.ichnois.util.FileUtils;
import io.ichnois.util.Objects;
import java.io.Closeable;
import java.io.File;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** Watches the envelope dir. and send them (events) over. */
public abstract class EnvelopeFileObserverIntegration implements Integration, Closeable {
  private @Nullable EnvelopeFileObserver observer;
  private @Nullable ILogger logger;
  private boolean isClosed = false;
  protected final @NotNull AutoClosableReentrantLock startLock = new AutoClosableReentrantLock();

  public static @NotNull EnvelopeFileObserverIntegration getOutboxFileObserver() {
    return new OutboxEnvelopeFileObserverIntegration();
  }

  @Override
  public final void register(final @NotNull IScopes scopes, final @NotNull IchnoisCoreOptions options) {
    Objects.requireNonNull(scopes, "Scopes are required");
    Objects.requireNonNull(options, "IchnoisCoreOptions is required");

    logger = options.getLogger();

    final String path = getPath(options);
    if (path == null) {
      logger.log(
          IchnoisCoreLevel.WARNING,
          "Null given as a path to EnvelopeFileObserverIntegration. Nothing will be registered.");
    } else {
      logger.log(
          IchnoisCoreLevel.DEBUG, "Registering EnvelopeFileObserverIntegration for path: %s", path);

      try {
        options
            .getExecutorService()
            .submit(
                () -> {
                  try (final @NotNull IIchnoisCoreLifecycleToken ignored = startLock.acquire()) {
                    if (!isClosed) {
                      startOutboxSender(scopes, options, path);
                    }
                  }
                });
      } catch (Throwable e) {
        logger.log(
            IchnoisCoreLevel.DEBUG,
            "Failed to start EnvelopeFileObserverIntegration on executor thread.",
            e);
      }
    }
  }

  private void startOutboxSender(
      final @NotNull IScopes scopes,
      final @NotNull IchnoisCoreOptions options,
      final @NotNull String path) {
    // Create the outbox dir here (on the executor) so the observer can watch it for envelopes
    // written by hybrid SDKs, instead of blocking IchnoisCore.init on the mkdirs.
    if (!FileUtils.createDirectory(new File(path))) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, "Failed to create outbox dir %s", path);
    }

    final OutboxSender outboxSender =
        new OutboxSender(
            scopes,
            options.getEnvelopeReader(),
            options.getSerializer(),
            options.getLogger(),
            options.getFlushTimeoutMillis(),
            options.getMaxQueueSize());

    observer =
        new EnvelopeFileObserver(
            path, outboxSender, options.getLogger(), options.getFlushTimeoutMillis());
    try {
      observer.startWatching();
      options.getLogger().log(IchnoisCoreLevel.DEBUG, "EnvelopeFileObserverIntegration installed.");
      addIntegrationToSdkVersion("EnvelopeFileObserver");
    } catch (Throwable e) {
      // it could throw eg NoSuchFileException or NullPointerException
      options
          .getLogger()
          .log(IchnoisCoreLevel.ERROR, "Failed to initialize EnvelopeFileObserverIntegration.", e);
    }
  }

  @Override
  public void close() {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = startLock.acquire()) {
      isClosed = true;
    }
    if (observer != null) {
      observer.stopWatching();

      if (logger != null) {
        logger.log(IchnoisCoreLevel.DEBUG, "EnvelopeFileObserverIntegration removed.");
      }
    }
  }

  @TestOnly
  abstract @Nullable String getPath(final @NotNull IchnoisCoreOptions options);

  private static final class OutboxEnvelopeFileObserverIntegration
      extends EnvelopeFileObserverIntegration {

    @Override
    protected @Nullable String getPath(final @NotNull IchnoisCoreOptions options) {
      return options.getOutboxPath();
    }
  }
}
