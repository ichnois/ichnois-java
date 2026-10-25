package io.ichnois.protocol.profiling;

import static io.ichnois.DateUtils.doubleToBigDecimal;

import io.ichnois.ILogger;
import io.ichnois.JsonDeserializer;
import io.ichnois.JsonSerializable;
import io.ichnois.JsonUnknown;
import io.ichnois.ObjectReader;
import io.ichnois.ObjectWriter;
import io.ichnois.util.CollectionUtils;
import io.ichnois.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class IchnoisCoreSample implements JsonUnknown, JsonSerializable {

  private double timestamp;

  private int stackId;

  private @Nullable String threadId;

  private @Nullable Map<String, Object> unknown;

  public IchnoisCoreSample() {}

  public IchnoisCoreSample(final @NotNull IchnoisCoreSample sample) {
    this.timestamp = sample.timestamp;
    this.stackId = sample.stackId;
    this.threadId = sample.threadId;
    this.unknown = CollectionUtils.newConcurrentHashMap(sample.unknown);
  }

  public double getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(double timestamp) {
    this.timestamp = timestamp;
  }

  public int getStackId() {
    return stackId;
  }

  public void setStackId(int stackId) {
    this.stackId = stackId;
  }

  public @Nullable String getThreadId() {
    return threadId;
  }

  public void setThreadId(@Nullable String threadId) {
    this.threadId = threadId;
  }

  public static final class JsonKeys {
    public static final String TIMESTAMP = "timestamp";
    public static final String STACK_ID = "stack_id";
    public static final String THREAD_ID = "thread_id";
  }

  @Override
  public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger) throws IOException {
    writer.beginObject();

    writer.name(JsonKeys.TIMESTAMP).value(logger, doubleToBigDecimal(timestamp));
    writer.name(JsonKeys.STACK_ID).value(logger, stackId);

    if (threadId != null) {
      writer.name(JsonKeys.THREAD_ID).value(logger, threadId);
    }

    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }

    writer.endObject();
  }

  @Nullable
  @Override
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class Deserializer implements JsonDeserializer<IchnoisCoreSample> {

    @Override
    public @NotNull IchnoisCoreSample deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      IchnoisCoreSample data = new IchnoisCoreSample();
      Map<String, Object> unknown = null;

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.TIMESTAMP:
            data.timestamp = reader.nextDouble();
            break;
          case JsonKeys.STACK_ID:
            data.stackId = reader.nextInt();
            break;
          case JsonKeys.THREAD_ID:
            data.threadId = reader.nextStringOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new HashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      data.setUnknown(unknown);
      reader.endObject();
      return data;
    }
  }
}
