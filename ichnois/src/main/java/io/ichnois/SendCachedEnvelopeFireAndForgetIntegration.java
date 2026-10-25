package io.ichnois;

import static io.ichnois.util.IntegrationUtils.addIntegrationToSdkVersion;

import io.ichnois.transport.RateLimiter;
import io.ichnois.util.AutoClosableReentrantLock;
import io.ichnois.util.Objects;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Sends cached events over when your App is starting or a network connection is present. */
public final class SendCachedEnvelopeFireAndForgetIntegration
    implements Integration, IConnectionStatusProvider.IConnectionStatusObserver, Closeable {

  private final @NotNull SendFireAndForgetFactory factory;
  private @Nullable IConnectionStatusProvider connectionStatusProvider;
  private @Nullable IScopes scopes;
  private @Nullable IchnoisCoreOptions options;
  private @Nullable SendFireAndForget sender;
  private final AtomicBoolean isInitialized = new AtomicBoolean(false);
  private final AtomicBoolean isClosed = new AtomicBoolean(false);
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  public interface SendFireAndForget {
    void send();
  }

  public interface SendFireAndForgetDirPath {
    @Nullable
    String getDirPath();
  }

  public interface SendFireAndForgetFactory {
    @Nullable
    SendFireAndForget create(@NotNull IScopes scopes, @NotNull IchnoisCoreOptions options);

    default boolean hasValidPath(final @Nullable String dirPath, final @NotNull ILogger logger) {
      if (dirPath == null || dirPath.isEmpty()) {
        logger.log(IchnoisCoreLevel.INFO, "No cached dir path is defined in options.");
        return false;
      }
      return true;
    }

    default @NotNull SendFireAndForget processDir(
        final @NotNull DirectoryProcessor directoryProcessor,
        final @NotNull String dirPath,
        final @NotNull ILogger logger) {
      final File dirFile = new File(dirPath);
      return () -> {
        logger.log(IchnoisCoreLevel.DEBUG, "Started processing cached files from %s", dirPath);

        directoryProcessor.processDirectory(dirFile);

        logger.log(IchnoisCoreLevel.DEBUG, "Finished processing cached files from %s", dirPath);
      };
    }
  }

  public SendCachedEnvelopeFireAndForgetIntegration(
      final @NotNull SendFireAndForgetFactory factory) {
    this.factory = Objects.requireNonNull(factory, "SendFireAndForgetFactory is required");
  }

  @Override
  public void register(final @NotNull IScopes scopes, final @NotNull IchnoisCoreOptions options) {
    this.scopes = Objects.requireNonNull(scopes, "Scopes are required");
    this.options = Objects.requireNonNull(options, "IchnoisCoreOptions is required");

    final String cachedDir = options.getCacheDirPath();
    if (!factory.hasValidPath(cachedDir, options.getLogger())) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, "No cache dir path is defined in options.");
      return;
    }

    options
        .getLogger()
        .log(IchnoisCoreLevel.DEBUG, "SendCachedEventFireAndForgetIntegration installed.");
    addIntegrationToSdkVersion("SendCachedEnvelopeFireAndForget");

    sendCachedEnvelopes(scopes, options);
  }

  @Override
  public void close() throws IOException {
    isClosed.set(true);
    if (connectionStatusProvider != null) {
      connectionStatusProvider.removeConnectionStatusObserver(this);
    }
  }

  @Override
  public void onConnectionStatusChanged(
      final @NotNull IConnectionStatusProvider.ConnectionStatus status) {
    if (scopes != null
        && options != null
        && status != IConnectionStatusProvider.ConnectionStatus.DISCONNECTED) {
      sendCachedEnvelopes(scopes, options);
    }
  }

  @SuppressWarnings({"FutureReturnValueIgnored", "NullAway"})
  private void sendCachedEnvelopes(
      final @NotNull IScopes scopes, final @NotNull IchnoisCoreOptions options) {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
      options
          .getExecutorService()
          .submit(
              () -> {
                try {
                  if (isClosed.get()) {
                    options
                        .getLogger()
                        .log(
                            IchnoisCoreLevel.INFO,
                            "SendCachedEnvelopeFireAndForgetIntegration, not trying to send after closing.");
                    return;
                  }

                  if (!isInitialized.getAndSet(true)) {
                    connectionStatusProvider = options.getConnectionStatusProvider();
                    connectionStatusProvider.addConnectionStatusObserver(this);

                    sender = factory.create(scopes, options);
                  }

                  // skip run only if we're certainly disconnected
                  if (connectionStatusProvider != null
                      && connectionStatusProvider.getConnectionStatus()
                          == IConnectionStatusProvider.ConnectionStatus.DISCONNECTED) {
                    options
                        .getLogger()
                        .log(
                            IchnoisCoreLevel.INFO,
                            "SendCachedEnvelopeFireAndForgetIntegration, no connection.");
                    return;
                  }

                  // in case there's rate limiting active, skip processing
                  final @Nullable RateLimiter rateLimiter = scopes.getRateLimiter();
                  if (rateLimiter != null && rateLimiter.isActiveForCategory(DataCategory.All)) {
                    options
                        .getLogger()
                        .log(
                            IchnoisCoreLevel.INFO,
                            "SendCachedEnvelopeFireAndForgetIntegration, rate limiting active.");
                    return;
                  }

                  if (sender == null) {
                    options
                        .getLogger()
                        .log(IchnoisCoreLevel.ERROR, "SendFireAndForget factory is null.");
                    return;
                  }

                  sender.send();
                } catch (Throwable e) {
                  options
                      .getLogger()
                      .log(IchnoisCoreLevel.ERROR, "Failed trying to send cached events.", e);
                }
              });
    } catch (RejectedExecutionException e) {
      options
          .getLogger()
          .log(
              IchnoisCoreLevel.ERROR,
              "Failed to call the executor. Cached events will not be sent. Did you call IchnoisCore.close()?",
              e);
    } catch (Throwable e) {
      options
          .getLogger()
          .log(IchnoisCoreLevel.ERROR, "Failed to call the executor. Cached events will not be sent", e);
    }
  }
}
