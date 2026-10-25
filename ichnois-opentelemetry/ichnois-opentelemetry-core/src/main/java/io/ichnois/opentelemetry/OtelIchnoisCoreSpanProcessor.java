package io.ichnois.opentelemetry;

import static io.ichnois.opentelemetry.InternalSemanticAttributes.IS_REMOTE_PARENT;
import static io.ichnois.opentelemetry.IchnoisCoreOtelKeys.ICHNOIS_SCOPES_KEY;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.ExceptionEventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.ichnois.Baggage;
import io.ichnois.DateUtils;
import io.ichnois.IScopes;
import io.ichnois.ProfileLifecycle;
import io.ichnois.PropagationContext;
import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCore;
import io.ichnois.IchnoisCoreDate;
import io.ichnois.IchnoisCoreEvent;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreLongDate;
import io.ichnois.IchnoisCoreTraceHeader;
import io.ichnois.SpanId;
import io.ichnois.TracesSamplingDecision;
import io.ichnois.exception.ExceptionMechanismException;
import io.ichnois.protocol.Mechanism;
import io.ichnois.protocol.IchnoisCoreId;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class OtelIchnoisCoreSpanProcessor implements SpanProcessor {
  private final @NotNull IchnoisCoreWeakSpanStorage spanStorage = IchnoisCoreWeakSpanStorage.getInstance();
  private final @NotNull IScopes scopes;

  public OtelIchnoisCoreSpanProcessor() {
    this(ScopesAdapter.getInstance());
  }

  OtelIchnoisCoreSpanProcessor(final @NotNull IScopes scopes) {
    this.scopes = scopes;
  }

  @Override
  public void onStart(final @NotNull Context parentContext, final @NotNull ReadWriteSpan otelSpan) {
    if (!ensurePrerequisites(otelSpan)) {
      return;
    }

    final @NotNull IScopes scopes = forkScopes(parentContext, otelSpan.toSpanData());

    final @Nullable IOtelSpanWrapper ichnoisParentSpan =
        spanStorage.getIchnoisCoreSpan(otelSpan.getParentSpanContext());
    @Nullable
    TracesSamplingDecision samplingDecision =
        OtelSamplingUtil.extractSamplingDecision(otelSpan.toSpanData().getAttributes());
    @Nullable Baggage baggage = null;
    @Nullable SpanId ichnoisParentSpanId = null;
    otelSpan.setAttribute(IS_REMOTE_PARENT, otelSpan.getParentSpanContext().isRemote());
    if (ichnoisParentSpan == null) {
      final @NotNull String traceId = otelSpan.getSpanContext().getTraceId();
      final @NotNull String spanId = otelSpan.getSpanContext().getSpanId();
      final @NotNull SpanId ichnoisSpanId = new SpanId(spanId);
      final @NotNull String parentSpanId = otelSpan.getParentSpanContext().getSpanId();
      ichnoisParentSpanId =
          io.opentelemetry.api.trace.SpanId.isValid(parentSpanId) ? new SpanId(parentSpanId) : null;

      @Nullable
      IchnoisCoreTraceHeader ichnoisTraceHeader = parentContext.get(IchnoisCoreOtelKeys.ICHNOIS_TRACE_KEY);
      @Nullable Baggage baggageFromContext = parentContext.get(IchnoisCoreOtelKeys.ICHNOIS_BAGGAGE_KEY);
      if (ichnoisTraceHeader != null) {
        baggage = baggageFromContext;
      }

      final @Nullable String baggageString =
          otelSpan.getAttribute(InternalSemanticAttributes.BAGGAGE);
      if (baggageString != null) {
        baggage = Baggage.fromHeader(baggageString);
      }

      final @Nullable Boolean sampled = isSampled(otelSpan, samplingDecision);

      if (Boolean.TRUE.equals(sampled) && isRootSpan(otelSpan.toSpanData())) {
        if (scopes.getOptions().isContinuousProfilingEnabled()
            && scopes.getOptions().getProfileLifecycle() == ProfileLifecycle.TRACE) {
          scopes
              .getOptions()
              .getContinuousProfiler()
              .startProfiler(
                  ProfileLifecycle.TRACE, scopes.getOptions().getInternalTracesSampler());
        }
      }

      final @NotNull PropagationContext propagationContext =
          new PropagationContext(
              new IchnoisCoreId(traceId), ichnoisSpanId, ichnoisParentSpanId, baggage, sampled);

      baggage = propagationContext.getBaggage();
      baggage.setValuesFromSamplingDecision(samplingDecision);

      updatePropagationContext(scopes, propagationContext);
    }

    final @NotNull SpanContext spanContext = otelSpan.getSpanContext();
    final @NotNull IchnoisCoreDate startTimestamp =
        new IchnoisCoreLongDate(otelSpan.toSpanData().getStartEpochNanos());
    final @NotNull IOtelSpanWrapper ichnoisSpan =
        new OtelSpanWrapper(
            otelSpan,
            scopes,
            startTimestamp,
            samplingDecision,
            ichnoisParentSpan,
            ichnoisParentSpanId,
            baggage);
    ichnoisSpan.getSpanContext().setOrigin(IchnoisCoreSpanExporter.TRACE_ORIGIN);
    ichnoisSpan
        .getSpanContext()
        .setProfilerId(scopes.getOptions().getContinuousProfiler().getProfilerId());
    spanStorage.storeIchnoisCoreSpan(spanContext, ichnoisSpan);
  }

  private IScopes forkScopes(final @NotNull Context context, final @NotNull SpanData span) {
    final @Nullable IScopes scopesFromContext = context.get(ICHNOIS_SCOPES_KEY);
    if (scopesFromContext == null || scopesFromContext.isNoOp()) {
      return IchnoisCore.forkedRootScopes("spanprocessor.new");
    }
    if (isRootSpan(span)) {
      return scopesFromContext.forkedScopes("spanprocessor.rootspan");
    }

    return scopesFromContext.forkedCurrentScope("spanprocessor.nonrootspan");
  }

  private boolean isRootSpan(SpanData otelSpan) {
    return !otelSpan.getParentSpanContext().isValid() || otelSpan.getParentSpanContext().isRemote();
  }

  private @Nullable Boolean isSampled(
      final @NotNull ReadWriteSpan otelSpan,
      final @Nullable TracesSamplingDecision samplingDecision) {
    if (samplingDecision != null) {
      return samplingDecision.getSampled();
    }

    if (otelSpan.getSpanContext().isSampled()) {
      return true;
    }

    // tracing without performance
    return null;
  }

  private static void updatePropagationContext(
      IScopes scopes, PropagationContext propagationContext) {
    scopes.configureScope(
        scope -> {
          scope.withPropagationContext(
              oldPropagationContext -> {
                scope.setPropagationContext(propagationContext);
              });
        });
  }

  @Override
  public boolean isStartRequired() {
    return true;
  }

  @Override
  public void onEnd(final @NotNull ReadableSpan spanBeingEnded) {
    final @Nullable IOtelSpanWrapper ichnoisSpan =
        spanStorage.getIchnoisCoreSpan(spanBeingEnded.getSpanContext());

    if (isRootSpan(spanBeingEnded.toSpanData())
        && scopes.getOptions().isContinuousProfilingEnabled()
        && scopes.getOptions().getProfileLifecycle() == ProfileLifecycle.TRACE) {
      scopes.getOptions().getContinuousProfiler().stopProfiler(ProfileLifecycle.TRACE);
    }

    if (ichnoisSpan != null) {
      final @NotNull IchnoisCoreDate finishDate =
          new IchnoisCoreLongDate(spanBeingEnded.toSpanData().getEndEpochNanos());
      ichnoisSpan.updateEndDate(finishDate);

      maybeCaptureSpanEventsAsExceptions(spanBeingEnded, ichnoisSpan);
    }
  }

  private void maybeCaptureSpanEventsAsExceptions(
      final @NotNull ReadableSpan spanBeingEnded, final @NotNull IOtelSpanWrapper ichnoisSpan) {
    final @NotNull IScopes spanScopes = ichnoisSpan.getScopes();
    if (spanScopes.getOptions().isCaptureOpenTelemetryEvents()) {
      final @NotNull List<EventData> events = spanBeingEnded.toSpanData().getEvents();
      for (EventData event : events) {
        if (event instanceof ExceptionEventData) {
          final @NotNull ExceptionEventData exceptionEvent = (ExceptionEventData) event;
          captureException(spanScopes, exceptionEvent, ichnoisSpan);
        }
      }
    }
  }

  private void captureException(
      final @NotNull IScopes scopes,
      final @NotNull ExceptionEventData exceptionEvent,
      final @NotNull IOtelSpanWrapper ichnoisSpan) {
    final @NotNull Throwable exception = exceptionEvent.getException();
    final Mechanism mechanism = new Mechanism();
    mechanism.setType("OpenTelemetrySpanEvent");
    mechanism.setHandled(true);
    // This is potentially the wrong Thread as it's the current thread meaning the thread where
    // the span is being ended on. This may not match the thread where the exception occurred.
    final Throwable mechanismException =
        new ExceptionMechanismException(mechanism, exception, Thread.currentThread());

    final IchnoisCoreEvent event = new IchnoisCoreEvent(mechanismException);
    event.setTimestamp(DateUtils.nanosToDate(exceptionEvent.getEpochNanos()));
    event.setLevel(IchnoisCoreLevel.ERROR);
    event.getContexts().setTrace(ichnoisSpan.getSpanContext());

    scopes.captureEvent(event);
  }

  @Override
  public boolean isEndRequired() {
    return true;
  }

  private boolean ensurePrerequisites(final @NotNull ReadableSpan otelSpan) {
    if (!hasIchnoisCoreBeenInitialized()) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Not forwarding OpenTelemetry span to IchnoisCore as IchnoisCore has not yet been initialized.");
      return false;
    }

    final @NotNull SpanContext otelSpanContext = otelSpan.getSpanContext();
    if (!otelSpanContext.isValid()) {
      scopes
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Not forwarding OpenTelemetry span to IchnoisCore as the span is invalid.");
      return false;
    }

    return true;
  }

  private boolean hasIchnoisCoreBeenInitialized() {
    return scopes.isEnabled();
  }
}
