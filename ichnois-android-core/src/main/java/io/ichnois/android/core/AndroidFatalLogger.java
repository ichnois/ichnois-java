package io.ichnois.android.core;

import android.util.Log;
import io.ichnois.ILogger;
import io.ichnois.IchnoisCoreLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class AndroidFatalLogger implements ILogger {

  private final @NotNull String tag;

  public AndroidFatalLogger() {
    this("IchnoisCore");
  }

  public AndroidFatalLogger(final @NotNull String tag) {
    this.tag = tag;
  }

  @SuppressWarnings("AnnotateFormatMethod")
  @Override
  public void log(
      final @NotNull IchnoisCoreLevel level,
      final @NotNull String message,
      final @Nullable Object... args) {
    if (args == null || args.length == 0) {
      Log.println(toLogcatLevel(level), tag, message);
    } else {
      Log.println(toLogcatLevel(level), tag, String.format(message, args));
    }
  }

  @SuppressWarnings("AnnotateFormatMethod")
  @Override
  public void log(
      final @NotNull IchnoisCoreLevel level,
      final @Nullable Throwable throwable,
      final @NotNull String message,
      final @Nullable Object... args) {
    if (args == null || args.length == 0) {
      log(level, message, throwable);
    } else {
      log(level, String.format(message, args), throwable);
    }
  }

  @Override
  public void log(
      final @NotNull IchnoisCoreLevel level,
      final @NotNull String message,
      final @Nullable Throwable throwable) {
    Log.wtf(tag, message, throwable);
  }

  @Override
  public boolean isEnabled(@Nullable IchnoisCoreLevel level) {
    return true;
  }

  private int toLogcatLevel(final @NotNull IchnoisCoreLevel ichnoisLevel) {
    return Log.ASSERT;
  }
}
