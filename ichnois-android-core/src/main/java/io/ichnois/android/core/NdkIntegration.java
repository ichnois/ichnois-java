package io.ichnois.android.core;

import static io.ichnois.util.IntegrationUtils.addIntegrationToSdkVersion;

import io.ichnois.IScopes;
import io.ichnois.Integration;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.util.Objects;
import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Method;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** Enables the NDK error reporting for Android */
public final class NdkIntegration implements Integration, Closeable {

  public static final String ICHNOIS_NDK_CLASS_NAME = "io.ichnois.android.ndk.IchnoisCoreNdk";

  private final @Nullable Class<?> ichnoisNdkClass;

  private @Nullable IchnoisCoreAndroidOptions options;

  public NdkIntegration(final @Nullable Class<?> ichnoisNdkClass) {
    this.ichnoisNdkClass = ichnoisNdkClass;
  }

  @Override
  public final void register(final @NotNull IScopes scopes, final @NotNull IchnoisCoreOptions options) {
    Objects.requireNonNull(scopes, "Scopes are required");
    this.options =
        Objects.requireNonNull(
            (options instanceof IchnoisCoreAndroidOptions) ? (IchnoisCoreAndroidOptions) options : null,
            "IchnoisCoreAndroidOptions is required");

    final boolean enabled = this.options.isEnableNdk();
    this.options.getLogger().log(IchnoisCoreLevel.DEBUG, "NdkIntegration enabled: %s", enabled);

    // Note: `scopes` isn't used here because the NDK integration writes files to disk which are
    // picked
    // up by another integration (EnvelopeFileObserverIntegration).
    if (enabled && ichnoisNdkClass != null) {
      final String cachedDir = this.options.getCacheDirPath();
      if (cachedDir == null) {
        this.options.getLogger().log(IchnoisCoreLevel.ERROR, "No cache dir path is defined in options.");
        disableNdkIntegration(this.options);
        return;
      }

      try {
        final Method method = ichnoisNdkClass.getMethod("init", IchnoisCoreAndroidOptions.class);
        final Object[] args = new Object[1];
        args[0] = this.options;
        method.invoke(null, args);

        this.options.getLogger().log(IchnoisCoreLevel.DEBUG, "NdkIntegration installed.");
        addIntegrationToSdkVersion("Ndk");
      } catch (NoSuchMethodException e) {
        disableNdkIntegration(this.options);
        this.options
            .getLogger()
            .log(IchnoisCoreLevel.ERROR, "Failed to invoke the IchnoisCoreNdk.init method.", e);
      } catch (Throwable e) {
        disableNdkIntegration(this.options);
        this.options.getLogger().log(IchnoisCoreLevel.ERROR, "Failed to initialize IchnoisCoreNdk.", e);
      }
    } else {
      disableNdkIntegration(this.options);
    }
  }

  private void disableNdkIntegration(final @NotNull IchnoisCoreAndroidOptions options) {
    options.setEnableNdk(false);
    options.setEnableScopeSync(false);
  }

  @TestOnly
  @Nullable
  Class<?> getIchnoisCoreNdkClass() {
    return ichnoisNdkClass;
  }

  @Override
  public void close() throws IOException {
    if (options != null && options.isEnableNdk() && ichnoisNdkClass != null) {
      try {
        final Method method = ichnoisNdkClass.getMethod("close");
        method.invoke(null, new Object[0]);

        options.getLogger().log(IchnoisCoreLevel.DEBUG, "NdkIntegration removed.");
      } catch (NoSuchMethodException e) {
        options
            .getLogger()
            .log(IchnoisCoreLevel.ERROR, "Failed to invoke the IchnoisCoreNdk.close method.", e);
      } catch (Throwable e) {
        options.getLogger().log(IchnoisCoreLevel.ERROR, "Failed to close IchnoisCoreNdk.", e);
      } finally {
        disableNdkIntegration(this.options);
      }
    }
  }
}
