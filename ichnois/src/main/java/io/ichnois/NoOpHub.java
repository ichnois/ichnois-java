package io.ichnois;

import io.ichnois.logger.ILoggerApi;
import io.ichnois.logger.NoOpLoggerApi;
import io.ichnois.metrics.IMetricsApi;
import io.ichnois.metrics.NoOpMetricsApi;
import io.ichnois.protocol.Feedback;
import io.ichnois.protocol.IchnoisCoreId;
import io.ichnois.protocol.IchnoisCoreTransaction;
import io.ichnois.protocol.User;
import io.ichnois.transport.RateLimiter;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated use {@link NoOpScopes} instead.
 */
@Deprecated
@ApiStatus.Internal
public final class NoOpHub implements IHub {

  private static final NoOpHub instance = new NoOpHub();

  private final @NotNull IchnoisCoreOptions emptyOptions = IchnoisCoreOptions.empty();

  private NoOpHub() {}

  @Deprecated
  public static NoOpHub getInstance() {
    return instance;
  }

  @Override
  public boolean isEnabled() {
    return false;
  }

  @Override
  public @NotNull IchnoisCoreId captureEvent(@NotNull IchnoisCoreEvent event, @Nullable Hint hint) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull IchnoisCoreId captureEvent(
      @NotNull IchnoisCoreEvent event, @Nullable Hint hint, @NotNull ScopeCallback callback) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull IchnoisCoreId captureMessage(@NotNull String message, @NotNull IchnoisCoreLevel level) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull IchnoisCoreId captureMessage(
      @NotNull String message, @NotNull IchnoisCoreLevel level, @NotNull ScopeCallback callback) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull IchnoisCoreId captureFeedback(
      @NotNull Feedback feedback, @Nullable Hint hint, @Nullable ScopeCallback callback) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull IchnoisCoreId captureEnvelope(@NotNull IchnoisCoreEnvelope envelope, @Nullable Hint hint) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull IchnoisCoreId captureException(@NotNull Throwable throwable, @Nullable Hint hint) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull IchnoisCoreId captureException(
      @NotNull Throwable throwable, @Nullable Hint hint, @NotNull ScopeCallback callback) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Deprecated
  @Override
  public void captureUserFeedback(@NotNull UserFeedback userFeedback) {}

  @Override
  public void startSession() {}

  @Override
  public void endSession() {}

  @Override
  public void close() {}

  @Override
  public void close(final boolean isRestarting) {}

