package io.ichnois;

import io.ichnois.profiling.ProfileRecordingState;
import io.ichnois.protocol.IchnoisCoreId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class NoOpContinuousProfiler implements IContinuousProfiler {

  private static final NoOpContinuousProfiler instance = new NoOpContinuousProfiler();

  private NoOpContinuousProfiler() {}

  public static NoOpContinuousProfiler getInstance() {
    return instance;
  }

  @Override
  public void stopProfiler(final @NotNull ProfileLifecycle profileLifecycle) {}

  @Override
  public boolean isRunning() {
    return false;
  }

  @Override
  public void startProfiler(
      final @NotNull ProfileLifecycle profileLifecycle,
      final @NotNull TracesSampler tracesSampler) {}

  @Override
  public void close(final boolean isTerminating) {}

  @Override
  public void reevaluateSampling() {}

  @Override
  public @NotNull IchnoisCoreId getProfilerId() {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull IchnoisCoreId getChunkId() {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull ProfileRecordingState getProfileRecordingState(
      final @NotNull IchnoisCoreId profilerId,
      final @NotNull IchnoisCoreDate startTime,
      final @NotNull IchnoisCoreDate endTime) {
    return ProfileRecordingState.UNKNOWN;
  }
}
