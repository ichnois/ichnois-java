package io.ichnois;

import io.ichnois.clientreport.ClientReport;
import io.ichnois.profilemeasurements.ProfileMeasurement;
import io.ichnois.profilemeasurements.ProfileMeasurementValue;
import io.ichnois.protocol.App;
import io.ichnois.protocol.Browser;
import io.ichnois.protocol.Contexts;
import io.ichnois.protocol.DebugImage;
import io.ichnois.protocol.DebugMeta;
import io.ichnois.protocol.Device;
import io.ichnois.protocol.Feedback;
import io.ichnois.protocol.Geo;
import io.ichnois.protocol.Gpu;
import io.ichnois.protocol.MeasurementValue;
import io.ichnois.protocol.Mechanism;
import io.ichnois.protocol.Message;
import io.ichnois.protocol.OperatingSystem;
import io.ichnois.protocol.Request;
import io.ichnois.protocol.SdkInfo;
import io.ichnois.protocol.SdkVersion;
import io.ichnois.protocol.IchnoisCoreException;
import io.ichnois.protocol.IchnoisCorePackage;
import io.ichnois.protocol.IchnoisCoreRuntime;
import io.ichnois.protocol.IchnoisCoreSpan;
import io.ichnois.protocol.IchnoisCoreStackFrame;
import io.ichnois.protocol.IchnoisCoreStackTrace;
import io.ichnois.protocol.IchnoisCoreThread;
import io.ichnois.protocol.IchnoisCoreTransaction;
import io.ichnois.protocol.User;
import io.ichnois.protocol.ViewHierarchy;
import io.ichnois.protocol.ViewHierarchyNode;
import io.ichnois.rrweb.RRWebBreadcrumbEvent;
import io.ichnois.rrweb.RRWebEventType;
import io.ichnois.rrweb.RRWebInteractionEvent;
import io.ichnois.rrweb.RRWebInteractionMoveEvent;
import io.ichnois.rrweb.RRWebMetaEvent;
import io.ichnois.rrweb.RRWebSpanEvent;
import io.ichnois.rrweb.RRWebVideoEvent;
import io.ichnois.util.Objects;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The serializer class that uses manual JSON parsing with the help of vendored GSON reader/writer
 * classes.
 */
public final class JsonSerializer implements ISerializer {

  /** the UTF-8 Charset */
  @SuppressWarnings("CharsetObjectCanBeUsed")
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  private static final int WRITER_BUFFER_SIZE = 512;

  /** the IchnoisCoreOptions */
  private final @NotNull IchnoisCoreOptions options;

  private final @NotNull Map<Class<?>, JsonDeserializer<?>> deserializersByClass;

