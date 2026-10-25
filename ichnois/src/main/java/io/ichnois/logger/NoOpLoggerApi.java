package io.ichnois.logger;

import io.ichnois.IchnoisCoreDate;
import io.ichnois.IchnoisCoreLogLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NoOpLoggerApi implements ILoggerApi {

  private static final NoOpLoggerApi instance = new NoOpLoggerApi();

  private NoOpLoggerApi() {}

  public static NoOpLoggerApi getInstance() {
    return instance;
  }

  @Override
  public void trace(@Nullable String message, @Nullable Object... args) {
    // do nothing
  }

  @Override
  public void debug(@Nullable String message, @Nullable Object... args) {
    // do nothing
  }

  @Override
  public void info(@Nullable String message, @Nullable Object... args) {
    // do nothing
  }

  @Override
  public void warn(@Nullable String message, @Nullable Object... args) {
    // do nothing
  }

  @Override
  public void error(@Nullable String message, @Nullable Object... args) {
    // do nothing
  }

  @Override
  public void fatal(@Nullable String message, @Nullable Object... args) {
    // do nothing
  }

  @Override
  public void log(
      @NotNull IchnoisCoreLogLevel level, @Nullable String message, @Nullable Object... args) {
    // do nothing
  }

  @Override
  public void log(
      @NotNull IchnoisCoreLogLevel level,
      @Nullable IchnoisCoreDate timestamp,
      @Nullable String message,
      @Nullable Object... args) {
    // do nothing
  }

  @Override
  public void log(
      @NotNull IchnoisCoreLogLevel level,
      @NotNull IchnoisCoreLogParameters params,
      @Nullable String message,
      @Nullable Object... args) {
    // do nothing
  }
}
