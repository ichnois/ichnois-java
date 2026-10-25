package io.ichnois.util.thread;

import io.ichnois.protocol.IchnoisCoreThread;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class NoOpThreadChecker implements IThreadChecker {

  private static final NoOpThreadChecker instance = new NoOpThreadChecker();

  public static NoOpThreadChecker getInstance() {
    return instance;
  }

  @Override
  public boolean isMainThread(long threadId) {
    return false;
  }

  @Override
  public boolean isMainThread(@NotNull Thread thread) {
    return false;
  }

  @Override
  public boolean isMainThread() {
    return false;
  }

  @Override
  public boolean isMainThread(@NotNull IchnoisCoreThread ichnoisThread) {
    return false;
  }

  @Override
  public @NotNull String getCurrentThreadName() {
    return "";
  }

  @Override
  public long currentThreadSystemId() {
    return 0;
  }
}
