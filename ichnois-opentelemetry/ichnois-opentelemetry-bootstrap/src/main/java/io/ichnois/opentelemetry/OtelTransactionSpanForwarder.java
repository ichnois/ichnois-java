package io.ichnois.opentelemetry;

import static io.ichnois.TransactionContext.DEFAULT_TRANSACTION_NAME;

import io.ichnois.BaggageHeader;
import io.ichnois.Hint;
import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.ISpan;
import io.ichnois.ITransaction;
import io.ichnois.Instrumenter;
import io.ichnois.MeasurementUnit;
import io.ichnois.IchnoisCoreDate;
import io.ichnois.IchnoisCoreTraceHeader;
import io.ichnois.SpanContext;
import io.ichnois.SpanOptions;
import io.ichnois.SpanStatus;
import io.ichnois.TraceContext;
import io.ichnois.TracesSamplingDecision;
import io.ichnois.protocol.Contexts;
import io.ichnois.protocol.IchnoisCoreId;
import io.ichnois.protocol.TransactionNameSource;
import io.ichnois.util.Objects;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class OtelTransactionSpanForwarder implements ITransaction {

  private final @NotNull IOtelSpanWrapper rootSpan;

  public OtelTransactionSpanForwarder(final @NotNull IOtelSpanWrapper rootSpan) {
    this.rootSpan = Objects.requireNonNull(rootSpan, "root span is required");
  }

  @Override
  public @NotNull ISpan startChild(@NotNull String operation) {
    return rootSpan.startChild(operation);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation, @Nullable String description, @NotNull SpanOptions spanOptions) {
    return rootSpan.startChild(operation, description, spanOptions);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull SpanContext spanContext, @NotNull SpanOptions spanOptions) {
    return rootSpan.startChild(spanContext, spanOptions);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation,
      @Nullable String description,
      @Nullable IchnoisCoreDate timestamp,
      @NotNull Instrumenter instrumenter) {
    return rootSpan.startChild(operation, description, timestamp, instrumenter);
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation,
      @Nullable String description,
      @Nullable IchnoisCoreDate timestamp,
      @NotNull Instrumenter instrumenter,
      @NotNull SpanOptions spanOptions) {
    return rootSpan.startChild(operation, description, timestamp, instrumenter, spanOptions);
  }

  @Override
  public @NotNull ISpan startChild(@NotNull String operation, @Nullable String description) {
    return rootSpan.startChild(operation, description);
  }

  @Override
  public @NotNull IchnoisCoreTraceHeader toIchnoisCoreTrace() {
    return rootSpan.toIchnoisCoreTrace();
  }

  @Override
  public @Nullable TraceContext traceContext() {
    return rootSpan.traceContext();
  }

  @Override
  public @Nullable BaggageHeader toBaggageHeader(@Nullable List<String> thirdPartyBaggageHeaders) {
    return rootSpan.toBaggageHeader(thirdPartyBaggageHeaders);
  }

  @Override
  public void finish() {
    rootSpan.finish();
  }

  @Override
  public void finish(@Nullable SpanStatus status) {
    rootSpan.finish(status);
  }

  @Override
  public void finish(@Nullable SpanStatus status, @Nullable IchnoisCoreDate timestamp) {
    rootSpan.finish(status, timestamp);
  }

  @Override
  public void setOperation(@NotNull String operation) {
    rootSpan.startChild(operation);
  }

  @Override
  public @NotNull String getOperation() {
    return rootSpan.getOperation();
  }

  @Override
  public void setDescription(@Nullable String description) {
    rootSpan.setDescription(description);
  }

  @Override
  public @Nullable String getDescription() {
    return rootSpan.getDescription();
  }

  @Override
  public void setStatus(@Nullable SpanStatus status) {
    rootSpan.setStatus(status);
  }

  @Override
  public @Nullable SpanStatus getStatus() {
    return rootSpan.getStatus();
  }

  @Override
  public void setThrowable(@Nullable Throwable throwable) {
    rootSpan.setThrowable(throwable);
  }

  @Override
  public @Nullable Throwable getThrowable() {
    return rootSpan.getThrowable();
  }

  @Override
  public @NotNull SpanContext getSpanContext() {
    return rootSpan.getSpanContext();
  }

  @Override
  public void setTag(@Nullable String key, @Nullable String value) {
    rootSpan.setTag(key, value);
  }

  @Override
  public @Nullable String getTag(@Nullable String key) {
    return rootSpan.getTag(key);
  }

  @Override
  public boolean isFinished() {
    return rootSpan.isFinished();
  }

  @Override
  public void setData(@Nullable String key, @Nullable Object value) {
    rootSpan.setData(key, value);
  }

  @Override
  public @Nullable Object getData(@Nullable String key) {
    return rootSpan.getData(key);
  }

  @Override
  public void setMeasurement(@NotNull String name, @NotNull Number value) {
    rootSpan.setMeasurement(name, value);
  }

  @Override
  public void setMeasurement(
      @NotNull String name, @NotNull Number value, @NotNull MeasurementUnit unit) {
    rootSpan.setMeasurement(name, value, unit);
  }

  @Override
  public boolean updateEndDate(@NotNull IchnoisCoreDate date) {
    return rootSpan.updateEndDate(date);
  }

  @Override
  public @NotNull IchnoisCoreDate getStartDate() {
    return rootSpan.getStartDate();
  }

  @Override
  public @Nullable IchnoisCoreDate getFinishDate() {
    return rootSpan.getFinishDate();
  }

  @Override
  public boolean isNoOp() {
    return rootSpan.isNoOp();
  }

  @Override
  public @NotNull TransactionNameSource getTransactionNameSource() {
    final @Nullable TransactionNameSource nameSource = rootSpan.getTransactionNameSource();
    if (nameSource == null) {
      return TransactionNameSource.CUSTOM;
    }
    return nameSource;
  }

  @Override
  public @NotNull List<io.ichnois.Span> getSpans() {
    return new ArrayList<>();
  }

  @Override
  public @NotNull ISpan startChild(
      @NotNull String operation, @Nullable String description, @Nullable IchnoisCoreDate timestamp) {
    return rootSpan.startChild(operation, description, timestamp, Instrumenter.ICHNOIS);
  }

  @Override
  public @Nullable Boolean isSampled() {
    return rootSpan.isSampled();
  }

  @Override
  public @Nullable Boolean isProfileSampled() {
    return rootSpan.isProfileSampled();
  }

  @Override
  public @Nullable TracesSamplingDecision getSamplingDecision() {
    return rootSpan.getSamplingDecision();
  }

  @Override
  public @Nullable ISpan getLatestActiveSpan() {
    return rootSpan;
  }

  @Override
  public @NotNull IchnoisCoreId getEventId() {
    return new IchnoisCoreId();
  }

  @ApiStatus.Internal
  @Override
  public @NotNull IIchnoisCoreLifecycleToken makeCurrent() {
    return rootSpan.makeCurrent();
  }

  @Override
  public void scheduleFinish() {}

  @Override
  public void forceFinish(
      @NotNull SpanStatus status, boolean dropIfNoChildren, @Nullable Hint hint) {
    rootSpan.finish(status);
  }

  @Override
  public void finish(
      @Nullable SpanStatus status,
      @Nullable IchnoisCoreDate timestamp,
      boolean dropIfNoChildren,
      @Nullable Hint hint) {
    rootSpan.finish(status, timestamp);
  }

  @Override
  public void setContext(@Nullable String key, @Nullable Object context) {
    // thoughts:
    // - span would have to save it on global storage too since we can't add complex data to otel
    // span
    // - with span ingestion there isn't a transaction anymore, so if we still need Contexts it
    // should go on the (root) span
    rootSpan.setContext(key, context);
  }

  @Override
  public @NotNull Contexts getContexts() {
    return rootSpan.getContexts();
  }

  @Override
  public void setName(@NotNull String name) {
    rootSpan.setTransactionName(name);
  }

  @Override
  public void setName(@NotNull String name, @NotNull TransactionNameSource nameSource) {
    rootSpan.setTransactionName(name, nameSource);
  }

  @Override
  public @NotNull String getName() {
    final @Nullable String name = rootSpan.getTransactionName();
    if (name == null) {
      return DEFAULT_TRANSACTION_NAME;
    }
    return name;
  }

  @Override
  public void addFeatureFlag(final @Nullable String flag, final @Nullable Boolean result) {
    rootSpan.addFeatureFlag(flag, result);
  }
}