  /**
   * All our custom deserializers need to be registered to be used with the deserializer instance. *
   */
  @SuppressWarnings("deprecation")
  public JsonSerializer(@NotNull IchnoisCoreOptions options) {
    this.options = options;

    deserializersByClass = new HashMap<>();
    deserializersByClass.put(App.class, new App.Deserializer());
    deserializersByClass.put(Breadcrumb.class, new Breadcrumb.Deserializer());
    deserializersByClass.put(Browser.class, new Browser.Deserializer());
    deserializersByClass.put(Contexts.class, new Contexts.Deserializer());
    deserializersByClass.put(DebugImage.class, new DebugImage.Deserializer());
    deserializersByClass.put(DebugMeta.class, new DebugMeta.Deserializer());
    deserializersByClass.put(Device.class, new Device.Deserializer());
    deserializersByClass.put(
        Device.DeviceOrientation.class, new Device.DeviceOrientation.Deserializer());
    deserializersByClass.put(Feedback.class, new Feedback.Deserializer());
    deserializersByClass.put(Gpu.class, new Gpu.Deserializer());
    deserializersByClass.put(
        IchnoisCoreStackTrace.InstructionAddressAdjustment.class,
        new IchnoisCoreStackTrace.InstructionAddressAdjustment.Deserializer());
    deserializersByClass.put(MeasurementValue.class, new MeasurementValue.Deserializer());
    deserializersByClass.put(Mechanism.class, new Mechanism.Deserializer());
    deserializersByClass.put(Message.class, new Message.Deserializer());
    deserializersByClass.put(OperatingSystem.class, new OperatingSystem.Deserializer());
    deserializersByClass.put(ProfileChunk.class, new ProfileChunk.Deserializer());
    deserializersByClass.put(ProfileContext.class, new ProfileContext.Deserializer());
    deserializersByClass.put(ProfilingTraceData.class, new ProfilingTraceData.Deserializer());
    deserializersByClass.put(
        ProfilingTransactionData.class, new ProfilingTransactionData.Deserializer());
    deserializersByClass.put(ProfileMeasurement.class, new ProfileMeasurement.Deserializer());
    deserializersByClass.put(
        ProfileMeasurementValue.class, new ProfileMeasurementValue.Deserializer());
    deserializersByClass.put(Request.class, new Request.Deserializer());
    deserializersByClass.put(ReplayRecording.class, new ReplayRecording.Deserializer());
    deserializersByClass.put(RRWebBreadcrumbEvent.class, new RRWebBreadcrumbEvent.Deserializer());
    deserializersByClass.put(RRWebEventType.class, new RRWebEventType.Deserializer());
    deserializersByClass.put(RRWebInteractionEvent.class, new RRWebInteractionEvent.Deserializer());
    deserializersByClass.put(
        RRWebInteractionMoveEvent.class, new RRWebInteractionMoveEvent.Deserializer());
    deserializersByClass.put(RRWebMetaEvent.class, new RRWebMetaEvent.Deserializer());
    deserializersByClass.put(RRWebSpanEvent.class, new RRWebSpanEvent.Deserializer());
    deserializersByClass.put(RRWebVideoEvent.class, new RRWebVideoEvent.Deserializer());
    deserializersByClass.put(SdkInfo.class, new SdkInfo.Deserializer());
    deserializersByClass.put(SdkVersion.class, new SdkVersion.Deserializer());
    deserializersByClass.put(IchnoisCoreEnvelopeHeader.class, new IchnoisCoreEnvelopeHeader.Deserializer());
    deserializersByClass.put(
        IchnoisCoreEnvelopeItemHeader.class, new IchnoisCoreEnvelopeItemHeader.Deserializer());
    deserializersByClass.put(IchnoisCoreEvent.class, new IchnoisCoreEvent.Deserializer());
    deserializersByClass.put(IchnoisCoreException.class, new IchnoisCoreException.Deserializer());
    deserializersByClass.put(IchnoisCoreItemType.class, new IchnoisCoreItemType.Deserializer());
    deserializersByClass.put(IchnoisCoreLevel.class, new IchnoisCoreLevel.Deserializer());
    deserializersByClass.put(IchnoisCoreLockReason.class, new IchnoisCoreLockReason.Deserializer());
    deserializersByClass.put(IchnoisCoreLogEvents.class, new IchnoisCoreLogEvents.Deserializer());
    deserializersByClass.put(IchnoisCoreMetricsEvents.class, new IchnoisCoreMetricsEvents.Deserializer());
    deserializersByClass.put(IchnoisCorePackage.class, new IchnoisCorePackage.Deserializer());
    deserializersByClass.put(IchnoisCoreRuntime.class, new IchnoisCoreRuntime.Deserializer());
    deserializersByClass.put(IchnoisCoreReplayEvent.class, new IchnoisCoreReplayEvent.Deserializer());
    deserializersByClass.put(IchnoisCoreSpan.class, new IchnoisCoreSpan.Deserializer());
    deserializersByClass.put(IchnoisCoreStackFrame.class, new IchnoisCoreStackFrame.Deserializer());
    deserializersByClass.put(IchnoisCoreStackTrace.class, new IchnoisCoreStackTrace.Deserializer());
    deserializersByClass.put(
        IchnoisCoreAppStartProfilingOptions.class, new IchnoisCoreAppStartProfilingOptions.Deserializer());
    deserializersByClass.put(IchnoisCoreThread.class, new IchnoisCoreThread.Deserializer());
    deserializersByClass.put(IchnoisCoreTransaction.class, new IchnoisCoreTransaction.Deserializer());
    deserializersByClass.put(Session.class, new Session.Deserializer());
    deserializersByClass.put(SpanContext.class, new SpanContext.Deserializer());
    deserializersByClass.put(SpanId.class, new SpanId.Deserializer());
    deserializersByClass.put(SpanStatus.class, new SpanStatus.Deserializer());
    deserializersByClass.put(User.class, new User.Deserializer());
    deserializersByClass.put(Geo.class, new Geo.Deserializer());
    deserializersByClass.put(UserFeedback.class, new UserFeedback.Deserializer());
    deserializersByClass.put(ClientReport.class, new ClientReport.Deserializer());
    deserializersByClass.put(ViewHierarchyNode.class, new ViewHierarchyNode.Deserializer());
    deserializersByClass.put(ViewHierarchy.class, new ViewHierarchy.Deserializer());
  }

  // Deserialize

