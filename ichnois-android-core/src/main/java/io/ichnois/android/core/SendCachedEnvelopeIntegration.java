package io.ichnois.android.core;

import static io.ichnois.util.IntegrationUtils.addIntegrationToSdkVersion;

import io.ichnois.DataCategory;
import io.ichnois.IConnectionStatusProvider;
import io.ichnois.IScopes;
import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.Integration;
import io.ichnois.SendCachedEnvelopeFireAndForgetIntegration;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.transport.RateLimiter;
import io.ichnois.util.AutoClosableReentrantLock;
import io.ichnois.util.LazyEvaluator;
import io.ichnois.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SendCachedEnvelopeIntegration
    implements Integration, IConnectionStatusProvider.IConnectionStatusObserver, Closeable {

  private final @NotNull SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForgetFactory
      factory;
  private final @NotNull LazyEvaluator<Boolean> startupCrashMarkerEvaluator;
  private final AtomicBoolean startupCrashHandled = new AtomicBoolean(false);
  private @Nullable IConnectionStatusProvider connectionStatusProvider;
  private @Nullable IScopes scopes;
  private @Nullable IchnoisCoreAndroidOptions options;
  private @Nullable SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForget sender;
  private final AtomicBoolean isInitialized = new AtomicBoolean(false);
  private final AtomicBoolean isClosed = new AtomicBoolean(false);
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();

  public SendCachedEnvelopeIntegration(
      final @NotNull SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForgetFactory factory,
      final @NotNull LazyEvaluator<Boolean> startupCrashMarkerEvaluator) {
    this.factory = Objects.requireNonNull(factory, "SendFireAndForgetFactory is required");
    this.startupCrashMarkerEvaluator = startupCrashMarkerEvaluator;
  }

  @Override
  public void register(@NotNull IScopes scopes, @NotNull IchnoisCoreOptions options) {
    this.scopes = Objects.requireNonNull(scopes, "Scopes are required");
    this.options =
        Objects.requireNonNull(
            (options instanceof IchnoisCoreAndroidOptions) ? (IchnoisCoreAndroidOptions) options : null,
            "IchnoisCoreAndroidOptions is required");

    final String cachedDir = options.getCacheDirPath();
    if (!factory.hasValidPath(cachedDir, options.getLogger())) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, "No cache dir path is defined in options.");
      return;
    }
    addIntegrationToSdkVersion("SendCachedEnvelope");

    sendCachedEnvelopes(scopes, this.options);
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

  @SuppressWarnings({"NullAway"})
  private void sendCachedEnvelopes(
      final @NotNull IScopes scopes, final @NotNull IchnoisCoreAndroidOptions options) {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
      final Future<?> future =
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
                                "SendCachedEnvelopeIntegration, not trying to send after closing.");
                        return;
                      }

                      if (!isInitialized.getAndSet(true)) {
                        connectionStatusProvider = options.getConnectionStatusProvider();
                        connectionStatusProvider.addConnectionStatusObserver(this);

                        sender = factory.create(scopes, options);
                      }

                      if (connectionStatusProvider != null
                          && connectionStatusProvider.getConnectionStatus()
                              == IConnectionStatusProvider.ConnectionStatus.DISCONNECTED) {
                        options
                            .getLogger()
                            .log(IchnoisCoreLevel.INFO, "SendCachedEnvelopeIntegration, no connection.");
                        return;
                      }

                      // in case there's rate limiting active, skip processing
                      final @Nullable RateLimiter rateLimiter = scopes.getRateLimiter();
                      if (rateLimiter != null
                          && rateLimiter.isActiveForCategory(DataCategory.All)) {
                        options
                            .getLogger()
                            .log(
                                IchnoisCoreLevel.INFO,
                                "SendCachedEnvelopeIntegration, rate limiting active.");
                        return;
                      }

                      if (sender == null) {
                        options
                            .getLogger()
                            .log(
                                IchnoisCoreLevel.ERROR,
                                "SendCachedEnvelopeIntegration factory is null.");
                        return;
                      }

                      sender.send();
                    } catch (Throwable e) {
                      options
                          .getLogger()
                          .log(IchnoisCoreLevel.ERROR, "Failed trying to send cached events.", e);
                    }
                  });

      // startupCrashMarkerEvaluator remains true on subsequent runs, let's ensure we only block on
      // the very first execution (=app start)
      if (startupCrashMarkerEvaluator.getValue()
          && startupCrashHandled.compareAndSet(false, true)) {
        options.getLogger().log(IchnoisCoreLevel.DEBUG, "Startup Crash marker exists, blocking flush.");
        try {
          future.get(options.getStartupCrashFlushTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
          options
              .getLogger()
              .log(IchnoisCoreLevel.DEBUG, "Synchronous send timed out, continuing in the background.");
        }
      }
      options.getLogger().log(IchnoisCoreLevel.DEBUG, "SendCachedEnvelopeIntegration installed.");
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
