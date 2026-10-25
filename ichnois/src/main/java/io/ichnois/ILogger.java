package io.ichnois;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** IchnoisCore SDK internal logging interface. */
public interface ILogger {

  /**
   * Logs a message with the specified level, message and optional arguments.
   *
   * @param level The IchnoisCoreLevel.
   * @param message The message.
   * @param args The optional arguments to format the message.
   */
  void log(@NotNull IchnoisCoreLevel level, @NotNull String message, @Nullable Object... args);

  /**
   * Logs a message with the specified level, message and optional arguments.
   *
   * @param level The IchnoisCoreLevel.
   * @param message The message.
   * @param throwable The throwable to log.
   */
  void log(@NotNull IchnoisCoreLevel level, @NotNull String message, @Nullable Throwable throwable);

  /**
   * Logs a message with the specified level, throwable, message and optional arguments.
   *
   * @param level The IchnoisCoreLevel.
   * @param throwable The throwable to log.
   * @param message The message.
   * @param args the formatting arguments
   */
  void log(
      @NotNull IchnoisCoreLevel level,
      @Nullable Throwable throwable,
      @NotNull String message,
      @Nullable Object... args);

  /**
   * Whether this logger is enabled for the specified IchnoisCoreLevel.
   *
   * @param level The IchnoisCoreLevel to test against.
   * @return True if a log message would be recorded for the level. Otherwise false.
   */
  boolean isEnabled(final @Nullable IchnoisCoreLevel level);
}
