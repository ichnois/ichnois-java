package io.ichnois;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** ILogger implementation to System.out. */
public final class SystemOutLogger implements ILogger {

  /**
   * Logs to console a message with the specified level, message and optional arguments.
   *
   * @param level The IchnoisCoreLevel.
   * @param message The message.
   * @param args The optional arguments to format the message.
   */
  @SuppressWarnings("AnnotateFormatMethod")
  @Override
  public void log(
      final @NotNull IchnoisCoreLevel level,
      final @NotNull String message,
      final @Nullable Object... args) {
    System.out.println(String.format("%s: %s", level, String.format(message, args)));
  }

  /**
   * Logs to console a message with the specified level, message and throwable.
   *
   * @param level The IchnoisCoreLevel.
   * @param message The message.
   * @param throwable The throwable to log.
   */
  @SuppressWarnings("AnnotateFormatMethod")
  @Override
  public void log(
      final @NotNull IchnoisCoreLevel level,
      final @NotNull String message,
      final @Nullable Throwable throwable) {
    if (throwable == null) {
      this.log(level, message);
    } else {
      System.out.println(
          String.format(
              "%s: %s\n%s",
              level, String.format(message, throwable.toString()), captureStackTrace(throwable)));
    }
  }

  /**
   * Logs to console a message with the specified level, throwable, message and optional arguments.
   *
   * @param level The IchnoisCoreLevel.
   * @param throwable The throwable to log.
   * @param message The message.
   * @param args The optional arguments to format the message.
   */
  @SuppressWarnings("AnnotateFormatMethod")
  @Override
  public void log(
      final @NotNull IchnoisCoreLevel level,
      final @Nullable Throwable throwable,
      final @NotNull String message,
      final @Nullable Object... args) {
    if (throwable == null) {
      this.log(level, message, args);
    } else {
      System.out.println(
          String.format(
              "%s: %s \n %s\n%s",
              level,
              String.format(message, args),
              throwable.toString(),
              captureStackTrace(throwable)));
    }
  }

  @Override
  public boolean isEnabled(final @Nullable IchnoisCoreLevel level) {
    return true;
  }

  private @NotNull String captureStackTrace(final @NotNull Throwable throwable) {
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    throwable.printStackTrace(printWriter);
    return stringWriter.toString();
  }
}
