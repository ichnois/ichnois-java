package io.ichnois;

import io.ichnois.protocol.Feedback;
import io.ichnois.protocol.IchnoisCoreId;
import io.ichnois.protocol.IchnoisCoreTransaction;
import io.ichnois.transport.RateLimiter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class NoOpIchnoisCoreClient implements IIchnoisCoreClient {

  private static final NoOpIchnoisCoreClient instance = new NoOpIchnoisCoreClient();

  private NoOpIchnoisCoreClient() {}

  public static NoOpIchnoisCoreClient getInstance() {
    return instance;
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public @NotNull IchnoisCoreId captureEvent(
      @NotNull IchnoisCoreEvent event, @Nullable IScope scope, @Nullable Hint hint) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public void close(final boolean isRestarting) {}

  @Override
  public void close() {}

  @Override
  public void flush(long timeoutMillis) {}

  @Override
  public @NotNull IchnoisCoreId captureFeedback(
      @NotNull Feedback feedback, @Nullable Hint hint, @NotNull IScope scope) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Deprecated
  @Override
  public void captureUserFeedback(@NotNull UserFeedback userFeedback) {}

  @Override
  public void captureSession(@NotNull Session session, @Nullable Hint hint) {}

  @Override
  public IchnoisCoreId captureEnvelope(@NotNull IchnoisCoreEnvelope envelope, @Nullable Hint hint) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull IchnoisCoreId captureTransaction(
      @NotNull IchnoisCoreTransaction transaction,
      @Nullable TraceContext traceContext,
      @Nullable IScope scope,
      @Nullable Hint hint,
      @Nullable ProfilingTraceData profilingTraceData) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull IchnoisCoreId captureProfileChunk(
      final @NotNull ProfileChunk profileChunk, final @Nullable IScope scope) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull IchnoisCoreId captureCheckIn(
      @NotNull CheckIn checkIn, @Nullable IScope scope, @Nullable Hint hint) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull IchnoisCoreId captureReplayEvent(
      @NotNull IchnoisCoreReplayEvent event, @Nullable IScope scope, @Nullable Hint hint) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public void captureLog(@NotNull IchnoisCoreLogEvent logEvent, @Nullable IScope scope) {
    // do nothing
  }

  @Override
  public void captureMetric(
      @NotNull IchnoisCoreMetricsEvent metricsEvent, @Nullable IScope scope, @Nullable Hint hint) {
    // do nothing
  }

  @ApiStatus.Internal
  @Override
  public void captureBatchedLogEvents(@NotNull IchnoisCoreLogEvents logEvents) {
    // do nothing
  }

  @ApiStatus.Internal
  @Override
  public void captureBatchedMetricsEvents(@NotNull IchnoisCoreMetricsEvents metricsEvents) {
    // do nothing
  }

  @Override
  public @Nullable RateLimiter getRateLimiter() {
    return null;
  }
}
