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

@SuppressWarnings("deprecation")
@Deprecated
public final class HubScopesWrapper implements IHub {

  private final @NotNull IScopes scopes;

  public HubScopesWrapper(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  public @NotNull IScopes getScopes() {
    return scopes;
  }

  @Override
  public boolean isEnabled() {
    return scopes.isEnabled();
  }

  @Override
  public @NotNull IchnoisCoreId captureEvent(@NotNull IchnoisCoreEvent event, @Nullable Hint hint) {
    return scopes.captureEvent(event, hint);
  }

  @Override
  public @NotNull IchnoisCoreId captureEvent(
      @NotNull IchnoisCoreEvent event, @Nullable Hint hint, @NotNull ScopeCallback callback) {
    return scopes.captureEvent(event, hint, callback);
  }

  @Override
  public @NotNull IchnoisCoreId captureMessage(@NotNull String message, @NotNull IchnoisCoreLevel level) {
    return scopes.captureMessage(message, level);
  }

  @Override
  public @NotNull IchnoisCoreId captureMessage(
      @NotNull String message, @NotNull IchnoisCoreLevel level, @NotNull ScopeCallback callback) {
    return scopes.captureMessage(message, level, callback);
  }

  @Override
  public @NotNull IchnoisCoreId captureFeedback(
      @NotNull Feedback feedback, @Nullable Hint hint, @Nullable ScopeCallback callback) {
    return scopes.captureFeedback(feedback, hint, callback);
  }

  @Override
  public @NotNull IchnoisCoreId captureEnvelope(@NotNull IchnoisCoreEnvelope envelope, @Nullable Hint hint) {
    return scopes.captureEnvelope(envelope, hint);
  }

  @Override
  public @NotNull IchnoisCoreId captureException(@NotNull Throwable throwable, @Nullable Hint hint) {
    return scopes.captureException(throwable, hint);
  }

  @Override
  public @NotNull IchnoisCoreId captureException(
      @NotNull Throwable throwable, @Nullable Hint hint, @NotNull ScopeCallback callback) {
    return scopes.captureException(throwable, hint, callback);
  }

  @Override
  public void captureUserFeedback(@NotNull UserFeedback userFeedback) {
    scopes.captureUserFeedback(userFeedback);
  }

  @Override
  public void startSession() {
    scopes.startSession();
  }

  @Override
  public void endSession() {
    scopes.endSession();
  }

  @Override
  public void close() {
    scopes.close();
  }

  @Override
  public void close(boolean isRestarting) {
    scopes.close(isRestarting);
  }

  @Override
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb, @Nullable Hint hint) {
    scopes.addBreadcrumb(breadcrumb, hint);
  }