  @SuppressWarnings("unchecked")
  @Override
  public <T, R> @Nullable T deserializeCollection(
      @NotNull Reader reader,
      @NotNull Class<T> clazz,
      @Nullable JsonDeserializer<R> elementDeserializer) {
    try (JsonObjectReader jsonObjectReader = new JsonObjectReader(reader)) {
      if (Collection.class.isAssignableFrom(clazz)) {
        if (elementDeserializer == null) {
          // if the object has no known deserializer we do best effort and deserialize it as map
          return (T) jsonObjectReader.nextObjectOrNull();
        }

        return (T) jsonObjectReader.nextListOrNull(options.getLogger(), elementDeserializer);
      } else {
        return (T) jsonObjectReader.nextObjectOrNull();
      }
    } catch (Throwable e) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, "Error when deserializing", e);
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> @Nullable T deserialize(@NotNull Reader reader, @NotNull Class<T> clazz) {
    try (JsonObjectReader jsonObjectReader = new JsonObjectReader(reader)) {
      JsonDeserializer<?> deserializer = deserializersByClass.get(clazz);
      if (deserializer != null) {
        Object object = deserializer.deserialize(jsonObjectReader, options.getLogger());
        return clazz.cast(object);
      } else if (isKnownPrimitive(clazz)) {
        return (T) jsonObjectReader.nextObjectOrNull();
      } else {
        return null; // No way to deserialize objects we don't know about.
      }
    } catch (Exception e) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, "Error when deserializing", e);
      return null;
    }
  }

  @Override
  public @Nullable IchnoisCoreEnvelope deserializeEnvelope(@NotNull InputStream inputStream) {
    Objects.requireNonNull(inputStream, "The InputStream object is required.");
    try {
      return options.getEnvelopeReader().read(inputStream);
    } catch (IOException e) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, "Error deserializing envelope.", e);
      return null;
    }
  }

  // Serialize

  @Override
  public <T> void serialize(@NotNull T entity, @NotNull Writer writer) throws IOException {
    Objects.requireNonNull(entity, "The entity is required.");
    Objects.requireNonNull(writer, "The Writer object is required.");

    if (options.getLogger().isEnabled(IchnoisCoreLevel.DEBUG)) {
      String serialized = serializeToString(entity, options.isEnablePrettySerializationOutput());
      options.getLogger().log(IchnoisCoreLevel.DEBUG, "Serializing object: %s", serialized);
    }
    JsonObjectWriter jsonObjectWriter = new JsonObjectWriter(writer, options.getMaxDepth());
    jsonObjectWriter.value(options.getLogger(), entity);
    writer.flush();
  }

  /**
   * Serializes an envelope to an OutputStream
   *
   * @param envelope the envelope
   * @param outputStream will not be closed automatically
   * @throws Exception an exception
   */
  @Override
  public void serialize(@NotNull IchnoisCoreEnvelope envelope, @NotNull OutputStream outputStream)
      throws Exception {
    Objects.requireNonNull(envelope, "The IchnoisCoreEnvelope object is required.");
    Objects.requireNonNull(outputStream, "The Stream object is required.");

    // we do not want to close these as we would also close the stream that was passed in
    final BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);
    final Writer writer =
        new BufferedWriter(new OutputStreamWriter(bufferedOutputStream, UTF_8), WRITER_BUFFER_SIZE);

    try {
      envelope
          .getHeader()
          .serialize(new JsonObjectWriter(writer, options.getMaxDepth()), options.getLogger());
      writer.write("\n");

      for (final IchnoisCoreEnvelopeItem item : envelope.getItems()) {
        try {
          // When this throws we don't write anything and continue with the next item.
          final byte[] data = item.getData();

          item.getHeader()
              .serialize(new JsonObjectWriter(writer, options.getMaxDepth()), options.getLogger());
          writer.write("\n");
          writer.flush();

          outputStream.write(data);

          writer.write("\n");
        } catch (Exception exception) {
          options
              .getLogger()
              .log(IchnoisCoreLevel.ERROR, "Failed to create envelope item. Dropping it.", exception);
        }
      }
    } finally {
      writer.flush();
    }
  }

  @Override
  public @NotNull String serialize(@NotNull Map<String, Object> data) throws Exception {
    return serializeToString(data, false);
  }

  // Helper

  private @NotNull String serializeToString(Object object, boolean pretty) throws IOException {
    StringWriter stringWriter = new StringWriter();
    JsonObjectWriter jsonObjectWriter = new JsonObjectWriter(stringWriter, options.getMaxDepth());
    if (pretty) {
      jsonObjectWriter.setIndent("\t");
    }
    jsonObjectWriter.value(options.getLogger(), object);
    return stringWriter.toString();
  }

  private <T> boolean isKnownPrimitive(final @NotNull Class<T> clazz) {
    return clazz.isArray()
        || Collection.class.isAssignableFrom(clazz)
        || String.class.isAssignableFrom(clazz)
        || Map.class.isAssignableFrom(clazz);
  }
}
