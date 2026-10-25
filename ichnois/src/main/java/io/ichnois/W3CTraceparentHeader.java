package io.ichnois;

import io.ichnois.protocol.IchnoisCoreId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Represents W3C traceparent HTTP header. */
public final class W3CTraceparentHeader {
  public static final String TRACEPARENT_HEADER = "traceparent";

  private final @NotNull IchnoisCoreId traceId;
  private final @NotNull SpanId spanId;
  private final @Nullable Boolean sampled;

  public W3CTraceparentHeader(
      final @NotNull IchnoisCoreId traceId,
      final @NotNull SpanId spanId,
      final @Nullable Boolean sampled) {
    this.traceId = traceId;
    this.spanId = spanId;
    this.sampled = sampled;
  }

  public @NotNull String getName() {
    return TRACEPARENT_HEADER;
  }

  public @NotNull String getValue() {
    final String sampledFlag = sampled != null && sampled ? "01" : "00";
    return String.format("00-%s-%s-%s", traceId, spanId, sampledFlag);
  }
}
