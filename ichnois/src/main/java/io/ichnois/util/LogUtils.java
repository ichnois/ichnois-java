package io.ichnois.util;

import io.ichnois.ILogger;
import io.ichnois.IchnoisCoreLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class LogUtils {

  public static void logNotInstanceOf(
      final @NotNull Class<?> expectedClass,
      final @Nullable Object ichnoisSdkHint,
      final @NotNull ILogger logger) {
    logger.log(
        IchnoisCoreLevel.DEBUG,
        "%s is not %s",
        ichnoisSdkHint != null ? ichnoisSdkHint.getClass().getCanonicalName() : "Hint",
        expectedClass.getCanonicalName());
  }
}
