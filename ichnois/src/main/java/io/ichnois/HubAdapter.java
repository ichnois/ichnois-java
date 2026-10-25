package io.ichnois;

import io.ichnois.logger.ILoggerApi;
import io.ichnois.metrics.IMetricsApi;
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
 * @deprecated use {@link ScopesAdapter} instead
 */
@Deprecated
public final class HubAdapter implements IHub {

  private static final HubAdapter INSTANCE = new HubAdapter();

  private HubAdapter() {}

  public static HubAdapter getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isEnabled() {
    return IchnoisCore.isEnabled();
  }

  @Override
  public @NotNull IchnoisCoreId captureEvent(@NotNull IchnoisCoreEvent event, @Nullable Hint hint) {
    return IchnoisCore.captureEvent(event, hint);
  }

  @Override
  public @NotNull IchnoisCoreId captureEvent(
      @NotNull IchnoisCoreEvent event, @Nullable Hint hint, @NotNull ScopeCallback callback) {
    return IchnoisCore.captureEvent(event, hint, callback);
  }

  @Override
  public @NotNull IchnoisCoreId captureMessage(@NotNull String message, @NotNull IchnoisCoreLevel level) {
    return IchnoisCore.captureMessage(message, level);
  }

  @Override
  public @NotNull IchnoisCoreId captureMessage(
      @NotNull String message, @NotNull IchnoisCoreLevel level, @NotNull ScopeCallback callback) {
    return IchnoisCore.captureMessage(message, level, callback);
  }

  @Override
  public @NotNull IchnoisCoreId captureFeedback(@NotNull Feedback feedback) {
    return IchnoisCore.captureFeedback(feedback);
  }

  @Override
  public @NotNull IchnoisCoreId captureFeedback(@NotNull Feedback feedback, @Nullable Hint hint) {
    return IchnoisCore.captureFeedback(feedback, hint);
  }

  @Override
  public @NotNull IchnoisCoreId captureFeedback(
      @NotNull Feedback feedback, @Nullable Hint hint, @Nullable ScopeCallback callback) {
    return IchnoisCore.captureFeedback(feedback, hint, callback);
  }

  @ApiStatus.Internal
  @Override
  public @NotNull IchnoisCoreId captureEnvelope(@NotNull IchnoisCoreEnvelope envelope, @Nullable Hint hint) {
    return IchnoisCore.getCurrentScopes().captureEnvelope(envelope, hint);
  }

  @Override
  public @NotNull IchnoisCoreId captureException(@NotNull Throwable throwable, @Nullable Hint hint) {
    return IchnoisCore.captureException(throwable, hint);
  }

  @Override
  public @NotNull IchnoisCoreId captureException(
      @NotNull Throwable throwable, @Nullable Hint hint, @NotNull ScopeCallback callback) {
    return IchnoisCore.captureException(throwable, hint, callback);
  }

  @Override
  public void captureUserFeedback(@NotNull UserFeedback userFeedback) {
    IchnoisCore.captureUserFeedback(userFeedback);
  }

  @Override
  public void startSession() {
    IchnoisCore.startSession();
  }

  @Override
  public void endSession() {
    IchnoisCore.endSession();
  }

  @Override
  public void close(final boolean isRestarting) {
    IchnoisCore.close();
  }

  @Override
  public void close() {
    IchnoisCore.close();
  }

