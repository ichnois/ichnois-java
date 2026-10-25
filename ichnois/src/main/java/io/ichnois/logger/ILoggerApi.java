package io.ichnois.logger;

import io.ichnois.IchnoisCoreDate;
import io.ichnois.IchnoisCoreLogLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ILoggerApi {

  void trace(final @Nullable String message, @Nullable Object... args);

  void debug(final @Nullable String message, @Nullable Object... args);

  void info(final @Nullable String message, @Nullable Object... args);

  void warn(final @Nullable String message, @Nullable Object... args);

  void error(final @Nullable String message, @Nullable Object... args);

  void fatal(final @Nullable String message, @Nullable Object... args);

  void log(@NotNull IchnoisCoreLogLevel level, @Nullable String message, @Nullable Object... args);

  void log(
      @NotNull IchnoisCoreLogLevel level,
      @Nullable IchnoisCoreDate timestamp,
      @Nullable String message,
      @Nullable Object... args);

  void log(
      @NotNull IchnoisCoreLogLevel level,
      @NotNull IchnoisCoreLogParameters params,
      @Nullable String message,
      @Nullable Object... args);
}
