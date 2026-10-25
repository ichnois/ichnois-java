package io.ichnois.protocol.profiling;

import io.ichnois.ILogger;
import io.ichnois.JsonDeserializer;
import io.ichnois.JsonSerializable;
import io.ichnois.JsonUnknown;
import io.ichnois.ObjectReader;
import io.ichnois.ObjectWriter;
import io.ichnois.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class IchnoisCoreThreadMetadata implements JsonUnknown, JsonSerializable {
  private @Nullable String name;

  private int priority;

  private @Nullable Map<String, Object> unknown;

  public @Nullable String getName() {
    return name;
  }

  public void setName(@Nullable String name) {
    this.name = name;
  }

  public int getPriority() {
    return priority;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }

  public static final class JsonKeys {
    public static final String NAME = "name";
    public static final String PRIORITY = "priority";
  }

  @Override
  public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger) throws IOException {
    writer.beginObject();
    if (name != null) {
      writer.name(JsonKeys.NAME).value(logger, name);
    }
    writer.name(JsonKeys.PRIORITY).value(logger, priority);

    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }

    writer.endObject();
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class Deserializer implements JsonDeserializer<IchnoisCoreThreadMetadata> {

    @Override
    public @NotNull IchnoisCoreThreadMetadata deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      IchnoisCoreThreadMetadata data = new IchnoisCoreThreadMetadata();
      Map<String, Object> unknown = null;

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.NAME:
            data.name = reader.nextStringOrNull();
            break;
          case JsonKeys.PRIORITY:
            data.priority = reader.nextInt();
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