  @Override
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb, @Nullable Hint hint) {
    IchnoisCore.addBreadcrumb(breadcrumb, hint);
  }

  @Override
  public void addBreadcrumb(final @NotNull Breadcrumb breadcrumb) {
    addBreadcrumb(breadcrumb, new Hint());
  }

  @Override
  public void setLevel(@Nullable IchnoisCoreLevel level) {
    IchnoisCore.setLevel(level);
  }

  @Override
  public void setTransaction(@Nullable String transaction) {
    IchnoisCore.setTransaction(transaction);
  }

  @Override
  public void setUser(@Nullable User user) {
    IchnoisCore.setUser(user);
  }

  @Override
  public void setFingerprint(@NotNull List<String> fingerprint) {
    IchnoisCore.setFingerprint(fingerprint);
  }

  @Override
  public void clearBreadcrumbs() {
    IchnoisCore.clearBreadcrumbs();
  }

  @Override
  public void setTag(@Nullable String key, @Nullable String value) {
    IchnoisCore.setTag(key, value);
  }

  @Override
  public void removeTag(@Nullable String key) {
    IchnoisCore.removeTag(key);
  }

  @Override
  public void setExtra(@Nullable String key, @Nullable String value) {
    IchnoisCore.setExtra(key, value);
  }

  @Override
  public void removeExtra(@Nullable String key) {
    IchnoisCore.removeExtra(key);
  }

  @Override
  public @NotNull IchnoisCoreId getLastEventId() {
    return IchnoisCore.getLastEventId();
  }

  @Override
  public @NotNull IIchnoisCoreLifecycleToken pushScope() {
    return IchnoisCore.pushScope();
  }

  @Override
  public @NotNull IIchnoisCoreLifecycleToken pushIsolationScope() {
    return IchnoisCore.pushIsolationScope();
  }

  /**
   * @deprecated please call {@link IIchnoisCoreLifecycleToken#close()} on the token returned by {@link
   *     ScopesAdapter#pushScope()} or {@link ScopesAdapter#pushIsolationScope()} instead.
   */
  @Override
  @Deprecated
  public void popScope() {
    IchnoisCore.popScope();
  }

  @Override
  public void withScope(@NotNull ScopeCallback callback) {
    IchnoisCore.withScope(callback);
  }

  @Override
  public void withIsolationScope(@NotNull ScopeCallback callback) {
    IchnoisCore.withIsolationScope(callback);
  }

  @Override
  public void configureScope(@Nullable ScopeType scopeType, @NotNull ScopeCallback callback) {
    IchnoisCore.configureScope(scopeType, callback);
  }

  @Override
  public void bindClient(@NotNull IIchnoisCoreClient client) {
    IchnoisCore.bindClient(client);
  }

  @Override
  public boolean isHealthy() {
    return IchnoisCore.isHealthy();
  }

  @Override
  public void flush(long timeoutMillis) {
    IchnoisCore.flush(timeoutMillis);
  }

  /**
   * @deprecated please use {@link IScopes#forkedScopes(String)} or {@link
   *     IScopes#forkedCurrentScope(String)} instead.
   */
  @Deprecated
  @Override
  public @NotNull IHub clone() {
    return IchnoisCore.getCurrentScopes().clone();
  }

  @Override
  public @NotNull IScopes forkedScopes(@NotNull String creator) {
    return IchnoisCore.forkedScopes(creator);
  }

  @Override
  public @NotNull IScopes forkedCurrentScope(@NotNull String creator) {
    return IchnoisCore.forkedCurrentScope(creator);
  }

  @Override
  public @NotNull IScopes forkedRootScopes(final @NotNull String creator) {
    return IchnoisCore.forkedRootScopes(creator);
  }

  @Override
  public @NotNull IIchnoisCoreLifecycleToken makeCurrent() {
    return NoOpScopesLifecycleToken.getInstance();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getScope() {
    return IchnoisCore.getCurrentScopes().getScope();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getIsolationScope() {
    return IchnoisCore.getCurrentScopes().getIsolationScope();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getGlobalScope() {
    return IchnoisCore.getGlobalScope();
  }

  @Override
  public @Nullable IScopes getParentScopes() {
    return IchnoisCore.getCurrentScopes().getParentScopes();
  }

  @Override
  public boolean isAncestorOf(final @Nullable IScopes otherScopes) {
    return IchnoisCore.getCurrentScopes().isAncestorOf(otherScopes);
  }

  @Override
  public @NotNull IchnoisCoreId captureTransaction(
      @NotNull IchnoisCoreTransaction transaction,
      @Nullable TraceContext traceContext,
      @Nullable Hint hint,
      @Nullable ProfilingTraceData profilingTraceData) {
    return IchnoisCore.getCurrentScopes()
        .captureTransaction(transaction, traceContext, hint, profilingTraceData);
  }

  @Override
  public @NotNull ITransaction startTransaction(
      @NotNull TransactionContext transactionContext,
      @NotNull TransactionOptions transactionOptions) {
    return IchnoisCore.startTransaction(transactionContext, transactionOptions);
  }

  @Override
  public void startProfiler() {
    IchnoisCore.startProfiler();
  }

  @Override
  public void stopProfiler() {
    IchnoisCore.stopProfiler();
  }

  @Override
  public @NotNull IchnoisCoreId captureProfileChunk(
      final @NotNull ProfileChunk profilingContinuousData) {
    return IchnoisCore.getCurrentScopes().captureProfileChunk(profilingContinuousData);
  }

  @Override
  public void setSpanContext(
      final @NotNull Throwable throwable,
      final @NotNull ISpan span,
      final @NotNull String transactionName) {
    IchnoisCore.getCurrentScopes().setSpanContext(throwable, span, transactionName);
  }

  @Override
  public @Nullable ISpan getSpan() {
    return IchnoisCore.getCurrentScopes().getSpan();
  }

  @Override
  public void setActiveSpan(final @Nullable ISpan span) {
    IchnoisCore.getCurrentScopes().setActiveSpan(span);
  }

  @Override
  @ApiStatus.Internal
  public @Nullable ITransaction getTransaction() {
    return IchnoisCore.getCurrentScopes().getTransaction();
  }

  @Override
  public @NotNull IchnoisCoreOptions getOptions() {
    return IchnoisCore.getCurrentScopes().getOptions();
  }

  @Override
  public @Nullable Boolean isCrashedLastRun() {
    return IchnoisCore.isCrashedLastRun();
  }

  @Override
  public void reportFullyDisplayed() {
    IchnoisCore.reportFullyDisplayed();
  }

  @Override
  public @Nullable TransactionContext continueTrace(
      final @Nullable String ichnoisTrace, final @Nullable List<String> baggageHeaders) {
    return IchnoisCore.continueTrace(ichnoisTrace, baggageHeaders);
  }

  @Override
  public @Nullable IchnoisCoreTraceHeader getTraceparent() {
    return IchnoisCore.getTraceparent();
  }

  @Override
  public @Nullable BaggageHeader getBaggage() {
    return IchnoisCore.getBaggage();
  }

  @Override
  @ApiStatus.Experimental
  public @NotNull IchnoisCoreId captureCheckIn(final @NotNull CheckIn checkIn) {
    return IchnoisCore.captureCheckIn(checkIn);
  }

  @Override
  public @NotNull IchnoisCoreId captureReplay(
      final @NotNull IchnoisCoreReplayEvent replay, final @Nullable Hint hint) {
    return IchnoisCore.getCurrentScopes().captureReplay(replay, hint);
  }

  @ApiStatus.Internal
  @Override
  public @Nullable RateLimiter getRateLimiter() {
    return IchnoisCore.getCurrentScopes().getRateLimiter();
  }

  @Override
  public @NotNull ILoggerApi logger() {
    return IchnoisCore.getCurrentScopes().logger();
  }

  @Override
  public @NotNull IMetricsApi metrics() {
    return IchnoisCore.getCurrentScopes().metrics();
  }

  @Override
  public @NotNull IFeedbackApi feedback() {
    return IchnoisCore.getCurrentScopes().feedback();
  }

  @Override
  public void setAttribute(final @Nullable String key, final @Nullable Object value) {
    IchnoisCore.setAttribute(key, value);
  }

  @Override
  public void setAttribute(final @Nullable IchnoisCoreAttribute attribute) {
    IchnoisCore.setAttribute(attribute);
  }

  @Override
  public void setAttributes(final @Nullable IchnoisCoreAttributes attributes) {
    IchnoisCore.setAttributes(attributes);
  }

  @Override
  public void removeAttribute(final @Nullable String key) {
    IchnoisCore.removeAttribute(key);
  }

  @Override
  public void addFeatureFlag(final @Nullable String flag, final @Nullable Boolean result) {
    IchnoisCore.addFeatureFlag(flag, result);
  }
}
