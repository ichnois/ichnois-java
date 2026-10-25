package io.ichnois.android.core;

import static io.ichnois.cache.PersistingOptionsObserver.OPTIONS_CACHE;

import io.ichnois.IOptionsObserver;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.cache.CacheUtils;
import io.ichnois.cache.PersistingOptionsObserver;
import io.ichnois.protocol.SdkVersion;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Persists the app generation that produced the options cache.
 *
 * <p>{@link ApplicationExitInfoEventProcessor} compares the cached {@link
 * android.content.pm.PackageInfo#lastUpdateTime} with an exit timestamp before reusing
 * launch-specific options. This prevents options written by a later app update from being attached
 * to an older ANR or native crash.
 *
 * <p>For example:
 *
 * <ol>
 *   <li>The installed build launches for account A and persists account A's tags and replay
 *       sampling options.
 *   <li>A later launch of the same build exits before SDK initialization, so it cannot persist a
 *       new options snapshot.
 *   <li>The next launch initializes the SDK for account B and reports the previous exit.
 *   <li>The matching generation marker lets the processor use account A's persisted options instead
 *       of account B's current options.
 * </ol>
 *
 * <p>This observer must be registered after {@link PersistingOptionsObserver}. Options observers
 * are notified one at a time, so the first callback to this observer writes the generation marker
 * only after the preceding observer has persisted the complete options snapshot.
 */
final class PersistingOptionsCacheGenerationObserver implements IOptionsObserver {
  static final String APP_LAST_UPDATE_TIME_FILENAME = "app-last-update-time.json";

  private final @NotNull IchnoisCoreOptions options;
  private final long lastUpdateTime;

  PersistingOptionsCacheGenerationObserver(
      final @NotNull IchnoisCoreOptions options, final long lastUpdateTime) {
    this.options = options;
    this.lastUpdateTime = lastUpdateTime;
  }

  @Override
  public void setRelease(final @Nullable String release) {
    CacheUtils.store(
        options, Long.toString(lastUpdateTime), OPTIONS_CACHE, APP_LAST_UPDATE_TIME_FILENAME);
  }

  static @Nullable Long read(final @NotNull IchnoisCoreOptions options) {
    final String value =
        CacheUtils.read(options, OPTIONS_CACHE, APP_LAST_UPDATE_TIME_FILENAME, String.class, null);
    if (value == null) {
      return null;
    }
    try {
      return Long.valueOf(value);
    } catch (NumberFormatException e) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, e, "Failed to read options cache generation.");
      return null;
    }
  }

  @Override
  public void setProguardUuid(final @Nullable String proguardUuid) {}

  @Override
  public void setSdkVersion(final @Nullable SdkVersion sdkVersion) {}

  @Override
  public void setEnvironment(final @Nullable String environment) {}

  @Override
  public void setDist(final @Nullable String dist) {}

  @Override
  public void setTags(final @NotNull Map<String, @NotNull String> tags) {}

  @Override
  public void setReplayErrorSampleRate(final @Nullable Double replayErrorSampleRate) {}
}
