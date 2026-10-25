package io.ichnois;

import io.ichnois.exception.IchnoisCoreEnvelopeException;
import io.ichnois.protocol.SdkVersion;
import io.ichnois.protocol.IchnoisCoreId;
import io.ichnois.util.Objects;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IchnoisCoreEnvelope {

  // types: session_batch, session, event, attachment
  // an envelope cannot have 2 events, but eg multiple sessions
  private final @NotNull IchnoisCoreEnvelopeHeader header;
  private final @NotNull Iterable<IchnoisCoreEnvelopeItem> items;

  @ApiStatus.Internal
  public @NotNull Iterable<IchnoisCoreEnvelopeItem> getItems() {
    return items;
  }

  @ApiStatus.Internal
  public @NotNull IchnoisCoreEnvelopeHeader getHeader() {
    return header;
  }

  @ApiStatus.Internal
  public IchnoisCoreEnvelope(
      final @NotNull IchnoisCoreEnvelopeHeader header,
      final @NotNull Iterable<IchnoisCoreEnvelopeItem> items) {
    this.header = Objects.requireNonNull(header, "IchnoisCoreEnvelopeHeader is required.");
    this.items = Objects.requireNonNull(items, "IchnoisCoreEnvelope items are required.");
  }

  @ApiStatus.Internal
  public IchnoisCoreEnvelope(
      final @Nullable IchnoisCoreId eventId,
      final @Nullable SdkVersion sdkVersion,
      final @NotNull Iterable<IchnoisCoreEnvelopeItem> items) {
    header = new IchnoisCoreEnvelopeHeader(eventId, sdkVersion);
    this.items = Objects.requireNonNull(items, "IchnoisCoreEnvelope items are required.");
  }

  @ApiStatus.Internal
  public IchnoisCoreEnvelope(
      final @Nullable IchnoisCoreId eventId,
      final @Nullable SdkVersion sdkVersion,
      final @NotNull IchnoisCoreEnvelopeItem item) {
    Objects.requireNonNull(item, "IchnoisCoreEnvelopeItem is required.");

    header = new IchnoisCoreEnvelopeHeader(eventId, sdkVersion);
    final List<IchnoisCoreEnvelopeItem> items = new ArrayList<>(1);
    items.add(item);
    this.items = items;
  }

  @ApiStatus.Internal
  public static @NotNull IchnoisCoreEnvelope from(
      final @NotNull ISerializer serializer,
      final @NotNull Session session,
      final @Nullable SdkVersion sdkVersion)
      throws IOException {
    Objects.requireNonNull(serializer, "Serializer is required.");
    Objects.requireNonNull(session, "session is required.");

    return new IchnoisCoreEnvelope(
        null, sdkVersion, IchnoisCoreEnvelopeItem.fromSession(serializer, session));
  }

  @ApiStatus.Internal
  public static @NotNull IchnoisCoreEnvelope from(
      final @NotNull ISerializer serializer,
      final @NotNull IchnoisCoreBaseEvent event,
      final @Nullable SdkVersion sdkVersion)
      throws IOException {
    Objects.requireNonNull(serializer, "Serializer is required.");
    Objects.requireNonNull(event, "item is required.");

    return new IchnoisCoreEnvelope(
        event.getEventId(), sdkVersion, IchnoisCoreEnvelopeItem.fromEvent(serializer, event));
  }

  @ApiStatus.Internal
  public static @NotNull IchnoisCoreEnvelope from(
      final @NotNull ISerializer serializer,
      final @NotNull ProfilingTraceData profilingTraceData,
      final long maxTraceFileSize,
      final @Nullable SdkVersion sdkVersion)
      throws IchnoisCoreEnvelopeException {
    Objects.requireNonNull(serializer, "Serializer is required.");
    Objects.requireNonNull(profilingTraceData, "Profiling trace data is required.");

    return new IchnoisCoreEnvelope(
        new IchnoisCoreId(profilingTraceData.getProfileId()),
        sdkVersion,
        IchnoisCoreEnvelopeItem.fromProfilingTrace(profilingTraceData, maxTraceFileSize, serializer));
  }
}