  @Override
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb, @Nullable Hint hint) {}

  @Override
  public void addBreadcrumb(final @NotNull Breadcrumb breadcrumb) {}

  @Override
  public void setLevel(@Nullable IchnoisCoreLevel level) {}

  @Override
  public void setTransaction(@Nullable String transaction) {}

  @Override
  public void setUser(@Nullable User user) {}

  @Override
  public void setFingerprint(@NotNull List<String> fingerprint) {}

  @Override
  public void clearBreadcrumbs() {}

  @Override
  public void setTag(@Nullable String key, @Nullable String value) {}

  @Override
  public void removeTag(@Nullable String key) {}

  @Override
  public void setExtra(@Nullable String key, @Nullable String value) {}

  @Override
  public void removeExtra(@Nullable String key) {}

  @Override
  public @NotNull IchnoisCoreId getLastEventId() {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull IIchnoisCoreLifecycleToken pushScope() {
    return NoOpScopesLifecycleToken.getInstance();
  }

  @Override
  public @NotNull IIchnoisCoreLifecycleToken pushIsolationScope() {
    return NoOpScopesLifecycleToken.getInstance();
  }

  /**
   * @deprecated please call {@link IIchnoisCoreLifecycleToken#close()} on the token returned by {@link
   *     IScopes#pushScope()} or {@link IScopes#pushIsolationScope()} instead.
   */
  @Override
  @Deprecated
  public void popScope() {}

  @Override
  public void withScope(@NotNull ScopeCallback callback) {
    callback.run(NoOpScope.getInstance());
  }

  @Override
  public void withIsolationScope(@NotNull ScopeCallback callback) {
    callback.run(NoOpScope.getInstance());
  }

  @Override
  public void configureScope(@Nullable ScopeType scopeType, @NotNull ScopeCallback callback) {}

  @Override
  public void bindClient(@NotNull IIchnoisCoreClient client) {}

  @Override
  public boolean isHealthy() {
    return true;
  }

  @Override
  public void flush(long timeoutMillis) {}

  /**
   * @deprecated please use {@link IScopes#forkedScopes(String)} or {@link
   *     IScopes#forkedCurrentScope(String)} instead.
   */
  @Deprecated
  @Override
  public @NotNull IHub clone() {
    return instance;
  }

  @Override
  public @NotNull IScopes forkedScopes(@NotNull String creator) {
    return NoOpScopes.getInstance();
  }

  @Override
  public @NotNull IScopes forkedCurrentScope(@NotNull String creator) {
    return NoOpScopes.getInstance();
  }

  @Override
  public @NotNull IIchnoisCoreLifecycleToken makeCurrent() {
    return NoOpScopesLifecycleToken.getInstance();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getScope() {
    return NoOpScope.getInstance();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getIsolationScope() {
    return NoOpScope.getInstance();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getGlobalScope() {
    return NoOpScope.getInstance();
  }

  @Override
  public @Nullable IScopes getParentScopes() {
    return null;
  }

  @Override
  public boolean isAncestorOf(@Nullable IScopes otherScopes) {
    return false;
  }

  @Override
  public @NotNull IScopes forkedRootScopes(final @NotNull String creator) {
    return NoOpScopes.getInstance();
  }

  @Override
  public @NotNull IchnoisCoreId captureTransaction(
      final @NotNull IchnoisCoreTransaction transaction,
      final @Nullable TraceContext traceContext,
      final @Nullable Hint hint,
      final @Nullable ProfilingTraceData profilingTraceData) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull IchnoisCoreId captureProfileChunk(final @NotNull ProfileChunk profileChunk) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull ITransaction startTransaction(
      @NotNull TransactionContext transactionContext,
      @NotNull TransactionOptions transactionOptions) {
    return NoOpTransaction.getInstance();
  }

  @Override
  public void startProfiler() {}

  @Override
  public void stopProfiler() {}

  @Override
  public void setSpanContext(
      final @NotNull Throwable throwable,
      final @NotNull ISpan spanContext,
      final @NotNull String transactionName) {}

  @Override
  public @Nullable ISpan getSpan() {
    return null;
  }

  @Override
  public void setActiveSpan(final @Nullable ISpan span) {}

  @Override
  public @Nullable ITransaction getTransaction() {
    return null;
  }

  @Override
  public @NotNull IchnoisCoreOptions getOptions() {
    return emptyOptions;
  }

  @Override
  public @Nullable Boolean isCrashedLastRun() {
    return null;
  }

  @Override
  public void reportFullyDisplayed() {}

  @Override
  public @Nullable TransactionContext continueTrace(
      final @Nullable String ichnoisTrace, final @Nullable List<String> baggageHeaders) {
    return null;
  }

  @Override
  public @Nullable IchnoisCoreTraceHeader getTraceparent() {
    return null;
  }

  @Override
  public @Nullable BaggageHeader getBaggage() {
    return null;
  }

  @Override
  public @NotNull IchnoisCoreId captureCheckIn(final @NotNull CheckIn checkIn) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull IchnoisCoreId captureReplay(@NotNull IchnoisCoreReplayEvent replay, @Nullable Hint hint) {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @Nullable RateLimiter getRateLimiter() {
    return null;
  }

  @Override
  public boolean isNoOp() {
    return true;
  }

  @Override
  public @NotNull ILoggerApi logger() {
    return NoOpLoggerApi.getInstance();
  }

  @Override
  public @NotNull IMetricsApi metrics() {
    return NoOpMetricsApi.getInstance();
  }

  @Override
  public @NotNull IFeedbackApi feedback() {
    return NoOpFeedbackApi.getInstance();
  }

  @Override
  public void setAttribute(final @Nullable String key, final @Nullable Object value) {}

  @Override
  public void setAttribute(final @Nullable IchnoisCoreAttribute attribute) {}

  @Override
  public void setAttributes(final @Nullable IchnoisCoreAttributes attributes) {}

  @Override
  public void removeAttribute(final @Nullable String key) {}

  @Override
  public void addFeatureFlag(final @Nullable String flag, final @Nullable Boolean result) {}
}
