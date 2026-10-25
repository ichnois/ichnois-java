package io.ichnois;

import static io.ichnois.IchnoisCoreLevel.INFO;

import io.ichnois.cache.EnvelopeCache;
import io.ichnois.cache.IEnvelopeCache;
import java.io.File;
import org.jetbrains.annotations.NotNull;

final class MovePreviousSession implements Runnable {

  private final @NotNull IchnoisCoreOptions options;

  MovePreviousSession(final @NotNull IchnoisCoreOptions options) {
    this.options = options;
  }

  @Override
  public void run() {
    final String cacheDirPath = options.getCacheDirPath();
    if (cacheDirPath == null) {
      options.getLogger().log(INFO, "Cache dir is not set, not moving the previous session.");
      return;
    }

    final IEnvelopeCache cache = options.getEnvelopeDiskCache();
    if (cache instanceof EnvelopeCache) {
      final File currentSessionFile = EnvelopeCache.getCurrentSessionFile(cacheDirPath);
      final File previousSessionFile = EnvelopeCache.getPreviousSessionFile(cacheDirPath);

      ((EnvelopeCache) cache).movePreviousSession(currentSessionFile, previousSessionFile);

      ((EnvelopeCache) cache).flushPreviousSession();
    }
  }
}
