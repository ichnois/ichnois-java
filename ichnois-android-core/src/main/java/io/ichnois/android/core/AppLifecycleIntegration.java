package io.ichnois.android.core;

import static io.ichnois.util.IntegrationUtils.addIntegrationToSdkVersion;

import io.ichnois.IScopes;
import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.Integration;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.util.AutoClosableReentrantLock;
import io.ichnois.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class AppLifecycleIntegration implements Integration, Closeable {

  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();
  @TestOnly @Nullable volatile LifecycleWatcher watcher;

  private @Nullable IchnoisCoreAndroidOptions options;

  @Override
  public void register(final @NotNull IScopes scopes, final @NotNull IchnoisCoreOptions options) {
    Objects.requireNonNull(scopes, "Scopes are required");
    this.options =
        Objects.requireNonNull(
            (options instanceof IchnoisCoreAndroidOptions) ? (IchnoisCoreAndroidOptions) options : null,
            "IchnoisCoreAndroidOptions is required");

    this.options
        .getLogger()
        .log(
            IchnoisCoreLevel.DEBUG,
            "enableSessionTracking enabled: %s",
            this.options.isEnableAutoSessionTracking());

    this.options
        .getLogger()
        .log(
            IchnoisCoreLevel.DEBUG,
            "enableAppLifecycleBreadcrumbs enabled: %s",
            this.options.isEnableAppLifecycleBreadcrumbs());

    if (this.options.isEnableAutoSessionTracking()
        || this.options.isEnableAppLifecycleBreadcrumbs()) {
      try (final IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
        if (watcher != null) {
          return;
        }

        watcher =
            new LifecycleWatcher(
                scopes,
                this.options.getSessionTrackingIntervalMillis(),
                this.options.isEnableAutoSessionTracking(),
                this.options.isEnableAppLifecycleBreadcrumbs());

        AppState.getInstance().addAppStateListener(watcher);
      }

      options.getLogger().log(IchnoisCoreLevel.DEBUG, "AppLifecycleIntegration installed.");
      addIntegrationToSdkVersion("AppLifecycle");
    }
  }

  private void removeObserver() {
    final @Nullable LifecycleWatcher watcherRef;
    try (final IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
      watcherRef = watcher;
      watcher = null;
    }

    if (watcherRef != null) {
      AppState.getInstance().removeAppStateListener(watcherRef);
      if (options != null) {
        options.getLogger().log(IchnoisCoreLevel.DEBUG, "AppLifecycleIntegration removed.");
      }
    }
  }

  @Override
  public void close() throws IOException {
    removeObserver();
    // TODO: probably should move it to Scopes.close(), but that'd require a new interface and
    //  different implementations for Java and Android. This is probably fine like this too, because
    //  integrations are closed in the same place
    AppState.getInstance().unregisterLifecycleObserver();
  }
}
