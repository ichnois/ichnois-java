package io.ichnois;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class NoOpIchnoisCoreExecutorService implements IIchnoisCoreExecutorService {
  private static final NoOpIchnoisCoreExecutorService instance = new NoOpIchnoisCoreExecutorService();

  private NoOpIchnoisCoreExecutorService() {}

  public static @NotNull IIchnoisCoreExecutorService getInstance() {
    return instance;
  }

  @Override
  public @NotNull Future<?> submit(final @NotNull Runnable runnable) {
    return new CancelledFuture<>();
  }

  @Override
  public @NotNull <T> Future<T> submit(final @NotNull Callable<T> callable) {
    return new CancelledFuture<>();
  }

  @Override
  public @NotNull Future<?> schedule(@NotNull Runnable runnable, long delayMillis) {
    return new CancelledFuture<>();
  }

  @Override
  public void close(long timeoutMillis) {}

  @Override
  public boolean isClosed() {
    return false;
  }
}
