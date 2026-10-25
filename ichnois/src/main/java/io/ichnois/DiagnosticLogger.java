package io.ichnois;

import io.ichnois.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** IchnoisCore SDK internal diagnostic logger. */
@ApiStatus.Internal
public final class DiagnosticLogger implements ILogger {
  private final @NotNull IchnoisCoreOptions options;
  private final @Nullable ILogger logger;

  /**
   * Creates a new instance of DiagnosticLogger with the wrapped ILogger.
   *
   * @param options a IchnoisCoreOptions instance
   * @param logger a ILogger instance
   */
  public DiagnosticLogger(final @NotNull IchnoisCoreOptions options, final @Nullable ILogger logger) {
    this.options = Objects.requireNonNull(options, "IchnoisCoreOptions is required.");
    this.logger = logger;
  }

  /**
   * Whether this logger is enabled for the specified IchnoisCoreLevel.
   *
   * @param level The IchnoisCoreLevel to test against.
   * @return True if a log message would be recorded for the level. Otherwise false.
   */
  // temp fix for JDK 21: IchnoisCoreLevel is declared in ascending severity order.
  @SuppressWarnings("EnumOrdinal")
  @Override
  public boolean isEnabled(final @Nullable IchnoisCoreLevel level) {
    final IchnoisCoreLevel diagLevel = options.getDiagnosticLevel();
    if (level == null) {
      return false;
    }
    return options.isDebug() && level.ordinal() >= diagLevel.ordinal();
  }

  /**
   * Logs a message with the specified level, message and optional arguments.
   *
   * @param level The IchnoisCoreLevel.
   * @param message The message.
   * @param args The optional arguments to format the message.
   */
  @Override
  public void log(
      final @NotNull IchnoisCoreLevel level,
      final @NotNull String message,
      final @Nullable Object... args) {
    if (logger != null && isEnabled(level)) {
      logger.log(level, message, args);
    }
  }

  /**
   * Logs a message with the specified level, message and throwable.
   *
   * @param level The IchnoisCoreLevel.
   * @param message The message.
   * @param throwable The throwable to log.
   */
  @Override
  public void log(
      final @NotNull IchnoisCoreLevel level,
      final @NotNull String message,
      final @Nullable Throwable throwable) {
    if (logger != null && isEnabled(level)) {
      logger.log(level, message, throwable);
    }
  }

  /**
   * Logs a message with the specified level, throwable, message and optional arguments.
   *
   * @param level The IchnoisCoreLevel.
   * @param throwable The throwable to log.
   * @param message The message.
   * @param args The optional arguments to format the message.
   */
  @Override
  public void log(
      final @NotNull IchnoisCoreLevel level,
      final @Nullable Throwable throwable,
      final @NotNull String message,
      final @Nullable Object... args) {
    if (logger != null && isEnabled(level)) {
      logger.log(level, throwable, message, args);
    }
  }

  @TestOnly
  public @Nullable ILogger getLogger() {
    return logger;
  }
}
