package io.ichnois.android.core;

import android.util.Log;
import io.ichnois.ILogger;
import io.ichnois.IchnoisCoreLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class AndroidLogger implements ILogger {

  private final @NotNull String tag;

  public AndroidLogger() {
    this("IchnoisCore");
  }

  public AndroidLogger(final @NotNull String tag) {
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

    switch (level) {
      case INFO:
        Log.i(tag, message, throwable);
        break;
      case WARNING:
        Log.w(tag, message, throwable);
        break;
      case ERROR:
        Log.e(tag, message, throwable);
        break;
      case FATAL:
        Log.wtf(tag, message, throwable);
        break;
      case DEBUG:
      default:
        Log.d(tag, message, throwable);
        break;
    }
  }

  @Override
  public boolean isEnabled(@Nullable IchnoisCoreLevel level) {
    return true;
  }

  private int toLogcatLevel(final @NotNull IchnoisCoreLevel ichnoisLevel) {
    switch (ichnoisLevel) {
      case INFO:
        return Log.INFO;
      case WARNING:
        return Log.WARN;
      case FATAL:
        return Log.ASSERT;
      case DEBUG:
      default:
        return Log.DEBUG;
    }
  }
}
