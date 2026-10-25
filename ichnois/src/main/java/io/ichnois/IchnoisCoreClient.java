package io.ichnois;

import io.ichnois.clientreport.DiscardReason;
import io.ichnois.exception.IchnoisCoreEnvelopeException;
import io.ichnois.hints.AbnormalExit;
import io.ichnois.hints.ApplyScopeData;
import io.ichnois.hints.Backfillable;
import io.ichnois.hints.Cached;
import io.ichnois.hints.DiskFlushNotification;
import io.ichnois.hints.TransactionEnd;
import io.ichnois.internal.eventprocessor.IchnoisCoreEventProcessor;
import io.ichnois.logger.ILoggerBatchProcessor;
import io.ichnois.metrics.IMetricsBatchProcessor;
import io.ichnois.protocol.Contexts;
import io.ichnois.protocol.DebugMeta;
import io.ichnois.protocol.FeatureFlags;
import io.ichnois.protocol.Feedback;
import io.ichnois.protocol.IchnoisCoreId;
import io.ichnois.protocol.IchnoisCoreTransaction;
import io.ichnois.transport.ITransport;
import io.ichnois.transport.RateLimiter;
import io.ichnois.util.*;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class IchnoisCoreClient implements IIchnoisCoreClient {
  static final String ICHNOIS_PROTOCOL_VERSION = "7";

  private boolean enabled;

  private final @NotNull IchnoisCoreOptions options;
  private final @NotNull ITransport transport;
  private final @NotNull ILoggerBatchProcessor loggerBatchProcessor;
  private final @NotNull IMetricsBatchProcessor metricsBatchProcessor;

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  public IchnoisCoreClient(final @NotNull IchnoisCoreOptions options) {
    this.options = Objects.requireNonNull(options, "IchnoisCoreOptions is required.");
    this.enabled = true;

    ITransportFactory transportFactory = options.getTransportFactory();
    if (transportFactory instanceof NoOpTransportFactory) {
      transportFactory = new AsyncHttpTransportFactory();
      options.setTransportFactory(transportFactory);
    }

    final RequestDetailsResolver requestDetailsResolver = new RequestDetailsResolver(options);
    transport = transportFactory.create(options, requestDetailsResolver.resolve());
    loggerBatchProcessor = options.getLogs().getLoggerBatchProcessorFactory().create(options, this);
    metricsBatchProcessor =
        options.getMetrics().getMetricsBatchProcessorFactory().create(options, this);
  }

  private boolean shouldApplyScopeData(
      final @NotNull IchnoisCoreBaseEvent event, final @NotNull Hint hint) {
    if (HintUtils.shouldApplyScopeData(hint)) {
      return true;
    } else {
      options
          .getLogger()
          .log(IchnoisCoreLevel.DEBUG, "Event was cached so not applying scope: %s", event.getEventId());
      return false;
    }
  }

  private boolean shouldApplyScopeData(final @NotNull CheckIn event, final @NotNull Hint hint) {
    if (HintUtils.shouldApplyScopeData(hint)) {
      return true;
    } else {
      options
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Check-in was cached so not applying scope: %s",
              event.getCheckInId());
      return false;
    }
  }

  @Override
  public @NotNull IchnoisCoreId captureEvent(
      @NotNull IchnoisCoreEvent event, final @Nullable IScope scope, @Nullable Hint hint) {
    Objects.requireNonNull(event, "IchnoisCoreEvent is required.");

    // Drop silently to prevent recursion; a log here can re-enter through a logging integration.
    if (IchnoisCoreCallbackReentrancyGuard.isActive()) {
      return IchnoisCoreId.EMPTY_ID;
    }

    if (hint == null) {
      hint = new Hint();
    }

    // Cached envelopes (e.g. native crashes from the outbox) already carry their attachments as
    // envelope items. Re-applying scope attachments here would duplicate them.
    if (shouldApplyScopeData(event, hint) && !HintUtils.hasType(hint, Cached.class)) {
      addScopeAttachmentsToHint(scope, hint);
    }

    options.getLogger().log(IchnoisCoreLevel.DEBUG, "Capturing event: %s", event.getEventId());

    if (event != null) {
      final Throwable eventThrowable = event.getThrowable();
      if (eventThrowable != null
          && ExceptionUtils.isIgnored(options.getIgnoredExceptionsForType(), eventThrowable)) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.DEBUG,
                "Event was dropped as the exception %s is ignored",
                eventThrowable.getClass());
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.EVENT_PROCESSOR, DataCategory.Error);
        return IchnoisCoreId.EMPTY_ID;
      }

      if (ErrorUtils.isIgnored(options.getIgnoredErrors(), event)) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.DEBUG,
                "Event was dropped as it matched a string/pattern in ignoredErrors",
                event.getMessage());
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.EVENT_PROCESSOR, DataCategory.Error);
        return IchnoisCoreId.EMPTY_ID;
      }
    }

    if (shouldApplyScopeData(event, hint)) {
      // Event has already passed through here before it was cached
      // Going through again could be reading data that is no longer relevant
      // i.e proguard id, app version, threads
      event = applyScope(event, scope, hint);

      if (event == null) {
        options.getLogger().log(IchnoisCoreLevel.DEBUG, "Event was dropped by applyScope");
        return IchnoisCoreId.EMPTY_ID;
      }
    }

    event = processEvent(event, hint, options.getEventProcessors());

    if (event != null) {
      event = executeBeforeSend(event, hint);

      if (event == null) {
        options.getLogger().log(IchnoisCoreLevel.DEBUG, "Event was dropped by beforeSend");
      }
    }

    if (event != null) {
      event = EventSizeLimitingUtils.limitEventSize(event, hint, options);
    }

    if (event == null) {
      return IchnoisCoreId.EMPTY_ID;
    }

    @Nullable
    Session sessionBeforeUpdate =
        scope != null ? scope.withSession((@Nullable Session session) -> {}) : null;
    @Nullable Session session = null;

    if (event != null) {
      // https://develop.ichnois.dev/sdk/sessions/#terminal-session-states
      if (sessionBeforeUpdate == null || !sessionBeforeUpdate.isTerminated()) {
        session = updateSessionData(event, hint, scope);
      }

      if (!sample()) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.DEBUG,
                "Event %s was dropped due to sampling decision.",
                event.getEventId());
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.SAMPLE_RATE, DataCategory.Error);
        // setting event as null to not be sent as its been discarded by sample rate
        event = null;
      }
    }

    final boolean shouldSendSessionUpdate =
        shouldSendSessionUpdateForDroppedEvent(sessionBeforeUpdate, session);

    if (event == null && !shouldSendSessionUpdate) {
      options
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Not sending session update for dropped event as it did not cause the session health to change.");
      return IchnoisCoreId.EMPTY_ID;
    }

    IchnoisCoreId ichnoisId = IchnoisCoreId.EMPTY_ID;
    if (event != null && event.getEventId() != null) {
      ichnoisId = event.getEventId();
    }

    final boolean isBackfillable = HintUtils.hasType(hint, Backfillable.class);
    final boolean isCached =
        HintUtils.hasType(hint, Cached.class) && !HintUtils.hasType(hint, ApplyScopeData.class);
    // if event is backfillable or cached we don't wanna trigger capture replay, because it's
    // an event from the past. If it's cached, but with ApplyScopeData, it comes from the outbox
    // folder and we still want to capture replay (e.g. a native captureException error)
    if (event != null && !isBackfillable && !isCached && (event.isErrored() || event.isCrashed())) {
      boolean shouldCaptureReplay = true;
      final IchnoisCoreReplayOptions.BeforeErrorSamplingCallback beforeErrorSampling =
          options.getSessionReplay().getBeforeErrorSampling();
      if (beforeErrorSampling != null) {
        try (final @NotNull IIchnoisCoreLifecycleToken ignored = IchnoisCoreCallbackReentrancyGuard.enter()) {
          shouldCaptureReplay = beforeErrorSampling.execute(event, hint);
        } catch (Throwable e) {
          options
              .getLogger()
              .log(
                  IchnoisCoreLevel.ERROR,
                  "The beforeErrorSampling callback threw an exception. Skipping replay capture.",
                  e);
          if (!IchnoisCoreId.EMPTY_ID.equals(options.getReplayController().getReplayId())) {
            options
                .getClientReportRecorder()
                .recordLostEvent(DiscardReason.CALLBACK_ERROR, DataCategory.Replay);
          }
          shouldCaptureReplay = false;
        }
      }
      if (shouldCaptureReplay) {
        final @NotNull IchnoisCoreId scopeReplayId =
            scope != null ? scope.getReplayId() : IchnoisCoreId.EMPTY_ID;
        final @NotNull IchnoisCoreId capturedReplayId =
            options.getReplayController().captureReplay(event.isCrashed());
        final @NotNull IchnoisCoreId replayId =
            !capturedReplayId.equals(IchnoisCoreId.EMPTY_ID) ? capturedReplayId : scopeReplayId;
        if (scope != null && !replayId.equals(IchnoisCoreId.EMPTY_ID)) {
          final @Nullable ITransaction transaction = scope.getTransaction();
          if (transaction != null) {
            final @Nullable Baggage baggage = transaction.getSpanContext().getBaggage();
            if (baggage != null) {
              baggage.forceSetReplayId(replayId);
            }
          }
        }
      }
    }

    try {
      final @Nullable TraceContext traceContext = getTraceContext(scope, hint, event);
      final boolean shouldSendAttachments = event != null;
      List<Attachment> attachments = shouldSendAttachments ? getAttachments(hint) : null;
      final @Nullable IchnoisCoreEnvelope envelope =
          buildEnvelope(event, attachments, session, traceContext, null);

      hint.clear();
      if (envelope != null) {
        ichnoisId = sendEnvelope(envelope, hint);
      }
    } catch (IOException | IchnoisCoreEnvelopeException e) {
      options.getLogger().log(IchnoisCoreLevel.WARNING, e, "Capturing event %s failed.", ichnoisId);

      // if there was an error capturing the event, we return an emptyId
      HintUtils.setCaptureFailed(hint);
      ichnoisId = IchnoisCoreId.EMPTY_ID;
    }

    // if we encountered a crash/abnormal exit finish tracing in order to persist and send
    // any running transaction / profiling data.
    if (scope != null) {
      finalizeTransaction(scope, hint);
    }

    return ichnoisId;
  }

  private void finalizeTransaction(final @NotNull IScope scope, final @NotNull Hint hint) {
    final @Nullable ITransaction transaction = scope.getTransaction();
    if (transaction != null) {
      if (HintUtils.hasType(hint, TransactionEnd.class)) {
        final Object ichnoisSdkHint = HintUtils.getIchnoisCoreSdkHint(hint);
        if (ichnoisSdkHint instanceof DiskFlushNotification) {
          ((DiskFlushNotification) ichnoisSdkHint).setFlushable(transaction.getEventId());
          transaction.forceFinish(SpanStatus.ABORTED, false, hint);
        } else {
          transaction.forceFinish(SpanStatus.ABORTED, false, null);
        }
      }
    }
  }

  @Override
  public @NotNull IchnoisCoreId captureReplayEvent(
      @NotNull IchnoisCoreReplayEvent event, final @Nullable IScope scope, @Nullable Hint hint) {
    Objects.requireNonNull(event, "SessionReplay is required.");

    // Drop silently to prevent recursion; a log here can re-enter through a logging integration.
    if (IchnoisCoreCallbackReentrancyGuard.isActive()) {
      return IchnoisCoreId.EMPTY_ID;
    }

    if (hint == null) {
      hint = new Hint();
    }

    if (shouldApplyScopeData(event, hint)) {
      applyScope(event, scope);
    }

    options.getLogger().log(IchnoisCoreLevel.DEBUG, "Capturing session replay: %s", event.getEventId());

    IchnoisCoreId ichnoisId = IchnoisCoreId.EMPTY_ID;
    if (event.getEventId() != null) {
      ichnoisId = event.getEventId();
    }

    event = processReplayEvent(event, hint, options.getEventProcessors());

    if (event != null) {
      event = executeBeforeSendReplay(event, hint);

      if (event == null) {
        options.getLogger().log(IchnoisCoreLevel.DEBUG, "Event was dropped by beforeSendReplay");
      }
    }

    if (event == null) {
      return IchnoisCoreId.EMPTY_ID;
    }

    try {
      final @Nullable TraceContext traceContext = getTraceContext(scope, hint, event, null);
      final boolean cleanupReplayFolder = HintUtils.hasType(hint, Backfillable.class);
      final IchnoisCoreEnvelope envelope =
          buildEnvelope(event, hint.getReplayRecording(), traceContext, cleanupReplayFolder);

      hint.clear();
      transport.send(envelope, hint);
    } catch (IOException e) {
      options.getLogger().log(IchnoisCoreLevel.WARNING, e, "Capturing event %s failed.", ichnoisId);

      // if there was an error capturing the event, we return an emptyId
      ichnoisId = IchnoisCoreId.EMPTY_ID;
    }

    return ichnoisId;
  }

  private void addScopeAttachmentsToHint(@Nullable IScope scope, @NotNull Hint hint) {
    if (scope != null) {
      hint.addAttachments(scope.getAttachments());
    }
  }

  private boolean shouldSendSessionUpdateForDroppedEvent(
      @Nullable Session sessionBeforeUpdate, @Nullable Session sessionAfterUpdate) {
    if (sessionAfterUpdate == null) {
      return false;
    }

    if (sessionBeforeUpdate == null) {
      return true;
    }

    final boolean didSessionMoveToCrashedState =
        sessionAfterUpdate.getStatus() == Session.State.Crashed
            && sessionBeforeUpdate.getStatus() != Session.State.Crashed;
    if (didSessionMoveToCrashedState) {
      return true;
    }

    final boolean didSessionMoveToErroredState =
        sessionAfterUpdate.errorCount() > 0 && sessionBeforeUpdate.errorCount() <= 0;
    if (didSessionMoveToErroredState) {
      return true;
    }

    return false;
  }

  private @Nullable List<Attachment> getAttachments(final @NotNull Hint hint) {
    @NotNull final List<Attachment> attachments = hint.getAttachments();

    @Nullable final Attachment screenshot = hint.getScreenshot();
    if (screenshot != null) {
      attachments.add(screenshot);
    }

    @Nullable final Attachment viewHierarchy = hint.getViewHierarchy();
    if (viewHierarchy != null) {
      attachments.add(viewHierarchy);
    }

    @Nullable final Attachment threadDump = hint.getThreadDump();
    if (threadDump != null) {
      attachments.add(threadDump);
    }

    @Nullable final Attachment tombstone = hint.getTombstone();
    if (tombstone != null) {
      attachments.add(tombstone);
    }

    return attachments;
  }

  private @Nullable IchnoisCoreEnvelope buildEnvelope(
      final @Nullable IchnoisCoreBaseEvent event,
      final @Nullable List<Attachment> attachments,
      final @Nullable Session session,
      final @Nullable TraceContext traceContext,
      final @Nullable ProfilingTraceData profilingTraceData)
      throws IOException, IchnoisCoreEnvelopeException {
    IchnoisCoreId ichnoisId = null;

    final List<IchnoisCoreEnvelopeItem> envelopeItems = new ArrayList<>();

    if (event != null) {
      final IchnoisCoreEnvelopeItem eventItem =
          IchnoisCoreEnvelopeItem.fromEvent(options.getSerializer(), event);
      envelopeItems.add(eventItem);
      ichnoisId = event.getEventId();
    }

    if (session != null) {
      final IchnoisCoreEnvelopeItem sessionItem =
          IchnoisCoreEnvelopeItem.fromSession(options.getSerializer(), session);
      envelopeItems.add(sessionItem);
    }

    if (profilingTraceData != null) {
      final IchnoisCoreEnvelopeItem profilingTraceItem =
          IchnoisCoreEnvelopeItem.fromProfilingTrace(
              profilingTraceData, options.getMaxTraceFileSize(), options.getSerializer());
      envelopeItems.add(profilingTraceItem);

      if (ichnoisId == null) {
        ichnoisId = new IchnoisCoreId(profilingTraceData.getProfileId());
      }
    }

    if (attachments != null) {
      for (final Attachment attachment : attachments) {
        final IchnoisCoreEnvelopeItem attachmentItem =
            IchnoisCoreEnvelopeItem.fromAttachment(
                options.getSerializer(),
                options.getLogger(),
                attachment,
                options.getMaxAttachmentSize());
        envelopeItems.add(attachmentItem);
      }
    }

    if (!envelopeItems.isEmpty()) {
      final IchnoisCoreEnvelopeHeader envelopeHeader =
          new IchnoisCoreEnvelopeHeader(ichnoisId, options.getSdkVersion(), traceContext);

      return new IchnoisCoreEnvelope(envelopeHeader, envelopeItems);
    }

    return null;
  }

  @Nullable
  private IchnoisCoreEvent processEvent(
      @NotNull IchnoisCoreEvent event,
      final @NotNull Hint hint,
      final @NotNull List<EventProcessor> eventProcessors) {
    for (final EventProcessor processor : eventProcessors) {
      try {
        // only wire backfillable events through the backfilling processors, skip from others, and
        // the other way around
        final boolean isBackfillingProcessor = processor instanceof BackfillingEventProcessor;
        final boolean isBackfillable = HintUtils.hasType(hint, Backfillable.class);
        if (isBackfillable && isBackfillingProcessor) {
          event = processor.process(event, hint);
        } else if (!isBackfillable && !isBackfillingProcessor) {
          event = processor.process(event, hint);
        }
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.ERROR,
                e,
                "An exception occurred while processing event by processor: %s",
                processor.getClass().getName());
        if (!(processor instanceof IchnoisCoreEventProcessor)) {
          options
              .getClientReportRecorder()
              .recordLostEvent(DiscardReason.CALLBACK_ERROR, DataCategory.Error);
          return null;
        }
      }

      if (event == null) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.DEBUG,
                "Event was dropped by a processor: %s",
                processor.getClass().getName());
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.EVENT_PROCESSOR, DataCategory.Error);
        break;
      }
    }
    return event;
  }

  private void recordLostLogEvent(
      final @NotNull DiscardReason reason, final @NotNull IchnoisCoreLogEvent event) {
    options.getClientReportRecorder().recordLostEvent(reason, DataCategory.LogItem);
    final long numberOfBytes =
        JsonSerializationUtils.byteSizeOf(options.getSerializer(), options.getLogger(), event);
    options.getClientReportRecorder().recordLostEvent(reason, DataCategory.LogByte, numberOfBytes);
  }

  private void recordLostMetricsEvent(
      final @NotNull DiscardReason reason, final @NotNull IchnoisCoreMetricsEvent event) {
    options.getClientReportRecorder().recordLostEvent(reason, DataCategory.TraceMetric);
    final long numberOfBytes =
        JsonSerializationUtils.byteSizeOf(options.getSerializer(), options.getLogger(), event);
    options
        .getClientReportRecorder()
        .recordLostEvent(reason, DataCategory.TraceMetricByte, numberOfBytes);
  }

  @Nullable
  private IchnoisCoreLogEvent processLogEvent(
      @NotNull IchnoisCoreLogEvent event, final @NotNull List<EventProcessor> eventProcessors) {
    for (final EventProcessor processor : eventProcessors) {
      final @NotNull IchnoisCoreLogEvent eventBeforeProcessor = event;
      try {
        event = processor.process(event);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.ERROR,
                e,
                "An exception occurred while processing log event by processor: %s",
                processor.getClass().getName());
        if (!(processor instanceof IchnoisCoreEventProcessor)) {
          recordLostLogEvent(DiscardReason.CALLBACK_ERROR, eventBeforeProcessor);
          return null;
        }
      }

      if (event == null) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.DEBUG,
                "Log event was dropped by a processor: %s",
                processor.getClass().getName());
        recordLostLogEvent(DiscardReason.EVENT_PROCESSOR, eventBeforeProcessor);
        break;
      }
    }
    return event;
  }

  @Nullable
  private IchnoisCoreMetricsEvent processMetricsEvent(
      @NotNull IchnoisCoreMetricsEvent event,
      final @NotNull List<EventProcessor> eventProcessors,
      final @NotNull Hint hint) {
    for (final EventProcessor processor : eventProcessors) {
      final @NotNull IchnoisCoreMetricsEvent eventBeforeProcessor = event;
      try {
        event = processor.process(event, hint);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.ERROR,
                e,
                "An exception occurred while processing metrics event by processor: %s",
                processor.getClass().getName());
        if (!(processor instanceof IchnoisCoreEventProcessor)) {
          recordLostMetricsEvent(DiscardReason.CALLBACK_ERROR, eventBeforeProcessor);
          return null;
        }
      }

      if (event == null) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.DEBUG,
                "Metrics event was dropped by a processor: %s",
                processor.getClass().getName());
        recordLostMetricsEvent(DiscardReason.EVENT_PROCESSOR, eventBeforeProcessor);
        break;
      }
    }
    return event;
  }

  private @Nullable IchnoisCoreTransaction processTransaction(
      @NotNull IchnoisCoreTransaction transaction,
      final @NotNull Hint hint,
      final @NotNull List<EventProcessor> eventProcessors,
      final boolean hasProfile) {
    for (final EventProcessor processor : eventProcessors) {
      final int spanCountBeforeProcessor = transaction.getSpans().size();
      try {
        transaction = processor.process(transaction, hint);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.ERROR,
                e,
                "An exception occurred while processing transaction by processor: %s",
                processor.getClass().getName());
        if (!(processor instanceof IchnoisCoreEventProcessor)) {
          options
              .getClientReportRecorder()
              .recordLostEvent(DiscardReason.CALLBACK_ERROR, DataCategory.Transaction);
          options
              .getClientReportRecorder()
              .recordLostEvent(
                  DiscardReason.CALLBACK_ERROR, DataCategory.Span, spanCountBeforeProcessor + 1);
          if (hasProfile) {
            options
                .getClientReportRecorder()
                .recordLostEvent(DiscardReason.CALLBACK_ERROR, DataCategory.Profile);
          }
          return null;
        }
      }
      final int spanCountAfterProcessor = transaction == null ? 0 : transaction.getSpans().size();

      if (transaction == null) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.DEBUG,
                "Transaction was dropped by a processor: %s",
                processor.getClass().getName());
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.EVENT_PROCESSOR, DataCategory.Transaction);
        // If we drop a transaction, we are also dropping all its spans (+1 for the root span)
        options
            .getClientReportRecorder()
            .recordLostEvent(
                DiscardReason.EVENT_PROCESSOR, DataCategory.Span, spanCountBeforeProcessor + 1);
        if (hasProfile) {
          options
              .getClientReportRecorder()
              .recordLostEvent(DiscardReason.EVENT_PROCESSOR, DataCategory.Profile);
        }
        break;
      } else if (spanCountAfterProcessor < spanCountBeforeProcessor) {
        // If the callback removed some spans, we report it
        final int droppedSpanCount = spanCountBeforeProcessor - spanCountAfterProcessor;
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.DEBUG,
                "%d spans were dropped by a processor: %s",
                droppedSpanCount,
                processor.getClass().getName());
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.EVENT_PROCESSOR, DataCategory.Span, droppedSpanCount);
      }
    }
    return transaction;
  }

  @Nullable
  private IchnoisCoreReplayEvent processReplayEvent(
      @NotNull IchnoisCoreReplayEvent replayEvent,
      final @NotNull Hint hint,
      final @NotNull List<EventProcessor> eventProcessors) {
    for (final EventProcessor processor : eventProcessors) {
      try {
        replayEvent = processor.process(replayEvent, hint);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.ERROR,
                e,
                "An exception occurred while processing replay event by processor: %s",
                processor.getClass().getName());
        if (!(processor instanceof IchnoisCoreEventProcessor)) {
          options
              .getClientReportRecorder()
              .recordLostEvent(DiscardReason.CALLBACK_ERROR, DataCategory.Replay);
          return null;
        }
      }

      if (replayEvent == null) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.DEBUG,
                "Replay event was dropped by a processor: %s",
                processor.getClass().getName());
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.EVENT_PROCESSOR, DataCategory.Replay);
        break;
      }
    }
    return replayEvent;
  }

  @Nullable
  private IchnoisCoreEvent processFeedbackEvent(
      @NotNull IchnoisCoreEvent feedbackEvent,
      final @NotNull Hint hint,
      final @NotNull List<EventProcessor> eventProcessors) {
    for (final EventProcessor processor : eventProcessors) {
      try {
        feedbackEvent = processor.process(feedbackEvent, hint);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.ERROR,
                e,
                "An exception occurred while processing feedback event by processor: %s",
                processor.getClass().getName());
        if (!(processor instanceof IchnoisCoreEventProcessor)) {
          options
              .getClientReportRecorder()
              .recordLostEvent(DiscardReason.CALLBACK_ERROR, DataCategory.Feedback);
          return null;
        }
      }

      if (feedbackEvent == null) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.DEBUG,
                "Feedback event was dropped by a processor: %s",
                processor.getClass().getName());
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.EVENT_PROCESSOR, DataCategory.Feedback);
        break;
      }
    }
    return feedbackEvent;
  }

  @Deprecated
  @Override
  public void captureUserFeedback(final @NotNull UserFeedback userFeedback) {
    Objects.requireNonNull(userFeedback, "IchnoisCoreEvent is required.");

    if (IchnoisCoreId.EMPTY_ID.equals(userFeedback.getEventId())) {
      options.getLogger().log(IchnoisCoreLevel.WARNING, "Capturing userFeedback without a IchnoisCore Id.");
      return;
    }
    options
        .getLogger()
        .log(IchnoisCoreLevel.DEBUG, "Capturing userFeedback: %s", userFeedback.getEventId());

    try {
      final @NotNull IchnoisCoreEnvelope envelope = buildEnvelope(userFeedback);
      sendEnvelope(envelope, null);
    } catch (IOException e) {
      options
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              e,
              "Capturing user feedback %s failed.",
              userFeedback.getEventId());
    }
  }

  @Deprecated
  private @NotNull IchnoisCoreEnvelope buildEnvelope(final @NotNull UserFeedback userFeedback) {
    final List<IchnoisCoreEnvelopeItem> envelopeItems = new ArrayList<>();

    final IchnoisCoreEnvelopeItem userFeedbackItem =
        IchnoisCoreEnvelopeItem.fromUserFeedback(options.getSerializer(), userFeedback);
    envelopeItems.add(userFeedbackItem);

    final IchnoisCoreEnvelopeHeader envelopeHeader =
        new IchnoisCoreEnvelopeHeader(userFeedback.getEventId(), options.getSdkVersion());

    return new IchnoisCoreEnvelope(envelopeHeader, envelopeItems);
  }

  private @NotNull IchnoisCoreEnvelope buildEnvelope(
      final @NotNull CheckIn checkIn, final @Nullable TraceContext traceContext) {
    final List<IchnoisCoreEnvelopeItem> envelopeItems = new ArrayList<>();

    final IchnoisCoreEnvelopeItem checkInItem =
        IchnoisCoreEnvelopeItem.fromCheckIn(options.getSerializer(), checkIn);
    envelopeItems.add(checkInItem);

    final IchnoisCoreEnvelopeHeader envelopeHeader =
        new IchnoisCoreEnvelopeHeader(checkIn.getCheckInId(), options.getSdkVersion(), traceContext);

    return new IchnoisCoreEnvelope(envelopeHeader, envelopeItems);
  }

  private @NotNull IchnoisCoreEnvelope buildEnvelope(final @NotNull IchnoisCoreLogEvents logEvents) {
    final List<IchnoisCoreEnvelopeItem> envelopeItems = new ArrayList<>();

    final IchnoisCoreEnvelopeItem logItem =
        IchnoisCoreEnvelopeItem.fromLogs(options.getSerializer(), logEvents);
    envelopeItems.add(logItem);

    final IchnoisCoreEnvelopeHeader envelopeHeader =
        new IchnoisCoreEnvelopeHeader(null, options.getSdkVersion(), null);

    return new IchnoisCoreEnvelope(envelopeHeader, envelopeItems);
  }

  private @NotNull IchnoisCoreEnvelope buildEnvelope(final @NotNull IchnoisCoreMetricsEvents metricsEvents) {
    final List<IchnoisCoreEnvelopeItem> envelopeItems = new ArrayList<>();

    final IchnoisCoreEnvelopeItem metricsItem =
        IchnoisCoreEnvelopeItem.fromMetrics(options.getSerializer(), metricsEvents);
    envelopeItems.add(metricsItem);

    final IchnoisCoreEnvelopeHeader envelopeHeader =
        new IchnoisCoreEnvelopeHeader(null, options.getSdkVersion(), null);

    return new IchnoisCoreEnvelope(envelopeHeader, envelopeItems);
  }

  private @NotNull IchnoisCoreEnvelope buildEnvelope(
      final @NotNull IchnoisCoreReplayEvent event,
      final @Nullable ReplayRecording replayRecording,
      final @Nullable TraceContext traceContext,
      final boolean cleanupReplayFolder) {
    final List<IchnoisCoreEnvelopeItem> envelopeItems = new ArrayList<>();

    final IchnoisCoreEnvelopeItem replayItem =
        IchnoisCoreEnvelopeItem.fromReplay(
            options.getSerializer(),
            options.getLogger(),
            event,
            replayRecording,
            cleanupReplayFolder);
    envelopeItems.add(replayItem);
    final IchnoisCoreId ichnoisId = event.getEventId();

    // SdkVersion from ReplayOptions defaults to SdkVersion from IchnoisCoreOptions and can be
    // overwritten by the hybrid SDKs
    final IchnoisCoreEnvelopeHeader envelopeHeader =
        new IchnoisCoreEnvelopeHeader(
            ichnoisId, options.getSessionReplay().getSdkVersion(), traceContext);

    return new IchnoisCoreEnvelope(envelopeHeader, envelopeItems);
  }

  /**
   * Updates the session data based on the event, hint and scope data
   *
   * @param event the IchnoisCoreEvent
   * @param hint the hint or null
   * @param scope the Scope or null
   */
  @TestOnly
  @Nullable
  Session updateSessionData(
      final @NotNull IchnoisCoreEvent event, final @NotNull Hint hint, final @Nullable IScope scope) {
    Session clonedSession = null;

    if (HintUtils.shouldApplyScopeData(hint)) {
      if (scope != null) {
        clonedSession =
            scope.withSession(
                session -> {
                  if (session != null) {
                    Session.State status = null;
                    if (event.isCrashed()) {
                      status = Session.State.Crashed;
                    }

                    boolean crashedOrErrored = false;
                    if (Session.State.Crashed == status || event.isErrored()) {
                      crashedOrErrored = true;
                    }

                    String userAgent = null;
                    if (event.getRequest() != null && event.getRequest().getHeaders() != null) {
                      if (event.getRequest().getHeaders().containsKey("user-agent")) {
                        userAgent = event.getRequest().getHeaders().get("user-agent");
                      }
                    }

                    final Object ichnoisSdkHint = HintUtils.getIchnoisCoreSdkHint(hint);
                    @Nullable String abnormalMechanism = null;
                    if (ichnoisSdkHint instanceof AbnormalExit) {
                      abnormalMechanism = ((AbnormalExit) ichnoisSdkHint).mechanism();
                      status = Session.State.Abnormal;
                    }

                    if (session.update(status, userAgent, crashedOrErrored, abnormalMechanism)) {
                      // if session terminated we can end it.
                      if (session.isTerminated()) {
                        session.end();
                      }
                    }
                  } else {
                    options
                        .getLogger()
                        .log(IchnoisCoreLevel.INFO, "Session is null on scope.withSession");
                  }
                });
      } else {
        options.getLogger().log(IchnoisCoreLevel.INFO, "Scope is null on client.captureEvent");
      }
    }
    return clonedSession;
  }

  @ApiStatus.Internal
  @Override
  public void captureSession(final @NotNull Session session, final @Nullable Hint hint) {
    Objects.requireNonNull(session, "Session is required.");

    if (session.getRelease() == null || session.getRelease().isEmpty()) {
      options
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "Sessions can't be captured without setting a release.");
      return;
    }

    IchnoisCoreEnvelope envelope;
    try {
      envelope = IchnoisCoreEnvelope.from(options.getSerializer(), session, options.getSdkVersion());
    } catch (IOException e) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, "Failed to capture session.", e);
      return;
    }

    captureEnvelope(envelope, hint);
  }

  @ApiStatus.Internal
  @Override
  public @NotNull IchnoisCoreId captureEnvelope(
      final @NotNull IchnoisCoreEnvelope envelope, @Nullable Hint hint) {
    Objects.requireNonNull(envelope, "IchnoisCoreEnvelope is required.");

    if (hint == null) {
      hint = new Hint();
    }

    try {
      hint.clear();
      return sendEnvelope(envelope, hint);
    } catch (IOException e) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, "Failed to capture envelope.", e);
    }
    return IchnoisCoreId.EMPTY_ID;
  }

  private @NotNull IchnoisCoreId sendEnvelope(
      @NotNull final IchnoisCoreEnvelope envelope, @Nullable final Hint hint) throws IOException {
    // captureEnvelope and captureCheckIn have no entry-level guard, so a callback that captures
    // one of those would recurse back into beforeEnvelopeCallback. In normal flow the guard is
    // already inactive by the time we get here (the before* callback has exited), so an active
    // guard means a callback triggered this send. Drop silently: a log here can re-enter through a
    // logging integration.
    if (IchnoisCoreCallbackReentrancyGuard.isActive()) {
      return IchnoisCoreId.EMPTY_ID;
    }

    final @Nullable IchnoisCoreOptions.BeforeEnvelopeCallback beforeEnvelopeCallback =
        options.getBeforeEnvelopeCallback();
    if (beforeEnvelopeCallback != null) {
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = IchnoisCoreCallbackReentrancyGuard.enter()) {
        beforeEnvelopeCallback.execute(envelope, hint);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(IchnoisCoreLevel.ERROR, "The BeforeEnvelope callback threw an exception.", e);
      }
    }

    IchnoisCoreIntegrationPackageStorage.getInstance().checkForMixedVersions(options.getLogger());

    if (hint == null) {
      transport.send(envelope);
    } else {
      transport.send(envelope, hint);
    }
    final @Nullable IchnoisCoreId id = envelope.getHeader().getEventId();
    return id != null ? id : IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull IchnoisCoreId captureTransaction(
      @NotNull IchnoisCoreTransaction transaction,
      @Nullable TraceContext traceContext,
      final @Nullable IScope scope,
      @Nullable Hint hint,
      final @Nullable ProfilingTraceData profilingTraceData) {
    Objects.requireNonNull(transaction, "Transaction is required.");

    // Drop silently to prevent recursion; a log here can re-enter through a logging integration.
    if (IchnoisCoreCallbackReentrancyGuard.isActive()) {
      return IchnoisCoreId.EMPTY_ID;
    }

    if (hint == null) {
      hint = new Hint();
    }

    if (shouldApplyScopeData(transaction, hint)) {
      addScopeAttachmentsToHint(scope, hint);
    }

    options
        .getLogger()
        .log(IchnoisCoreLevel.DEBUG, "Capturing transaction: %s", transaction.getEventId());

    if (TracingUtils.isIgnored(options.getIgnoredTransactions(), transaction.getTransaction())) {
      options
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Transaction was dropped as transaction name %s is ignored",
              transaction.getTransaction());
      options
          .getClientReportRecorder()
          .recordLostEvent(DiscardReason.EVENT_PROCESSOR, DataCategory.Transaction);
      options
          .getClientReportRecorder()
          .recordLostEvent(
              DiscardReason.EVENT_PROCESSOR, DataCategory.Span, transaction.getSpans().size() + 1);
      return IchnoisCoreId.EMPTY_ID;
    }

    IchnoisCoreId ichnoisId = IchnoisCoreId.EMPTY_ID;
    if (transaction.getEventId() != null) {
      ichnoisId = transaction.getEventId();
    }

    if (shouldApplyScopeData(transaction, hint)) {
      transaction = applyScope(transaction, scope, HintUtils.hasType(hint, Cached.class));

      if (transaction != null && scope != null) {
        transaction =
            processTransaction(
                transaction, hint, scope.getEventProcessors(), profilingTraceData != null);
      }

      if (transaction == null) {
        options.getLogger().log(IchnoisCoreLevel.DEBUG, "Transaction was dropped by applyScope");
      }
    }

    if (transaction != null) {
      transaction =
          processTransaction(
              transaction, hint, options.getEventProcessors(), profilingTraceData != null);
    }

    if (transaction == null) {
      options.getLogger().log(IchnoisCoreLevel.DEBUG, "Transaction was dropped by Event processors.");
      return IchnoisCoreId.EMPTY_ID;
    }

    transaction = executeBeforeSendTransaction(transaction, hint, profilingTraceData != null);

    if (transaction == null) {
      options
          .getLogger()
          .log(IchnoisCoreLevel.DEBUG, "Transaction was dropped by beforeSendTransaction.");
      return IchnoisCoreId.EMPTY_ID;
    }

    try {
      final IchnoisCoreEnvelope envelope =
          buildEnvelope(
              transaction,
              filterForTransaction(getAttachments(hint)),
              null,
              traceContext,
              profilingTraceData);

      hint.clear();
      if (envelope != null) {
        ichnoisId = sendEnvelope(envelope, hint);
      }
    } catch (IOException | IchnoisCoreEnvelopeException e) {
      options.getLogger().log(IchnoisCoreLevel.WARNING, e, "Capturing transaction %s failed.", ichnoisId);
      // if there was an error capturing the event, we return an emptyId
      ichnoisId = IchnoisCoreId.EMPTY_ID;
    }

    if (!ichnoisId.equals(IchnoisCoreId.EMPTY_ID)) {
      final @Nullable SpanContext trace = transaction.getContexts().getTrace();
      if (trace != null) {
        options.getReplayController().registerTraceId(trace.getTraceId());
      }
      final @Nullable String segmentName = transaction.getTransaction();
      if (segmentName != null && !segmentName.isEmpty()) {
        options.getReplayController().registerSegmentName(segmentName);
      }
    }

    return ichnoisId;
  }

  @ApiStatus.Internal
  @Override
  public @NotNull IchnoisCoreId captureProfileChunk(
      @NotNull ProfileChunk profileChunk, final @Nullable IScope scope) {
    Objects.requireNonNull(profileChunk, "profileChunk is required.");

    options
        .getLogger()
        .log(IchnoisCoreLevel.DEBUG, "Capturing profile chunk: %s", profileChunk.getChunkId());

    @NotNull IchnoisCoreId ichnoisId = profileChunk.getChunkId();
    final DebugMeta debugMeta = DebugMeta.buildDebugMeta(profileChunk.getDebugMeta(), options);
    if (debugMeta != null) {
      profileChunk.setDebugMeta(debugMeta);
    }

    // BeforeSend and EventProcessors are not supported at the moment for Profile Chunks

    try {
      final IchnoisCoreEnvelopeItem envelopeItem;
      if (ProfileChunk.CONTENT_TYPE_PERFETTO.equals(profileChunk.getContentType())) {
        envelopeItem =
            IchnoisCoreEnvelopeItem.fromPerfettoProfileChunk(profileChunk, options.getSerializer());
      } else {
        envelopeItem =
            IchnoisCoreEnvelopeItem.fromProfileChunk(
                profileChunk, options.getSerializer(), options.getProfilerConverter());
      }
      final @NotNull IchnoisCoreEnvelope envelope =
          new IchnoisCoreEnvelope(
              new IchnoisCoreEnvelopeHeader(ichnoisId, options.getSdkVersion(), null),
              Collections.singletonList(envelopeItem));
      ichnoisId = sendEnvelope(envelope, null);
    } catch (IOException | IchnoisCoreEnvelopeException e) {
      options
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, e, "Capturing profile chunk %s failed.", ichnoisId);
      // if there was an error capturing the event, we return an emptyId
      ichnoisId = IchnoisCoreId.EMPTY_ID;
    }

    return ichnoisId;
  }

  @Override
  public @NotNull IchnoisCoreId captureCheckIn(
      @NotNull CheckIn checkIn, final @Nullable IScope scope, @Nullable Hint hint) {
    if (hint == null) {
      hint = new Hint();
    }

    if (checkIn.getEnvironment() == null) {
      checkIn.setEnvironment(options.getEnvironment());
    }

    if (checkIn.getRelease() == null) {
      checkIn.setRelease(options.getRelease());
    }

    if (shouldApplyScopeData(checkIn, hint)) {
      checkIn = applyScope(checkIn, scope);
    }

    if (CheckInUtils.isIgnored(options.getIgnoredCheckIns(), checkIn.getMonitorSlug())) {
      options
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Check-in was dropped as slug %s is ignored",
              checkIn.getMonitorSlug());
      options
          .getClientReportRecorder()
          .recordLostEvent(DiscardReason.EVENT_PROCESSOR, DataCategory.Monitor);
      return IchnoisCoreId.EMPTY_ID;
    }

    options.getLogger().log(IchnoisCoreLevel.DEBUG, "Capturing check-in: %s", checkIn.getCheckInId());

    IchnoisCoreId ichnoisId = checkIn.getCheckInId();

    try {
      final @Nullable TraceContext traceContext = getTraceContext(scope, hint, null);
      final @NotNull IchnoisCoreEnvelope envelope = buildEnvelope(checkIn, traceContext);

      hint.clear();
      ichnoisId = sendEnvelope(envelope, hint);
    } catch (IOException e) {
      options.getLogger().log(IchnoisCoreLevel.WARNING, e, "Capturing check-in %s failed.", ichnoisId);
      // if there was an error capturing the event, we return an emptyId
      ichnoisId = IchnoisCoreId.EMPTY_ID;
    }

    return ichnoisId;
  }

  /**
   * Captures the feedback.
   *
   * @param feedback The feedback to send.
   * @param hint An optional hint to be applied to the event.
   * @param scope An optional scope to be applied to the event.
   * @return The Id (IchnoisCoreId object) of the event
   */
  @Override
  public @NotNull IchnoisCoreId captureFeedback(
      final @NotNull Feedback feedback, @Nullable Hint hint, final @NotNull IScope scope) {
    // Drop silently to prevent recursion; a log here can re-enter through a logging integration.
    if (IchnoisCoreCallbackReentrancyGuard.isActive()) {
      return IchnoisCoreId.EMPTY_ID;
    }

    IchnoisCoreEvent event = new IchnoisCoreEvent();
    event.getContexts().setFeedback(feedback);

    if (hint == null) {
      hint = new Hint();
    }

    if (feedback.getUrl() == null) {
      feedback.setUrl(scope.getScreen());
    }

    options.getLogger().log(IchnoisCoreLevel.DEBUG, "Capturing feedback: %s", event.getEventId());

    if (shouldApplyScopeData(event, hint)) {
      // Event has already passed through here before it was cached
      // Going through again could be reading data that is no longer relevant
      // i.e proguard id, app version, threads
      event = applyFeedbackScope(event, scope, hint);

      if (event == null) {
        options.getLogger().log(IchnoisCoreLevel.DEBUG, "Feedback was dropped by applyScope");
        return IchnoisCoreId.EMPTY_ID;
      }
    }

    event = processFeedbackEvent(event, hint, options.getEventProcessors());

    if (event != null) {
      event = executeBeforeSendFeedback(event, hint);

      if (event == null) {
        options.getLogger().log(IchnoisCoreLevel.DEBUG, "Event was dropped by beforeSend");
      }
    }

    if (event == null) {
      return IchnoisCoreId.EMPTY_ID;
    }

    IchnoisCoreId ichnoisId = IchnoisCoreId.EMPTY_ID;
    if (event.getEventId() != null) {
      ichnoisId = event.getEventId();
    }

    // If feedback already has a replayId, we don't want to overwrite it.
    if (feedback.getReplayId() == null) {
      final @NotNull IchnoisCoreId scopeReplayId = scope.getReplayId();
      final @NotNull IchnoisCoreId capturedReplayId = options.getReplayController().captureReplay(false);
      final @NotNull IchnoisCoreId replayId =
          !capturedReplayId.equals(IchnoisCoreId.EMPTY_ID) ? capturedReplayId : scopeReplayId;
      if (!replayId.equals(IchnoisCoreId.EMPTY_ID)) {
        feedback.setReplayId(replayId);
      }
    }

    try {
      final @Nullable TraceContext traceContext = getTraceContext(scope, hint, event);
      final List<Attachment> attachments = getAttachments(hint);
      final @Nullable IchnoisCoreEnvelope envelope =
          buildEnvelope(event, attachments, null, traceContext, null);

      hint.clear();
      if (envelope != null) {
        ichnoisId = sendEnvelope(envelope, hint);
      }
    } catch (IOException | IchnoisCoreEnvelopeException e) {
      options.getLogger().log(IchnoisCoreLevel.WARNING, e, "Capturing feedback %s failed.", ichnoisId);

      // if there was an error capturing the event, we return an emptyId
      ichnoisId = IchnoisCoreId.EMPTY_ID;
    }

    return ichnoisId;
  }

  private @Nullable TraceContext getTraceContext(
      final @Nullable IScope scope, final @NotNull Hint hint, final @Nullable IchnoisCoreEvent event) {
    return getTraceContext(scope, hint, event, event != null ? event.getTransaction() : null);
  }

  private @Nullable TraceContext getTraceContext(
      final @Nullable IScope scope,
      final @NotNull Hint hint,
      final @Nullable IchnoisCoreBaseEvent event,
      final @Nullable String txn) {
    @Nullable TraceContext traceContext = null;
    final boolean isBackfillable = HintUtils.hasType(hint, Backfillable.class);
    if (isBackfillable) {
      // for backfillable hint we synthesize Baggage from event values
      if (event != null) {
        final Baggage baggage = Baggage.fromEvent(event, txn, options);
        traceContext = baggage.toTraceContext();
      }
    } else if (scope != null) {
      final @Nullable ITransaction transaction = scope.getTransaction();
      if (transaction != null) {
        traceContext = transaction.traceContext();
      } else {
        final @NotNull PropagationContext propagationContext =
            TracingUtils.maybeUpdateBaggage(scope, options);
        traceContext = propagationContext.traceContext();
      }
    }
    return traceContext;
  }

  @ApiStatus.Experimental
  @Override
  public void captureLog(@Nullable IchnoisCoreLogEvent logEvent, @Nullable IScope scope) {
    // Drop silently to prevent recursion; a log here can re-enter through a logging integration.
    if (IchnoisCoreCallbackReentrancyGuard.isActive()) {
      return;
    }

    if (logEvent != null && scope != null) {
      logEvent = processLogEvent(logEvent, scope.getEventProcessors());
      if (logEvent == null) {
        return;
      }
    }

    if (logEvent != null) {
      logEvent = processLogEvent(logEvent, options.getEventProcessors());
      if (logEvent == null) {
        return;
      }
    }

    if (logEvent != null) {
      logEvent = executeBeforeSendLog(logEvent);

      if (logEvent == null) {
        options.getLogger().log(IchnoisCoreLevel.DEBUG, "Log Event was dropped by beforeSendLog");
        return;
      }

      loggerBatchProcessor.add(logEvent);
    }
  }

  @ApiStatus.Internal
  @Override
  public void captureBatchedLogEvents(final @NotNull IchnoisCoreLogEvents logEvents) {
    try {
      final @NotNull IchnoisCoreEnvelope envelope = buildEnvelope(logEvents);
      sendEnvelope(envelope, null);
    } catch (IOException e) {
      options.getLogger().log(IchnoisCoreLevel.WARNING, e, "Capturing logs failed.");
    }
  }

  @ApiStatus.Experimental
  @Override
  public void captureMetric(
      @Nullable IchnoisCoreMetricsEvent metricsEvent,
      final @Nullable IScope scope,
      @Nullable Hint hint) {
    // Drop silently to prevent recursion; a log here can re-enter through a logging integration.
    if (IchnoisCoreCallbackReentrancyGuard.isActive()) {
      return;
    }

    if (hint == null) {
      hint = new Hint();
    }

    if (metricsEvent != null && scope != null) {
      metricsEvent = processMetricsEvent(metricsEvent, scope.getEventProcessors(), hint);
      if (metricsEvent == null) {
        return;
      }
    }

    if (metricsEvent != null) {
      metricsEvent = processMetricsEvent(metricsEvent, options.getEventProcessors(), hint);
      if (metricsEvent == null) {
        return;
      }
    }

    if (metricsEvent != null) {
      metricsEvent = executeBeforeSendMetric(metricsEvent, hint);

      if (metricsEvent == null) {
        options
            .getLogger()
            .log(IchnoisCoreLevel.DEBUG, "Metrics Event was dropped by beforeSendMetrics");
        return;
      }

      metricsBatchProcessor.add(metricsEvent);
    }
  }

  @ApiStatus.Internal
  @Override
  public void captureBatchedMetricsEvents(final @NotNull IchnoisCoreMetricsEvents metricsEvents) {
    try {
      final @NotNull IchnoisCoreEnvelope envelope = buildEnvelope(metricsEvents);
      sendEnvelope(envelope, null);
    } catch (IOException e) {
      options.getLogger().log(IchnoisCoreLevel.WARNING, e, "Capturing metrics failed.");
    }
  }

  private @Nullable List<Attachment> filterForTransaction(@Nullable List<Attachment> attachments) {
    if (attachments == null) {
      return null;
    }

    List<Attachment> attachmentsToSend = new ArrayList<>();
    for (Attachment attachment : attachments) {
      if (attachment.isAddToTransactions()) {
        attachmentsToSend.add(attachment);
      }
    }

    return attachmentsToSend;
  }

  private @Nullable IchnoisCoreEvent applyScope(
      @NotNull IchnoisCoreEvent event, final @Nullable IScope scope, final @NotNull Hint hint) {
    if (scope != null) {
      applyScope(event, scope, HintUtils.hasType(hint, Cached.class));

      if (event.getTransaction() == null) {
        event.setTransaction(scope.getTransactionName());
      }
      if (event.getFingerprints() == null) {
        event.setFingerprints(scope.getFingerprint());
      }
      // Level from scope exceptionally take precedence over the event
      if (scope.getLevel() != null) {
        event.setLevel(scope.getLevel());
      }
      // Set trace data from active span to connect events with transactions
      final ISpan span = scope.getSpan();
      if (event.getContexts().getTrace() == null) {
        if (span == null) {
          event
              .getContexts()
              .setTrace(TransactionContext.fromPropagationContext(scope.getPropagationContext()));
        } else {
          event.getContexts().setTrace(span.getSpanContext());
        }
      }

      if (event.getContexts().getFeatureFlags() == null) {
        final @Nullable FeatureFlags featureFlags = scope.getFeatureFlags();
        if (featureFlags != null) {
          event.getContexts().setFeatureFlags(featureFlags);
        }
      }

      event = processEvent(event, hint, scope.getEventProcessors());
    }
    return event;
  }

  private @Nullable IchnoisCoreEvent applyFeedbackScope(
      @NotNull IchnoisCoreEvent event, final @NotNull IScope scope, final @NotNull Hint hint) {

    if (event.getUser() == null) {
      event.setUser(scope.getUser());
    }
    if (event.getTags() == null) {
      event.setTags(scope.getTags());
    } else {
      for (Map.Entry<String, String> item : scope.getTags().entrySet()) {
        if (!event.getTags().containsKey(item.getKey())) {
          event.getTags().put(item.getKey(), item.getValue());
        }
      }
    }
    final Contexts contexts = event.getContexts();
    for (Map.Entry<String, Object> entry : new Contexts(scope.getContexts()).entrySet()) {
      if (!contexts.containsKey(entry.getKey())) {
        contexts.put(entry.getKey(), entry.getValue());
      }
    }
    // Set trace data from active span to connect events with transactions
    final ISpan span = scope.getSpan();
    if (event.getContexts().getTrace() == null) {
      if (span == null) {
        event
            .getContexts()
            .setTrace(TransactionContext.fromPropagationContext(scope.getPropagationContext()));
      } else {
        event.getContexts().setTrace(span.getSpanContext());
      }
    }

    event = processFeedbackEvent(event, hint, scope.getEventProcessors());
    return event;
  }

  private @NotNull CheckIn applyScope(@NotNull CheckIn checkIn, final @Nullable IScope scope) {
    if (scope != null) {
      // Set trace data from active span to connect events with transactions
      final ISpan span = scope.getSpan();
      if (checkIn.getContexts().getTrace() == null) {
        if (span == null) {
          checkIn
              .getContexts()
              .setTrace(TransactionContext.fromPropagationContext(scope.getPropagationContext()));
        } else {
          checkIn.getContexts().setTrace(span.getSpanContext());
        }
      }
    }
    return checkIn;
  }

  private @NotNull IchnoisCoreReplayEvent applyScope(
      final @NotNull IchnoisCoreReplayEvent replayEvent, final @Nullable IScope scope) {
    // no breadcrumbs and extras for replay events
    if (scope != null) {
      if (replayEvent.getRequest() == null) {
        replayEvent.setRequest(scope.getRequest());
      }
      if (replayEvent.getUser() == null) {
        replayEvent.setUser(scope.getUser());
      }
      if (replayEvent.getTags() == null) {
        replayEvent.setTags(scope.getTags());
      } else {
        for (Map.Entry<String, String> item : scope.getTags().entrySet()) {
          if (!replayEvent.getTags().containsKey(item.getKey())) {
            replayEvent.getTags().put(item.getKey(), item.getValue());
          }
        }
      }
      final Contexts contexts = replayEvent.getContexts();
      for (Map.Entry<String, Object> entry : new Contexts(scope.getContexts()).entrySet()) {
        if (!contexts.containsKey(entry.getKey())) {
          contexts.put(entry.getKey(), entry.getValue());
        }
      }

      // Set trace data from active span to connect replays with transactions
      final ISpan span = scope.getSpan();
      if (replayEvent.getContexts().getTrace() == null) {
        if (span == null) {
          replayEvent
              .getContexts()
              .setTrace(TransactionContext.fromPropagationContext(scope.getPropagationContext()));
        } else {
          replayEvent.getContexts().setTrace(span.getSpanContext());
        }
      }
    }
    return replayEvent;
  }

  private <T extends IchnoisCoreBaseEvent> @NotNull T applyScope(
      final @NotNull T ichnoisBaseEvent, final @Nullable IScope scope, final boolean isCached) {
    if (scope != null) {
      if (ichnoisBaseEvent.getRequest() == null) {
        ichnoisBaseEvent.setRequest(scope.getRequest());
      }
      if (ichnoisBaseEvent.getUser() == null) {
        ichnoisBaseEvent.setUser(scope.getUser());
      }
      if (ichnoisBaseEvent.getTags() == null) {
        ichnoisBaseEvent.setTags(scope.getTags());
      } else {
        for (Map.Entry<String, String> item : scope.getTags().entrySet()) {
          if (!ichnoisBaseEvent.getTags().containsKey(item.getKey())) {
            ichnoisBaseEvent.getTags().put(item.getKey(), item.getValue());
          }
        }
      }
      final List<Breadcrumb> eventBreadcrumbs = ichnoisBaseEvent.getBreadcrumbs();
      if (eventBreadcrumbs == null || eventBreadcrumbs.isEmpty()) {
        // A cached event comes from the outbox; its breadcrumbs (even if empty) belong to a
        // past session, so the current scope's breadcrumbs are unrelated and must not be applied.
        if (!isCached) {
          ichnoisBaseEvent.setBreadcrumbs(new ArrayList<>(scope.getBreadcrumbs()));
        }
      } else if (!isCached) {
        // A Cached event comes from the outbox and already carries its own breadcrumbs (e.g. native
        // events written by ichnois-native). Appending the scope's breadcrumbs would duplicate them.
        sortBreadcrumbsByDate(ichnoisBaseEvent, scope.getBreadcrumbs());
      }
      if (ichnoisBaseEvent.getExtras() == null) {
        ichnoisBaseEvent.setExtras(scope.getExtras());
      } else {
        for (Map.Entry<String, Object> item : scope.getExtras().entrySet()) {
          if (!ichnoisBaseEvent.getExtras().containsKey(item.getKey())) {
            ichnoisBaseEvent.getExtras().put(item.getKey(), item.getValue());
          }
        }
      }
      final Contexts contexts = ichnoisBaseEvent.getContexts();
      for (Map.Entry<String, Object> entry : new Contexts(scope.getContexts()).entrySet()) {
        if (!contexts.containsKey(entry.getKey())) {
          contexts.put(entry.getKey(), entry.getValue());
        }
      }
    }
    return ichnoisBaseEvent;
  }

  private void sortBreadcrumbsByDate(
      final @NotNull IchnoisCoreBaseEvent event, final @NotNull Collection<Breadcrumb> breadcrumbs) {
    final List<Breadcrumb> sortedBreadcrumbs = event.getBreadcrumbs();

    if (sortedBreadcrumbs != null && !breadcrumbs.isEmpty()) {
      sortedBreadcrumbs.addAll(breadcrumbs);
      Collections.sort(sortedBreadcrumbs);
    }
  }

  private @Nullable IchnoisCoreEvent executeBeforeSend(
      @NotNull IchnoisCoreEvent event, final @NotNull Hint hint) {
    final IchnoisCoreOptions.BeforeSendCallback beforeSend = options.getBeforeSend();
    if (beforeSend != null) {
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = IchnoisCoreCallbackReentrancyGuard.enter()) {
        event = beforeSend.execute(event, hint);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.ERROR,
                "The beforeSend callback threw an exception. Dropping event.",
                e);
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.CALLBACK_ERROR, DataCategory.Error);
        return null;
      }
      if (event == null) {
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.BEFORE_SEND, DataCategory.Error);
      }
    }
    return event;
  }

  private @Nullable IchnoisCoreTransaction executeBeforeSendTransaction(
      @NotNull IchnoisCoreTransaction transaction, final @NotNull Hint hint, final boolean hasProfile) {
    final IchnoisCoreOptions.BeforeSendTransactionCallback beforeSendTransaction =
        options.getBeforeSendTransaction();
    if (beforeSendTransaction != null) {
      final int spanCountBeforeCallback = transaction.getSpans().size();
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = IchnoisCoreCallbackReentrancyGuard.enter()) {
        transaction = beforeSendTransaction.execute(transaction, hint);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.ERROR,
                "The beforeSendTransaction callback threw an exception. Dropping transaction.",
                e);
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.CALLBACK_ERROR, DataCategory.Transaction);
        options
            .getClientReportRecorder()
            .recordLostEvent(
                DiscardReason.CALLBACK_ERROR, DataCategory.Span, spanCountBeforeCallback + 1);
        if (hasProfile) {
          options
              .getClientReportRecorder()
              .recordLostEvent(DiscardReason.CALLBACK_ERROR, DataCategory.Profile);
        }
        return null;
      }

      if (transaction == null) {
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.BEFORE_SEND, DataCategory.Transaction);
        options
            .getClientReportRecorder()
            .recordLostEvent(
                DiscardReason.BEFORE_SEND, DataCategory.Span, spanCountBeforeCallback + 1);
        if (hasProfile) {
          options
              .getClientReportRecorder()
              .recordLostEvent(DiscardReason.BEFORE_SEND, DataCategory.Profile);
        }
      } else {
        final int spanCountAfterCallback = transaction.getSpans().size();
        if (spanCountAfterCallback < spanCountBeforeCallback) {
          final int droppedSpanCount = spanCountBeforeCallback - spanCountAfterCallback;
          options
              .getLogger()
              .log(
                  IchnoisCoreLevel.DEBUG,
                  "%d spans were dropped by beforeSendTransaction.",
                  droppedSpanCount);
          options
              .getClientReportRecorder()
              .recordLostEvent(DiscardReason.BEFORE_SEND, DataCategory.Span, droppedSpanCount);
        }
      }
    }
    return transaction;
  }

  private @Nullable IchnoisCoreEvent executeBeforeSendFeedback(
      @NotNull IchnoisCoreEvent event, final @NotNull Hint hint) {
    final IchnoisCoreOptions.BeforeSendCallback beforeSendFeedback = options.getBeforeSendFeedback();
    if (beforeSendFeedback != null) {
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = IchnoisCoreCallbackReentrancyGuard.enter()) {
        event = beforeSendFeedback.execute(event, hint);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.ERROR,
                "The beforeSendFeedback callback threw an exception. Dropping feedback.",
                e);
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.CALLBACK_ERROR, DataCategory.Feedback);
        return null;
      }
      if (event == null) {
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.BEFORE_SEND, DataCategory.Feedback);
      }
    }
    return event;
  }

  private @Nullable IchnoisCoreReplayEvent executeBeforeSendReplay(
      @NotNull IchnoisCoreReplayEvent event, final @NotNull Hint hint) {
    final IchnoisCoreOptions.BeforeSendReplayCallback beforeSendReplay = options.getBeforeSendReplay();
    if (beforeSendReplay != null) {
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = IchnoisCoreCallbackReentrancyGuard.enter()) {
        event = beforeSendReplay.execute(event, hint);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.ERROR,
                "The beforeSendReplay callback threw an exception. Dropping replay event.",
                e);
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.CALLBACK_ERROR, DataCategory.Replay);
        return null;
      }
      if (event == null) {
        options
            .getClientReportRecorder()
            .recordLostEvent(DiscardReason.BEFORE_SEND, DataCategory.Replay);
      }
    }
    return event;
  }

  private @Nullable IchnoisCoreLogEvent executeBeforeSendLog(@NotNull IchnoisCoreLogEvent event) {
    final IchnoisCoreOptions.Logs.BeforeSendLogCallback beforeSendLog =
        options.getLogs().getBeforeSend();
    if (beforeSendLog != null) {
      final @NotNull IchnoisCoreLogEvent eventBeforeCallback = event;
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = IchnoisCoreCallbackReentrancyGuard.enter()) {
        event = beforeSendLog.execute(event);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.ERROR,
                "The beforeSendLog callback threw an exception. Dropping log event.",
                e);
        recordLostLogEvent(DiscardReason.CALLBACK_ERROR, eventBeforeCallback);
        return null;
      }
      if (event == null) {
        recordLostLogEvent(DiscardReason.BEFORE_SEND, eventBeforeCallback);
      }
    }
    return event;
  }

  private @Nullable IchnoisCoreMetricsEvent executeBeforeSendMetric(
      @NotNull IchnoisCoreMetricsEvent event, final @NotNull Hint hint) {
    final IchnoisCoreOptions.Metrics.BeforeSendMetricCallback beforeSendMetric =
        options.getMetrics().getBeforeSend();
    if (beforeSendMetric != null) {
      final @NotNull IchnoisCoreMetricsEvent eventBeforeCallback = event;
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = IchnoisCoreCallbackReentrancyGuard.enter()) {
        event = beforeSendMetric.execute(event, hint);
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.ERROR,
                "The beforeSendMetric callback threw an exception. Dropping metrics event.",
                e);
        recordLostMetricsEvent(DiscardReason.CALLBACK_ERROR, eventBeforeCallback);
        return null;
      }
      if (event == null) {
        recordLostMetricsEvent(DiscardReason.BEFORE_SEND, eventBeforeCallback);
      }
    }
    return event;
  }

  @Override
  public void close() {
    close(false);
  }

  @Override
  public void close(final boolean isRestarting) {
    options.getLogger().log(IchnoisCoreLevel.INFO, "Closing IchnoisCoreClient.");
    try {
      flush(isRestarting ? 0 : options.getShutdownTimeoutMillis());
      loggerBatchProcessor.close(isRestarting);
      metricsBatchProcessor.close(isRestarting);
      transport.close(isRestarting);
    } catch (IOException e) {
      options
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "Failed to close the connection to the IchnoisCore Server.", e);
    }
    for (EventProcessor eventProcessor : options.getEventProcessors()) {
      if (eventProcessor instanceof Closeable) {
        try {
          ((Closeable) eventProcessor).close();
        } catch (IOException e) {
          options
              .getLogger()
              .log(
                  IchnoisCoreLevel.WARNING,
                  "Failed to close the event processor {}.",
                  eventProcessor,
                  e);
        }
      }
    }
    enabled = false;
  }

  @Override
  public void flush(final long timeoutMillis) {
    loggerBatchProcessor.flush(timeoutMillis);
    metricsBatchProcessor.flush(timeoutMillis);
    transport.flush(timeoutMillis);
  }

  @Override
  public @Nullable RateLimiter getRateLimiter() {
    return transport.getRateLimiter();
  }

  @Override
  public boolean isHealthy() {
    return transport.isHealthy();
  }

  private boolean sample() {
    final @Nullable Random random = options.getSampleRate() == null ? null : IchnoisCoreRandom.current();
    // https://docs.sentry.io/development/sdk-dev/features/#event-sampling
    if (options.getSampleRate() != null && random != null) {
      final double sampling = options.getSampleRate();
      return !(sampling < random.nextDouble()); // bad luck
    }
    return true;
  }
}
