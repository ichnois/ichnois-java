package io.ichnois;

import io.ichnois.clientreport.DiscardReason;
import io.ichnois.hints.SessionEndHint;
import io.ichnois.hints.SessionStartHint;
import io.ichnois.logger.ILoggerApi;
import io.ichnois.logger.LoggerApi;
import io.ichnois.metrics.IMetricsApi;
import io.ichnois.metrics.MetricsApi;
import io.ichnois.protocol.*;
import io.ichnois.transport.RateLimiter;
import io.ichnois.util.HintUtils;
import io.ichnois.util.Objects;
import io.ichnois.util.SpanUtils;
import io.ichnois.util.TracingUtils;
import java.io.Closeable;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Scopes implements IScopes {

  private final @NotNull IScope scope;
  private final @NotNull IScope isolationScope;
  private final @NotNull IScope globalScope;

  private final @Nullable Scopes parentScopes;

  private final @NotNull String creator;
  private final @NotNull CompositePerformanceCollector compositePerformanceCollector;

  private final @NotNull CombinedScopeView combinedScope;
  private final @NotNull ILoggerApi logger;
  private final @NotNull IMetricsApi metrics;
  private final @NotNull IFeedbackApi feedbackApi;

  public Scopes(
      final @NotNull IScope scope,
      final @NotNull IScope isolationScope,
      final @NotNull IScope globalScope,
      final @NotNull String creator) {
    this(scope, isolationScope, globalScope, null, creator);
  }

  private Scopes(
      final @NotNull IScope scope,
      final @NotNull IScope isolationScope,
      final @NotNull IScope globalScope,
      final @Nullable Scopes parentScopes,
      final @NotNull String creator) {
    this.combinedScope = new CombinedScopeView(globalScope, isolationScope, scope);
    this.scope = scope;
    this.isolationScope = isolationScope;
    this.globalScope = globalScope;
    this.parentScopes = parentScopes;
    this.creator = creator;

    final @NotNull IchnoisCoreOptions options = getOptions();
    validateOptions(options);
    this.compositePerformanceCollector = options.getCompositePerformanceCollector();
    this.logger = new LoggerApi(this);
    this.metrics = new MetricsApi(this);
    this.feedbackApi = new FeedbackApi(this);
  }

  public @NotNull String getCreator() {
    return creator;
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getScope() {
    return scope;
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getIsolationScope() {
    return isolationScope;
  }

  @Override
  @ApiStatus.Internal
  public @NotNull IScope getGlobalScope() {
    return globalScope;
  }

  @Override
  @ApiStatus.Internal
  public @Nullable IScopes getParentScopes() {
    return parentScopes;
  }

  @Override
  @ApiStatus.Internal
  public boolean isAncestorOf(final @Nullable IScopes otherScopes) {
    if (otherScopes == null) {
      return false;
    }

    if (this == otherScopes) {
      return true;
    }

    if (otherScopes.getParentScopes() != null) {
      return isAncestorOf(otherScopes.getParentScopes());
    }

    return false;
  }

  @Override
  public @NotNull IScopes forkedScopes(final @NotNull String creator) {
    return new Scopes(scope.clone(), isolationScope.clone(), globalScope, this, creator);
  }

  @Override
  public @NotNull IScopes forkedCurrentScope(final @NotNull String creator) {
    return new Scopes(scope.clone(), isolationScope, globalScope, this, creator);
  }

  @Override
  public @NotNull IScopes forkedRootScopes(final @NotNull String creator) {
    return IchnoisCore.forkedRootScopes(creator);
  }

  @Override
  public boolean isEnabled() {
    return getClient().isEnabled();
  }

  @Override
  public @NotNull IchnoisCoreId captureEvent(@NotNull IchnoisCoreEvent event, @Nullable Hint hint) {
    return captureEventInternal(event, hint, null);
  }

  @Override
  public @NotNull IchnoisCoreId captureEvent(
      @NotNull IchnoisCoreEvent event, @Nullable Hint hint, @NotNull ScopeCallback callback) {
    return captureEventInternal(event, hint, callback);
  }

  private @NotNull IchnoisCoreId captureEventInternal(
      final @NotNull IchnoisCoreEvent event,
      final @Nullable Hint hint,
      final @Nullable ScopeCallback scopeCallback) {
    IchnoisCoreId ichnoisId = IchnoisCoreId.EMPTY_ID;
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING, "Instance is disabled and this 'captureEvent' call is a no-op.");
      HintUtils.setCaptureFailed(hint);
    } else if (event == null) {
      getOptions().getLogger().log(IchnoisCoreLevel.WARNING, "captureEvent called with null parameter.");
      HintUtils.setCaptureFailed(hint);
    } else {
      try {
        assignTraceContext(event);
        final IScope localScope = buildLocalScope(getCombinedScopeView(), scopeCallback);

        ichnoisId = getClient().captureEvent(event, localScope, hint);
        updateLastEventId(ichnoisId);
      } catch (Throwable e) {
        getOptions()
            .getLogger()
            .log(
                IchnoisCoreLevel.ERROR, "Error while capturing event with id: " + event.getEventId(), e);
        HintUtils.setCaptureFailed(hint);
      }
    }
    return ichnoisId;
  }

  @ApiStatus.Internal
  @NotNull
  public IIchnoisCoreClient getClient() {
    return getCombinedScopeView().getClient();
  }

  private void assignTraceContext(final @NotNull IchnoisCoreEvent event) {
    getCombinedScopeView().assignTraceContext(event);
  }

  private @NotNull IScope buildLocalScope(
      final @NotNull IScope parentScope, final @Nullable ScopeCallback callback) {
    if (callback != null) {
      try {
        final IScope localScope = parentScope.clone();
        callback.run(localScope);
        return localScope;
      } catch (Throwable t) {
        getOptions()
            .getLogger()
            .log(IchnoisCoreLevel.ERROR, "Error in the 'ScopeCallback' callback.", t);
      }
    }
    return parentScope;
  }

  @Override
  public @NotNull IchnoisCoreId captureMessage(
      final @NotNull String message, final @NotNull IchnoisCoreLevel level) {
    return captureMessageInternal(message, level, null);
  }

  @Override
  public @NotNull IchnoisCoreId captureMessage(
      final @NotNull String message,
      final @NotNull IchnoisCoreLevel level,
      final @NotNull ScopeCallback callback) {
    return captureMessageInternal(message, level, callback);
  }

  private @NotNull IchnoisCoreId captureMessageInternal(
      final @NotNull String message,
      final @NotNull IchnoisCoreLevel level,
      final @Nullable ScopeCallback scopeCallback) {
    IchnoisCoreId ichnoisId = IchnoisCoreId.EMPTY_ID;
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Instance is disabled and this 'captureMessage' call is a no-op.");
    } else if (message == null) {
      getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "captureMessage called with null parameter.");
    } else {
      try {
        final IScope localScope = buildLocalScope(getCombinedScopeView(), scopeCallback);

        ichnoisId = getClient().captureMessage(message, level, localScope);
      } catch (Throwable e) {
        getOptions()
            .getLogger()
            .log(IchnoisCoreLevel.ERROR, "Error while capturing message: " + message, e);
      }
    }
    updateLastEventId(ichnoisId);
    return ichnoisId;
  }

  @Override
  public @NotNull IchnoisCoreId captureFeedback(
      final @NotNull Feedback feedback,
      final @Nullable Hint hint,
      final @Nullable ScopeCallback scopeCallback) {
    IchnoisCoreId ichnoisId = IchnoisCoreId.EMPTY_ID;
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Instance is disabled and this 'captureFeedback' call is a no-op.");
    } else if (feedback.getMessage().isEmpty()) {
      getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "captureFeedback called with empty message.");
    } else {
      try {
        final @NotNull IScope localScope = buildLocalScope(getCombinedScopeView(), scopeCallback);

        ichnoisId = getClient().captureFeedback(feedback, hint, localScope);
      } catch (Throwable e) {
        getOptions()
            .getLogger()
            .log(IchnoisCoreLevel.ERROR, "Error while capturing feedback: " + feedback.getMessage(), e);
      }
    }
    return ichnoisId;
  }

  @ApiStatus.Internal
  @Override
  public @NotNull IchnoisCoreId captureEnvelope(
      final @NotNull IchnoisCoreEnvelope envelope, final @Nullable Hint hint) {
    Objects.requireNonNull(envelope, "IchnoisCoreEnvelope is required.");

    IchnoisCoreId ichnoisId = IchnoisCoreId.EMPTY_ID;
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Instance is disabled and this 'captureEnvelope' call is a no-op.");
    } else {
      try {
        final IchnoisCoreId capturedEnvelopeId = getClient().captureEnvelope(envelope, hint);
        if (capturedEnvelopeId != null) {
          ichnoisId = capturedEnvelopeId;
        }
      } catch (Throwable e) {
        getOptions().getLogger().log(IchnoisCoreLevel.ERROR, "Error while capturing envelope.", e);
      }
    }
    return ichnoisId;
  }

  @Override
  public @NotNull IchnoisCoreId captureException(
      final @NotNull Throwable throwable, final @Nullable Hint hint) {
    return captureExceptionInternal(throwable, hint, null);
  }

  @Override
  public @NotNull IchnoisCoreId captureException(
      final @NotNull Throwable throwable,
      final @Nullable Hint hint,
      final @NotNull ScopeCallback callback) {

    return captureExceptionInternal(throwable, hint, callback);
  }

  private @NotNull IchnoisCoreId captureExceptionInternal(
      final @NotNull Throwable throwable,
      final @Nullable Hint hint,
      final @Nullable ScopeCallback scopeCallback) {
    IchnoisCoreId ichnoisId = IchnoisCoreId.EMPTY_ID;
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Instance is disabled and this 'captureException' call is a no-op.");
    } else if (throwable == null) {
      getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "captureException called with null parameter.");
    } else {
      try {
        final IchnoisCoreEvent event = new IchnoisCoreEvent(throwable);
        assignTraceContext(event);

        final IScope localScope = buildLocalScope(getCombinedScopeView(), scopeCallback);

        ichnoisId = getClient().captureEvent(event, localScope, hint);
      } catch (Throwable e) {
        getOptions()
            .getLogger()
            .log(
                IchnoisCoreLevel.ERROR, "Error while capturing exception: " + throwable.getMessage(), e);
      }
    }
    updateLastEventId(ichnoisId);
    return ichnoisId;
  }

  @Deprecated
  @Override
  public void captureUserFeedback(final @NotNull UserFeedback userFeedback) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Instance is disabled and this 'captureUserFeedback' call is a no-op.");
    } else {
      try {
        getClient().captureUserFeedback(userFeedback);
      } catch (Throwable e) {
        getOptions()
            .getLogger()
            .log(
                IchnoisCoreLevel.ERROR,
                "Error while capturing captureUserFeedback: " + userFeedback.toString(),
                e);
      }
    }
  }

  @Override
  public void startSession() {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING, "Instance is disabled and this 'startSession' call is a no-op.");
    } else {
      final Scope.SessionPair pair = getCombinedScopeView().startSession();
      if (pair != null) {
        // TODO: add helper overload `captureSessions` to pass a list of sessions and submit a
        // single envelope
        // Or create the envelope here with both items and call `captureEnvelope`
        if (pair.getPrevious() != null) {
          final Hint hint = HintUtils.createWithTypeCheckHint(new SessionEndHint());

          getClient().captureSession(pair.getPrevious(), hint);
        }

        final Hint hint = HintUtils.createWithTypeCheckHint(new SessionStartHint());

        getClient().captureSession(pair.getCurrent(), hint);
      } else {
        getOptions().getLogger().log(IchnoisCoreLevel.WARNING, "Session could not be started.");
      }
    }
  }

  @Override
  public void endSession() {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "Instance is disabled and this 'endSession' call is a no-op.");
    } else {
      final Session previousSession = getCombinedScopeView().endSession();
      if (previousSession != null) {
        final Hint hint = HintUtils.createWithTypeCheckHint(new SessionEndHint());

        getClient().captureSession(previousSession, hint);
      }
    }
  }

  @ApiStatus.Internal
  @NotNull
  public IScope getCombinedScopeView() {
    return combinedScope;
  }

  @Override
  public void close() {
    close(false);
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public void close(final boolean isRestarting) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "Instance is disabled and this 'close' call is a no-op.");
    } else {
      try {
        for (Integration integration : getOptions().getIntegrations()) {
          if (integration instanceof Closeable) {
            try {
              ((Closeable) integration).close();
            } catch (Throwable e) {
              getOptions()
                  .getLogger()
                  .log(IchnoisCoreLevel.WARNING, "Failed to close the integration {}.", integration, e);
            }
          }
        }
        for (EventProcessor eventProcessor : getOptions().getEventProcessors()) {
          if (eventProcessor instanceof Closeable) {
            try {
              ((Closeable) eventProcessor).close();
            } catch (Throwable e) {
              getOptions()
                  .getLogger()
                  .log(
                      IchnoisCoreLevel.WARNING,
                      "Failed to close the event processor {}.",
                      eventProcessor,
                      e);
            }
          }
        }

        configureScope(scope -> scope.clear());
        configureScope(ScopeType.ISOLATION, scope -> scope.clear());
        getOptions().getBackpressureMonitor().close();
        getOptions().getTransactionProfiler().close();
        getOptions().getContinuousProfiler().close(true);
        getOptions().getCompositePerformanceCollector().close();
        getOptions().getConnectionStatusProvider().close();
        // On restart we intentionally leave the timer executor running so that pending idle/
        // deadline timeouts of transactions started before the restart still fire and finish
        // those transactions. It self-terminates once idle (allowCoreThreadTimeOut).
        if (!isRestarting) {
          getOptions().getTimerExecutorService().close(getOptions().getShutdownTimeoutMillis());
        }
        final @NotNull IIchnoisCoreExecutorService executorService = getOptions().getExecutorService();
        if (isRestarting) {
          try {
            executorService.submit(
                () -> executorService.close(getOptions().getShutdownTimeoutMillis()));
          } catch (RejectedExecutionException e) {
            getOptions()
                .getLogger()
                .log(
                    IchnoisCoreLevel.WARNING,
                    "Failed to submit executor service shutdown task during restart. Shutting down synchronously.",
                    e);
            executorService.close(getOptions().getShutdownTimeoutMillis());
          }
        } else {
          executorService.close(getOptions().getShutdownTimeoutMillis());
        }

        // TODO: should we end session before closing client?
        configureScope(ScopeType.CURRENT, scope -> scope.getClient().close(isRestarting));
        configureScope(ScopeType.ISOLATION, scope -> scope.getClient().close(isRestarting));
        configureScope(ScopeType.GLOBAL, scope -> scope.getClient().close(isRestarting));
      } catch (Throwable e) {
        getOptions().getLogger().log(IchnoisCoreLevel.ERROR, "Error while closing the Scopes.", e);
      }
    }
  }

  @Override
  public void addBreadcrumb(final @NotNull Breadcrumb breadcrumb, final @Nullable Hint hint) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Instance is disabled and this 'addBreadcrumb' call is a no-op.");
    } else if (breadcrumb == null) {
      getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "addBreadcrumb called with null parameter.");
    } else {
      getCombinedScopeView().addBreadcrumb(breadcrumb, hint);
    }
  }

  @Override
  public void addBreadcrumb(final @NotNull Breadcrumb breadcrumb) {
    addBreadcrumb(breadcrumb, new Hint());
  }

  @Override
  public void setLevel(final @Nullable IchnoisCoreLevel level) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "Instance is disabled and this 'setLevel' call is a no-op.");
    } else {
      getCombinedScopeView().setLevel(level);
    }
  }

  @Override
  public void setTransaction(final @Nullable String transaction) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Instance is disabled and this 'setTransaction' call is a no-op.");
    } else if (transaction != null) {
      getCombinedScopeView().setTransaction(transaction);
    } else {
      getOptions().getLogger().log(IchnoisCoreLevel.WARNING, "Transaction cannot be null");
    }
  }

  @Override
  public void setUser(final @Nullable User user) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "Instance is disabled and this 'setUser' call is a no-op.");
    } else {
      getCombinedScopeView().setUser(user);
    }
  }

  @Override
  public void setFingerprint(final @NotNull List<String> fingerprint) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Instance is disabled and this 'setFingerprint' call is a no-op.");
    } else if (fingerprint == null) {
      getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "setFingerprint called with null parameter.");
    } else {
      getCombinedScopeView().setFingerprint(fingerprint);
    }
  }

  @Override
  public void clearBreadcrumbs() {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Instance is disabled and this 'clearBreadcrumbs' call is a no-op.");
    } else {
      getCombinedScopeView().clearBreadcrumbs();
    }
  }

  @Override
  public void setTag(final @Nullable String key, final @Nullable String value) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "Instance is disabled and this 'setTag' call is a no-op.");
    } else if (key == null || value == null) {
      getOptions().getLogger().log(IchnoisCoreLevel.WARNING, "setTag called with null parameter.");
    } else {
      getCombinedScopeView().setTag(key, value);
    }
  }

  @Override
  public void removeTag(final @Nullable String key) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "Instance is disabled and this 'removeTag' call is a no-op.");
    } else if (key == null) {
      getOptions().getLogger().log(IchnoisCoreLevel.WARNING, "removeTag called with null parameter.");
    } else {
      getCombinedScopeView().removeTag(key);
    }
  }

  @Override
  public void setExtra(final @Nullable String key, final @Nullable String value) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "Instance is disabled and this 'setExtra' call is a no-op.");
    } else if (key == null || value == null) {
      getOptions().getLogger().log(IchnoisCoreLevel.WARNING, "setExtra called with null parameter.");
    } else {
      getCombinedScopeView().setExtra(key, value);
    }
  }

  @Override
  public void removeExtra(final @Nullable String key) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "Instance is disabled and this 'removeExtra' call is a no-op.");
    } else if (key == null) {
      getOptions().getLogger().log(IchnoisCoreLevel.WARNING, "removeExtra called with null parameter.");
    } else {
      getCombinedScopeView().removeExtra(key);
    }
  }

  private void updateLastEventId(final @NotNull IchnoisCoreId lastEventId) {
    getCombinedScopeView().setLastEventId(lastEventId);
  }

  @Override
  public @NotNull IchnoisCoreId getLastEventId() {
    return getCombinedScopeView().getLastEventId();
  }

  @Override
  public IIchnoisCoreLifecycleToken pushScope() {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "Instance is disabled and this 'pushScope' call is a no-op.");
      return NoOpScopesLifecycleToken.getInstance();
    } else {
      final @NotNull IScopes scopes = this.forkedCurrentScope("pushScope");
      return scopes.makeCurrent();
    }
  }

  @Override
  public IIchnoisCoreLifecycleToken pushIsolationScope() {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Instance is disabled and this 'pushIsolationScope' call is a no-op.");
      return NoOpScopesLifecycleToken.getInstance();
    } else {
      final @NotNull IScopes scopes = this.forkedScopes("pushIsolationScope");
      return scopes.makeCurrent();
    }
  }

  @Override
  public @NotNull IIchnoisCoreLifecycleToken makeCurrent() {
    return IchnoisCore.setCurrentScopes(this);
  }

  /**
   * @deprecated please call {@link IIchnoisCoreLifecycleToken#close()} on the token returned by {@link
   *     IScopes#pushScope()} or {@link IScopes#pushIsolationScope()} instead.
   */
  @Override
  @Deprecated
  public void popScope() {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "Instance is disabled and this 'popScope' call is a no-op.");
    } else {
      final @Nullable Scopes parent = parentScopes;
      if (parent != null) {
        parent.makeCurrent();
      }
    }
  }

  @Override
  public void withScope(final @NotNull ScopeCallback callback) {
    if (!isEnabled()) {
      try {
        callback.run(NoOpScope.getInstance());
      } catch (Throwable e) {
        getOptions().getLogger().log(IchnoisCoreLevel.ERROR, "Error in the 'withScope' callback.", e);
      }

    } else {
      final @NotNull IScopes forkedScopes = forkedCurrentScope("withScope");
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = forkedScopes.makeCurrent()) {
        callback.run(forkedScopes.getScope());
      } catch (Throwable e) {
        getOptions().getLogger().log(IchnoisCoreLevel.ERROR, "Error in the 'withScope' callback.", e);
      }
    }
  }

  @Override
  public void withIsolationScope(final @NotNull ScopeCallback callback) {
    if (!isEnabled()) {
      try {
        callback.run(NoOpScope.getInstance());
      } catch (Throwable e) {
        getOptions()
            .getLogger()
            .log(IchnoisCoreLevel.ERROR, "Error in the 'withIsolationScope' callback.", e);
      }

    } else {
      final @NotNull IScopes forkedScopes = forkedScopes("withIsolationScope");
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = forkedScopes.makeCurrent()) {
        callback.run(forkedScopes.getIsolationScope());
      } catch (Throwable e) {
        getOptions()
            .getLogger()
            .log(IchnoisCoreLevel.ERROR, "Error in the 'withIsolationScope' callback.", e);
      }
    }
  }

  @Override
  public void configureScope(
      final @Nullable ScopeType scopeType, final @NotNull ScopeCallback callback) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Instance is disabled and this 'configureScope' call is a no-op.");
    } else {
      try {
        callback.run(combinedScope.getSpecificScope(scopeType));
      } catch (Throwable e) {
        getOptions()
            .getLogger()
            .log(IchnoisCoreLevel.ERROR, "Error in the 'configureScope' callback.", e);
      }
    }
  }

  @Override
  public void bindClient(final @NotNull IIchnoisCoreClient client) {
    if (client != null) {
      getOptions().getLogger().log(IchnoisCoreLevel.DEBUG, "New client bound to scope.");
      getCombinedScopeView().bindClient(client);
    } else {
      getOptions().getLogger().log(IchnoisCoreLevel.DEBUG, "NoOp client bound to scope.");
      getCombinedScopeView().bindClient(NoOpIchnoisCoreClient.getInstance());
    }
  }

  @Override
  public boolean isHealthy() {
    return getClient().isHealthy();
  }

  @Override
  public void flush(long timeoutMillis) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "Instance is disabled and this 'flush' call is a no-op.");
    } else {
      try {
        getClient().flush(timeoutMillis);
      } catch (Throwable e) {
        getOptions().getLogger().log(IchnoisCoreLevel.ERROR, "Error in the 'client.flush'.", e);
      }
    }
  }

  /**
   * @deprecated please use {@link IScopes#forkedScopes(String)} or {@link
   *     IScopes#forkedCurrentScope(String)} instead.
   */
  @Override
  @Deprecated
  @SuppressWarnings("deprecation")
  public @NotNull IHub clone() {
    if (!isEnabled()) {
      getOptions().getLogger().log(IchnoisCoreLevel.WARNING, "Disabled Scopes cloned.");
    }
    return new HubScopesWrapper(forkedScopes("scopes clone"));
  }

  @ApiStatus.Internal
  @Override
  public @NotNull IchnoisCoreId captureTransaction(
      final @NotNull IchnoisCoreTransaction transaction,
      final @Nullable TraceContext traceContext,
      final @Nullable Hint hint,
      final @Nullable ProfilingTraceData profilingTraceData) {
    Objects.requireNonNull(transaction, "transaction is required");

    IchnoisCoreId ichnoisId = IchnoisCoreId.EMPTY_ID;
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Instance is disabled and this 'captureTransaction' call is a no-op.");
    } else {
      if (!transaction.isFinished()) {
        getOptions()
            .getLogger()
            .log(
                IchnoisCoreLevel.WARNING,
                "Transaction: %s is not finished and this 'captureTransaction' call is a no-op.",
                transaction.getEventId());
      } else {
        if (!Boolean.TRUE.equals(transaction.isSampled())) {
          getOptions()
              .getLogger()
              .log(
                  IchnoisCoreLevel.DEBUG,
                  "Transaction %s was dropped due to sampling decision.",
                  transaction.getEventId());
          if (getOptions().getBackpressureMonitor().getDownsampleFactor() > 0) {
            getOptions()
                .getClientReportRecorder()
                .recordLostEvent(DiscardReason.BACKPRESSURE, DataCategory.Transaction);
            getOptions()
                .getClientReportRecorder()
                .recordLostEvent(
                    DiscardReason.BACKPRESSURE,
                    DataCategory.Span,
                    transaction.getSpans().size() + 1);
          } else {
            getOptions()
                .getClientReportRecorder()
                .recordLostEvent(DiscardReason.SAMPLE_RATE, DataCategory.Transaction);
            getOptions()
                .getClientReportRecorder()
                .recordLostEvent(
                    DiscardReason.SAMPLE_RATE,
                    DataCategory.Span,
                    transaction.getSpans().size() + 1);
          }
        } else {
          try {
            ichnoisId =
                getClient()
                    .captureTransaction(
                        transaction,
                        traceContext,
                        getCombinedScopeView(),
                        hint,
                        profilingTraceData);
          } catch (Throwable e) {
            getOptions()
                .getLogger()
                .log(
                    IchnoisCoreLevel.ERROR,
                    "Error while capturing transaction with id: " + transaction.getEventId(),
                    e);
          }
        }
      }
    }
    return ichnoisId;
  }

  @ApiStatus.Internal
  @Override
  public @NotNull IchnoisCoreId captureProfileChunk(
      final @NotNull ProfileChunk profilingContinuousData) {
    Objects.requireNonNull(profilingContinuousData, "profilingContinuousData is required");

    @NotNull IchnoisCoreId ichnoisId = IchnoisCoreId.EMPTY_ID;
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Instance is disabled and this 'captureTransaction' call is a no-op.");
    } else {
      try {
        ichnoisId = getClient().captureProfileChunk(profilingContinuousData, getScope());
      } catch (Throwable e) {
        getOptions()
            .getLogger()
            .log(
                IchnoisCoreLevel.ERROR,
                "Error while capturing profile chunk with id: "
                    + profilingContinuousData.getChunkId(),
                e);
      }
    }
    return ichnoisId;
  }

  @Override
  public @NotNull ITransaction startTransaction(
      final @NotNull TransactionContext transactionContext,
      final @NotNull TransactionOptions transactionOptions) {
    return createTransaction(transactionContext, transactionOptions);
  }

  private @NotNull ITransaction createTransaction(
      final @NotNull TransactionContext transactionContext,
      final @NotNull TransactionOptions transactionOptions) {
    Objects.requireNonNull(transactionContext, "transactionContext is required");
    transactionContext.setOrigin(transactionOptions.getOrigin());

    ITransaction transaction;
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Instance is disabled and this 'startTransaction' returns a no-op.");
      transaction = NoOpTransaction.getInstance();
    } else if (SpanUtils.isIgnored(
        getOptions().getIgnoredSpanOrigins(), transactionContext.getOrigin())) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Returning no-op for span origin %s as the SDK has been configured to ignore it",
              transactionContext.getOrigin());
      transaction = NoOpTransaction.getInstance();

    } else if (!getOptions().getInstrumenter().equals(transactionContext.getInstrumenter())) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Returning no-op for instrumenter %s as the SDK has been configured to use instrumenter %s",
              transactionContext.getInstrumenter(),
              getOptions().getInstrumenter());
      transaction = NoOpTransaction.getInstance();
    } else if (!getOptions().isTracingEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.INFO, "Tracing is disabled and this 'startTransaction' returns a no-op.");
      transaction = NoOpTransaction.getInstance();
    } else {
      final Double sampleRand = getSampleRand(transactionContext);
      final SamplingContext samplingContext =
          new SamplingContext(
              transactionContext, transactionOptions.getCustomSamplingContext(), sampleRand, null);
      final @NotNull TracesSampler tracesSampler = getOptions().getInternalTracesSampler();
      @NotNull TracesSamplingDecision samplingDecision = tracesSampler.sample(samplingContext);
      transactionContext.setSamplingDecision(samplingDecision);

      final @Nullable ISpanFactory maybeSpanFactory = transactionOptions.getSpanFactory();
      final @NotNull ISpanFactory spanFactory =
          maybeSpanFactory == null ? getOptions().getSpanFactory() : maybeSpanFactory;

      // If continuous profiling is enabled in trace mode, let's start it unless skipProfiling is
      // true in TransactionOptions.
      // Profiler will sample on its own.
      // Profiler is started before the transaction is created, so that the profiler id is available
      // when the transaction starts
      if (samplingDecision.getSampled()
          && getOptions().isContinuousProfilingEnabled()
          && getOptions().getProfileLifecycle() == ProfileLifecycle.TRACE
          && transactionContext.getProfilerId().equals(IchnoisCoreId.EMPTY_ID)) {
        getOptions()
            .getContinuousProfiler()
            .startProfiler(ProfileLifecycle.TRACE, getOptions().getInternalTracesSampler());
      }

      transaction =
          spanFactory.createTransaction(
              transactionContext, this, transactionOptions, compositePerformanceCollector);
      //          new IchnoisCoreTracer(
      //              transactionContext, this, transactionOptions,
      // compositePerformanceCollector);

      // The listener is called only if the transaction exists, as the transaction is needed to
      // stop it
      if (samplingDecision.getSampled()) {
        // If transaction profiler is sampled, let's start it
        if (samplingDecision.getProfileSampled()) {
          final ITransactionProfiler transactionProfiler = getOptions().getTransactionProfiler();
          // If the profiler is not running, we start and bind it here.
          if (!transactionProfiler.isRunning()) {
            transactionProfiler.start();
            transactionProfiler.bindTransaction(transaction);
          } else if (transactionOptions.isAppStartTransaction()) {
            // If the profiler is running and the current transaction is the app start, we bind it.
            transactionProfiler.bindTransaction(transaction);
          }
        }
      }
    }
    if (transactionOptions.isBindToScope()) {
      transaction.makeCurrent();
    }
    return transaction;
  }

  private @NotNull Double getSampleRand(final @NotNull TransactionContext transactionContext) {
    final @Nullable Baggage baggage = transactionContext.getBaggage();
    if (baggage != null) {
      final @Nullable Double sampleRandFromBaggageMaybe = baggage.getSampleRand();
      if (sampleRandFromBaggageMaybe != null) {
        return sampleRandFromBaggageMaybe;
      }
    }

    return getCombinedScopeView().getPropagationContext().getSampleRand();
  }

  @Override
  public void startProfiler() {
    if (getOptions().isContinuousProfilingEnabled()) {
      if (getOptions().getProfileLifecycle() != ProfileLifecycle.MANUAL) {
        getOptions()
            .getLogger()
            .log(
                IchnoisCoreLevel.WARNING,
                "Profiling lifecycle is %s. Profiling cannot be started manually.",
                getOptions().getProfileLifecycle().name());
        return;
      }
      getOptions()
          .getContinuousProfiler()
          .startProfiler(ProfileLifecycle.MANUAL, getOptions().getInternalTracesSampler());
    } else if (getOptions().isProfilingEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Continuous Profiling is not enabled. Set profilesSampleRate and profilesSampler to null to enable it.");
    }
  }

  @Override
  public void stopProfiler() {
    if (getOptions().isContinuousProfilingEnabled()) {
      if (getOptions().getProfileLifecycle() != ProfileLifecycle.MANUAL) {
        getOptions()
            .getLogger()
            .log(
                IchnoisCoreLevel.WARNING,
                "Profiling lifecycle is %s. Profiling cannot be stopped manually.",
                getOptions().getProfileLifecycle().name());
        return;
      }
      getOptions().getLogger().log(IchnoisCoreLevel.DEBUG, "Stopped continuous Profiling.");
      getOptions().getContinuousProfiler().stopProfiler(ProfileLifecycle.MANUAL);
    } else {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Continuous Profiling is not enabled. Set profilesSampleRate and profilesSampler to null to enable it.");
    }
  }

  @Override
  @ApiStatus.Internal
  public void setSpanContext(
      final @NotNull Throwable throwable,
      final @NotNull ISpan span,
      final @NotNull String transactionName) {
    getCombinedScopeView().setSpanContext(throwable, span, transactionName);
  }

  @Override
  public @Nullable ISpan getSpan() {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "Instance is disabled and this 'getSpan' call is a no-op.");
    } else {
      return getCombinedScopeView().getSpan();
    }
    return null;
  }

  @Override
  public void setActiveSpan(final @Nullable ISpan span) {
    getCombinedScopeView().setActiveSpan(span);
  }

  @Override
  @ApiStatus.Internal
  public @Nullable ITransaction getTransaction() {
    ITransaction span = null;
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Instance is disabled and this 'getTransaction' call is a no-op.");
    } else {
      span = getCombinedScopeView().getTransaction();
    }
    return span;
  }

  @Override
  public @NotNull IchnoisCoreOptions getOptions() {
    return combinedScope.getOptions();
  }

  @Override
  public @Nullable Boolean isCrashedLastRun() {
    return IchnoisCoreCrashLastRunState.getInstance()
        .isCrashedLastRun(
            getOptions().getCacheDirPath(), !getOptions().isEnableAutoSessionTracking());
  }

  @Override
  public void reportFullyDisplayed() {
    if (getOptions().isEnableTimeToFullDisplayTracing()) {
      getOptions().getFullyDisplayedReporter().reportFullyDrawn();
    }
  }

  @Override
  public @Nullable TransactionContext continueTrace(
      final @Nullable String ichnoisTrace, final @Nullable List<String> baggageHeaders) {
    @NotNull
    PropagationContext propagationContext =
        PropagationContext.fromHeaders(
            getOptions().getLogger(), ichnoisTrace, baggageHeaders, getOptions());
    configureScope(
        (scope) -> {
          scope.withPropagationContext(
              oldPropagationContext -> {
                scope.setPropagationContext(propagationContext);
              });
        });
    if (getOptions().isTracingEnabled()) {
      return TransactionContext.fromPropagationContext(propagationContext);
    } else {
      return null;
    }
  }

  @Override
  public @Nullable IchnoisCoreTraceHeader getTraceparent() {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Instance is disabled and this 'getTraceparent' call is a no-op.");
    } else {
      final @Nullable TracingUtils.TracingHeaders headers =
          TracingUtils.trace(this, null, getSpan());
      if (headers != null) {
        return headers.getIchnoisCoreTraceHeader();
      }
    }

    return null;
  }

  @Override
  public @Nullable BaggageHeader getBaggage() {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "Instance is disabled and this 'getBaggage' call is a no-op.");
    } else {
      final @Nullable TracingUtils.TracingHeaders headers =
          TracingUtils.trace(this, null, getSpan());
      if (headers != null) {
        return headers.getBaggageHeader();
      }
    }

    return null;
  }

  @Override
  public @NotNull IchnoisCoreId captureCheckIn(final @NotNull CheckIn checkIn) {
    IchnoisCoreId ichnoisId = IchnoisCoreId.EMPTY_ID;
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Instance is disabled and this 'captureCheckIn' call is a no-op.");
    } else {
      try {
        ichnoisId = getClient().captureCheckIn(checkIn, getCombinedScopeView(), null);
      } catch (Throwable e) {
        getOptions()
            .getLogger()
            .log(IchnoisCoreLevel.ERROR, "Error while capturing check-in for slug", e);
      }
    }
    updateLastEventId(ichnoisId);
    return ichnoisId;
  }

  @Override
  public @NotNull IchnoisCoreId captureReplay(
      final @NotNull IchnoisCoreReplayEvent replay, final @Nullable Hint hint) {
    IchnoisCoreId ichnoisId = IchnoisCoreId.EMPTY_ID;
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Instance is disabled and this 'captureReplay' call is a no-op.");
    } else {
      try {
        ichnoisId = getClient().captureReplayEvent(replay, getCombinedScopeView(), hint);
      } catch (Throwable e) {
        getOptions().getLogger().log(IchnoisCoreLevel.ERROR, "Error while capturing replay", e);
      }
    }
    return ichnoisId;
  }

  @ApiStatus.Internal
  @Override
  public @Nullable RateLimiter getRateLimiter() {
    return getClient().getRateLimiter();
  }

  @Override
  public @NotNull ILoggerApi logger() {
    return logger;
  }

  @Override
  public @NotNull IMetricsApi metrics() {
    return metrics;
  }

  @Override
  public @NotNull IFeedbackApi feedback() {
    return feedbackApi;
  }

  @Override
  public void setAttribute(final @Nullable String key, final @Nullable Object value) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING, "Instance is disabled and this 'setAttribute' call is a no-op.");
    } else {
      getCombinedScopeView().setAttribute(key, value);
    }
  }

  @Override
  public void setAttribute(final @Nullable IchnoisCoreAttribute attribute) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING, "Instance is disabled and this 'setAttribute' call is a no-op.");
    } else {
      getCombinedScopeView().setAttribute(attribute);
    }
  }

  @Override
  public void setAttributes(final @Nullable IchnoisCoreAttributes attributes) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Instance is disabled and this 'setAttributes' call is a no-op.");
    } else {
      getCombinedScopeView().setAttributes(attributes);
    }
  }

  @Override
  public void removeAttribute(final @Nullable String key) {
    if (!isEnabled()) {
      getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "Instance is disabled and this 'removeAttribute' call is a no-op.");
    } else {
      getCombinedScopeView().removeAttribute(key);
    }
  }

  @Override
  public void addFeatureFlag(final @Nullable String flag, final @Nullable Boolean result) {
    combinedScope.addFeatureFlag(flag, result);
  }

  private static void validateOptions(final @NotNull IchnoisCoreOptions options) {
    Objects.requireNonNull(options, "IchnoisCoreOptions is required.");
    if (options.getDsn() == null || options.getDsn().isEmpty()) {
      throw new IllegalArgumentException(
          "Scopes requires a DSN to be instantiated. Considering using the NoOpScopes if no DSN is available.");
    }
  }
}
