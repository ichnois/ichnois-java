package io.ichnois.opentelemetry;

import io.ichnois.Baggage;
import io.ichnois.IchnoisCoreTraceHeader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Deprecated
@ApiStatus.Internal
public final class TraceData {

  private final @NotNull String traceId;
  private final @NotNull String spanId;
  private final @Nullable String parentSpanId;
  private final @Nullable IchnoisCoreTraceHeader ichnoisTraceHeader;
  private final @Nullable Baggage baggage;

  public TraceData(
      @NotNull final String traceId,
      @NotNull final String spanId,
      @Nullable final String parentSpanId,
      @Nullable final IchnoisCoreTraceHeader ichnoisTraceHeader,
      @Nullable final Baggage baggage) {
    this.traceId = traceId;
    this.spanId = spanId;
    this.parentSpanId = parentSpanId;
    this.ichnoisTraceHeader = ichnoisTraceHeader;
    this.baggage = baggage;
  }

  public @NotNull String getTraceId() {
    return traceId;
  }

  public @NotNull String getSpanId() {
    return spanId;
  }

  public @Nullable String getParentSpanId() {
    return parentSpanId;
  }

  public @Nullable IchnoisCoreTraceHeader getIchnoisCoreTraceHeader() {
    return ichnoisTraceHeader;
  }

  public @Nullable Baggage getBaggage() {
    return baggage;
  }
}