  @Override
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb) {
    scopes.addBreadcrumb(breadcrumb);
  }

  @Override
  public void setLevel(@Nullable IchnoisCoreLevel level) {
    scopes.setLevel(level);
  }

  @Override
  public void setTransaction(@Nullable String transaction) {
    scopes.setTransaction(transaction);
  }

  @Override
  public void setUser(@Nullable User user) {
    scopes.setUser(user);
  }

  @Override
  public void setFingerprint(@NotNull List<String> fingerprint) {
    scopes.setFingerprint(fingerprint);
  }

  @Override
  public void clearBreadcrumbs() {
    scopes.clearBreadcrumbs();
  }

  @Override
  public void setTag(@Nullable String key, @Nullable String value) {
    scopes.setTag(key, value);
  }

  @Override
  public void removeTag(@Nullable String key) {
    scopes.removeTag(key);
  }

  @Override
  public void setExtra(@Nullable String key, @Nullable String value) {
    scopes.setExtra(key, value);
  }

  @Override
  public void removeExtra(@Nullable String key) {
    scopes.removeExtra(key);
  }

  @Override
  public @NotNull IchnoisCoreId getLastEventId() {
    return scopes.getLastEventId();
  }

  @Override
  public @NotNull IIchnoisCoreLifecycleToken pushScope() {
    return scopes.pushScope();
  }

  @Override
  public @NotNull IIchnoisCoreLifecycleToken pushIsolationScope() {
    return scopes.pushIsolationScope();
  }

  /**
   * @deprecated please call {@link IIchnoisCoreLifecycleToken#close()} on the token returned by {@link
   *     IScopes#pushScope()} or {@link IScopes#pushIsolationScope()} instead.
   */
  @Override
  @Deprecated
  public void popScope() {
    scopes.popScope();
  }

  @Override
  public void withScope(@NotNull ScopeCallback callback) {
    scopes.withScope(callback);
  }

  @Override
  public void withIsolationScope(@NotNull ScopeCallback callback) {
    scopes.withIsolationScope(callback);
  }

  @Override
  public void configureScope(@Nullable ScopeType scopeType, @NotNull ScopeCallback callback) {
    scopes.configureScope(scopeType, callback);
  }

  @Override
  public void bindClient(@NotNull IIchnoisCoreClient client) {
    scopes.bindClient(client);
  }

  @Override
  public boolean isHealthy() {
    return scopes.isHealthy();
  }

  @Override
  public void flush(long timeoutMillis) {
    scopes.flush(timeoutMillis);
  }

  /**
   * @deprecated please use {@link IScopes#forkedScopes(String)} or {@link
   *     IScopes#forkedCurrentScope(String)} instead.
   */
  @Override
  @Deprecated
  public @NotNull IHub clone() {
    return scopes.clone();
  }

  @Override
  public @NotNull IScopes forkedScopes(@NotNull String creator) {
    return scopes.forkedScopes(creator);
  }

  @Override
  public @NotNull IScopes forkedCurrentScope(@NotNull String creator) {
    return scopes.forkedCurrentScope(creator);
  }

  @Override
  public @NotNull IScopes forkedRootScopes(final @NotNull String creator) {
    return IchnoisCore.forkedRootScopes(creator);
  }

  @Override
  public @NotNull IIchnoisCoreLifecycleToken makeCurrent() {
    return scopes.makeCurrent();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getScope() {
    return scopes.getScope();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getIsolationScope() {
    return scopes.getIsolationScope();
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getGlobalScope() {
    return IchnoisCore.getGlobalScope();
  }

  @Override
  public @Nullable IScopes getParentScopes() {
    return scopes.getParentScopes();
  }

  @Override
  public boolean isAncestorOf(final @Nullable IScopes otherScopes) {
    return scopes.isAncestorOf(otherScopes);
  }

  @ApiStatus.Internal
  @Override
  public @NotNull IchnoisCoreId captureTransaction(
      @NotNull IchnoisCoreTransaction transaction,
      @Nullable TraceContext traceContext,
      @Nullable Hint hint,
      @Nullable ProfilingTraceData profilingTraceData) {
    return scopes.captureTransaction(transaction, traceContext, hint, profilingTraceData);
  }

  @Override
  public @NotNull IchnoisCoreId captureProfileChunk(@NotNull ProfileChunk profileChunk) {
    return scopes.captureProfileChunk(profileChunk);
  }

  @Override
  public @NotNull ITransaction startTransaction(
      @NotNull TransactionContext transactionContext,
      @NotNull TransactionOptions transactionOptions) {
    return scopes.startTransaction(transactionContext, transactionOptions);
  }

  @Override
  public void startProfiler() {
    scopes.startProfiler();
  }

  @Override
  public void stopProfiler() {
    scopes.stopProfiler();
  }

  @ApiStatus.Internal
  @Override
  public void setSpanContext(
      @NotNull Throwable throwable, @NotNull ISpan span, @NotNull String transactionName) {
    scopes.setSpanContext(throwable, span, transactionName);
  }

  @Override
  public @Nullable ISpan getSpan() {
    return scopes.getSpan();
  }

  @Override
  public void setActiveSpan(final @Nullable ISpan span) {
    scopes.setActiveSpan(span);
  }

  @ApiStatus.Internal
  @Override
  public @Nullable ITransaction getTransaction() {
    return scopes.getTransaction();
  }

  @Override
  public @NotNull IchnoisCoreOptions getOptions() {
    return scopes.getOptions();
  }

  @Override
  public @Nullable Boolean isCrashedLastRun() {
    return scopes.isCrashedLastRun();
  }

  @Override
  public void reportFullyDisplayed() {
    scopes.reportFullyDisplayed();
  }

  @Override
  public @Nullable TransactionContext continueTrace(
      @Nullable String ichnoisTrace, @Nullable List<String> baggageHeaders) {
    return scopes.continueTrace(ichnoisTrace, baggageHeaders);
  }

  @Override
  public @Nullable IchnoisCoreTraceHeader getTraceparent() {
    return scopes.getTraceparent();
  }

  @Override
  public @Nullable BaggageHeader getBaggage() {
    return scopes.getBaggage();
  }

  @Override
  public @NotNull IchnoisCoreId captureCheckIn(@NotNull CheckIn checkIn) {
    return scopes.captureCheckIn(checkIn);
  }

  @ApiStatus.Internal
  @Override
  public @Nullable RateLimiter getRateLimiter() {
    return scopes.getRateLimiter();
  }

  @Override
  public @NotNull IchnoisCoreId captureReplay(@NotNull IchnoisCoreReplayEvent replay, @Nullable Hint hint) {
    return scopes.captureReplay(replay, hint);
  }

  @Override
  public @NotNull ILoggerApi logger() {
    return scopes.logger();
  }

  @Override
  public @NotNull IMetricsApi metrics() {
    return scopes.metrics();
  }

  @Override
  public @NotNull IFeedbackApi feedback() {
    return scopes.feedback();
  }

  @Override
  public void setAttribute(final @Nullable String key, final @Nullable Object value) {
    scopes.setAttribute(key, value);
  }

  @Override
  public void setAttribute(final @Nullable IchnoisCoreAttribute attribute) {
    scopes.setAttribute(attribute);
  }

  @Override
  public void setAttributes(final @Nullable IchnoisCoreAttributes attributes) {
    scopes.setAttributes(attributes);
  }

  @Override
  public void removeAttribute(final @Nullable String key) {
    scopes.removeAttribute(key);
  }

  @Override
  public void addFeatureFlag(final @Nullable String flag, final @Nullable Boolean result) {
    scopes.addFeatureFlag(flag, result);
  }
}
