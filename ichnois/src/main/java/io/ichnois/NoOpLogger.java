package io.ichnois;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** No-op implementation of ILogger */
@ApiStatus.Internal
public final class NoOpLogger implements ILogger {

  private static final NoOpLogger instance = new NoOpLogger();

  public static NoOpLogger getInstance() {
    return instance;
  }

  private NoOpLogger() {}

  @Override
  public void log(@NotNull IchnoisCoreLevel level, @NotNull String message, @Nullable Object... args) {}

  @Override
  public void log(
      @NotNull IchnoisCoreLevel level, @NotNull String message, @Nullable Throwable throwable) {}

  @Override
  public void log(
      @NotNull IchnoisCoreLevel level,
      @Nullable Throwable throwable,
      @NotNull String message,
      @Nullable Object... args) {}

  @Override
  public boolean isEnabled(@Nullable IchnoisCoreLevel level) {
    return false;
  }
}
