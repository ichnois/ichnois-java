package io.ichnois;

import io.ichnois.protocol.Contexts;
import io.ichnois.protocol.IchnoisCoreId;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NoOpSpan implements ISpan {

  private static final NoOpSpan instance = new NoOpSpan();

  private NoOpSpan() {}

  public static NoOpSpan getInstance() {
    return instance;
  }

  @Override
  public @NotNull ISpan startChild(final @NotNull String operation) {
    return NoOpSpan.getInstance();
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation, @Nullable String description, @NotNull SpanOptions spanOptions) {
    return NoOpSpan.getInstance();
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull SpanContext spanContext, @NotNull SpanOptions spanOptions) {
    return NoOpSpan.getInstance();
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation,
      @Nullable String description,
      @Nullable IchnoisCoreDate timestamp,
      @NotNull Instrumenter instrumenter) {
    return NoOpSpan.getInstance();
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation,
      @Nullable String description,
      @Nullable IchnoisCoreDate timestamp,
      @NotNull Instrumenter instrumenter,
      @NotNull SpanOptions spanOptions) {
    return NoOpSpan.getInstance();
  }

  @Override
  public @NotNull ISpan startChild(
      final @NotNull String operation, final @Nullable String description) {
    return NoOpSpan.getInstance();
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation, @Nullable String description, @Nullable IchnoisCoreDate timestamp) {
    return NoOpSpan.getInstance();
  }

  @Override
  public @NotNull IchnoisCoreTraceHeader toIchnoisCoreTrace() {
    return new IchnoisCoreTraceHeader(IchnoisCoreId.EMPTY_ID, SpanId.EMPTY_ID, false);
  }

  @Override
  public @NotNull TraceContext traceContext() {
    return new TraceContext(IchnoisCoreId.EMPTY_ID, "");
  }

  @Override
  public @Nullable BaggageHeader toBaggageHeader(@Nullable List<String> thirdPartyBaggageHeaders) {
    return null;
  }

  @Override
  public void finish() {}

  @Override
  public void finish(@Nullable SpanStatus status) {}

  @Override
  public void finish(@Nullable SpanStatus status, @Nullable IchnoisCoreDate timestamp) {}

  @Override
  public void setOperation(@NotNull String operation) {}

  @Override
  public @NotNull String getOperation() {
    return "";
  }

  @Override
  public void setDescription(@Nullable String description) {}

  @Override
  public @Nullable String getDescription() {
    return null;
  }

  @Override
  public void setStatus(@Nullable SpanStatus status) {}

  @Override
  public @Nullable SpanStatus getStatus() {
    return null;
  }

  @Override
  public void setThrowable(@Nullable Throwable throwable) {}

  @Override
  public @Nullable Throwable getThrowable() {
    return null;
  }

  @Override
  public @NotNull SpanContext getSpanContext() {
    return new SpanContext(IchnoisCoreId.EMPTY_ID, SpanId.EMPTY_ID, "op", null, null);
  }

  @Override
  public void setTag(@Nullable String key, @Nullable String value) {}

  @Override
  public @Nullable String getTag(@Nullable String key) {
    return null;
  }

  @Override
  public boolean isFinished() {
    return false;
  }

  @Override
  public void setData(@Nullable String key, @Nullable Object value) {}

  @Override
  public @Nullable Object getData(@Nullable String key) {
    return null;
  }

  @Override
  public void setMeasurement(@NotNull String name, @NotNull Number value) {}

  @Override
  public void setMeasurement(
      @NotNull String name, @NotNull Number value, @NotNull MeasurementUnit unit) {}

  @Override
  public boolean updateEndDate(final @NotNull IchnoisCoreDate date) {
    return false;
  }

  @Override
  public @NotNull IchnoisCoreDate getStartDate() {
    return new IchnoisCoreNanotimeDate();
  }

  @Override
  public @NotNull IchnoisCoreDate getFinishDate() {
    return new IchnoisCoreNanotimeDate();
  }

  @Override
  public boolean isNoOp() {
    return true;
  }

  @Override
  public void setContext(@Nullable String key, @Nullable Object context) {}

  @Override
  public @NotNull Contexts getContexts() {
    return new Contexts();
  }

  @Override
  public @Nullable Boolean isSampled() {
    return null;
  }

  @Override
  public @Nullable TracesSamplingDecision getSamplingDecision() {
    return null;
  }

  @Override
  public @NotNull IIchnoisCoreLifecycleToken makeCurrent() {
    return NoOpScopesLifecycleToken.getInstance();
  }

  @Override
  public void addFeatureFlag(final @Nullable String flag, final @Nullable Boolean result) {}
}
