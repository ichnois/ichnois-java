package io.ichnois;

import io.ichnois.protocol.Contexts;
import io.ichnois.protocol.IchnoisCoreId;
import io.ichnois.protocol.TransactionNameSource;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NoOpTransaction implements ITransaction {

  private static final NoOpTransaction instance = new NoOpTransaction();

  private NoOpTransaction() {}

  public static NoOpTransaction getInstance() {
    return instance;
  }

  @Override
  public void setName(@NotNull String name) {}

  @ApiStatus.Internal
  @Override
  public void setName(@NotNull String name, @NotNull TransactionNameSource transactionNameSource) {}

  @Override
  public @NotNull String getName() {
    return "";
  }

  @Override
  public @NotNull TransactionNameSource getTransactionNameSource() {
    return TransactionNameSource.CUSTOM;
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
  public @Nullable String getDescription() {
    return null;
  }

  @Override
  public @NotNull List<Span> getSpans() {
    return Collections.emptyList();
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation, @Nullable String description, @Nullable IchnoisCoreDate timestamp) {
    return NoOpSpan.getInstance();
  }

  @Override
  public @Nullable ISpan getLatestActiveSpan() {
    return null;
  }

  @Override
  public @NotNull IchnoisCoreId getEventId() {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public @NotNull IIchnoisCoreLifecycleToken makeCurrent() {
    return NoOpScopesLifecycleToken.getInstance();
  }

  @Override
  public void scheduleFinish() {}

  @Override
  public void forceFinish(
      @NotNull SpanStatus status, boolean dropIfNoChildren, @Nullable Hint hint) {}

  @Override
  public void finish(
      @Nullable SpanStatus status,
      @Nullable IchnoisCoreDate timestamp,
      boolean dropIfNoChildren,
      @Nullable Hint hint) {}

  @Override
  public boolean isFinished() {
    return true;
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
  public @Nullable Boolean isSampled() {
    return null;
  }

  @Override
  public @Nullable Boolean isProfileSampled() {
    return null;
  }

  @Override
  public @Nullable TracesSamplingDecision getSamplingDecision() {
    return null;
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

  @ApiStatus.Internal
  @Override
  public void setContext(@Nullable String key, @Nullable Object context) {}

  @ApiStatus.Internal
  @Override
  public @NotNull Contexts getContexts() {
    return new Contexts();
  }

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
  public void addFeatureFlag(final @Nullable String flag, final @Nullable Boolean result) {}
}
