package io.ichnois.protocol.profiling;

import io.ichnois.ILogger;
import io.ichnois.JsonDeserializer;
import io.ichnois.JsonSerializable;
import io.ichnois.JsonUnknown;
import io.ichnois.ObjectReader;
import io.ichnois.ObjectWriter;
import io.ichnois.protocol.IchnoisCoreStackFrame;
import io.ichnois.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class IchnoisCoreProfile implements JsonUnknown, JsonSerializable {
  private @NotNull List<IchnoisCoreSample> samples = new ArrayList<>();

  private @NotNull List<List<Integer>> stacks = new ArrayList<>(); // List of frame indices

  private @NotNull List<IchnoisCoreStackFrame> frames = new ArrayList<>(); // List of stack frames

  private @NotNull Map<String, IchnoisCoreThreadMetadata> threadMetadata =
      new HashMap<>(); // Key is Thread ID (String)

  private @Nullable Map<String, Object> unknown;

  @Override
  public void serialize(@NotNull ObjectWriter writer, @NotNull ILogger logger) throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.SAMPLES).value(logger, samples);
    writer.name(JsonKeys.STACKS).value(logger, stacks);
    writer.name(JsonKeys.FRAMES).value(logger, frames);
    writer.name(JsonKeys.THREAD_METADATA).value(logger, threadMetadata);

    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key).value(logger, value);
      }
    }
    writer.endObject();
  }

  public @NotNull List<IchnoisCoreSample> getSamples() {
    return samples;
  }

  public void setSamples(@NotNull List<IchnoisCoreSample> samples) {
    this.samples = samples;
  }

  public @NotNull List<List<Integer>> getStacks() {
    return stacks;
  }

  public void setStacks(@NotNull List<List<Integer>> stacks) {
    this.stacks = stacks;
  }

  public @NotNull List<IchnoisCoreStackFrame> getFrames() {
    return frames;
  }

  public void setFrames(@NotNull List<IchnoisCoreStackFrame> frames) {
    this.frames = frames;
  }

  public @NotNull Map<String, IchnoisCoreThreadMetadata> getThreadMetadata() {
    return threadMetadata;
  }

  public void setThreadMetadata(@NotNull Map<String, IchnoisCoreThreadMetadata> threadMetadata) {
    this.threadMetadata = threadMetadata;
  }

  @Override
  public @Nullable Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class JsonKeys {
    public static final String SAMPLES = "samples";
    public static final String STACKS = "stacks";
    public static final String FRAMES = "frames";
    public static final String THREAD_METADATA = "thread_metadata";
  }

  public static final class Deserializer implements JsonDeserializer<IchnoisCoreProfile> {

    @Override
    public @NotNull IchnoisCoreProfile deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();
      IchnoisCoreProfile data = new IchnoisCoreProfile();
      Map<String, Object> unknown = null;

      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.FRAMES:
            List<IchnoisCoreStackFrame> jfrFrame =
                reader.nextListOrNull(logger, new IchnoisCoreStackFrame.Deserializer());
            if (jfrFrame != null) {
              data.frames = jfrFrame;
            }
            break;
          case JsonKeys.SAMPLES:
            List<IchnoisCoreSample> ichnoisSamples =
                reader.nextListOrNull(logger, new IchnoisCoreSample.Deserializer());
            if (ichnoisSamples != null) {
              data.samples = ichnoisSamples;
            }
            break;
          case JsonKeys.THREAD_METADATA:
            Map<String, IchnoisCoreThreadMetadata> threadMetadata =
                reader.nextMapOrNull(logger, new IchnoisCoreThreadMetadata.Deserializer());
            if (threadMetadata != null) {
              data.threadMetadata = threadMetadata;
            }
            break;
          case JsonKeys.STACKS:
            List<List<Integer>> jfrStacks =
                reader.nextOrNull(logger, new NestedIntegerListDeserializer());
            if (jfrStacks != null) {
              data.stacks = jfrStacks;
            }
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
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

  // Custom Deserializer to handle nested Integer list
  private static final class NestedIntegerListDeserializer
      implements JsonDeserializer<List<List<Integer>>> {
    @Override
    public @NotNull List<List<Integer>> deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      List<List<Integer>> result = new ArrayList<>();
      reader.beginArray();
      while (reader.hasNext()) {
        List<Integer> innerList = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
          innerList.add(reader.nextInt());
        }
        reader.endArray();
        result.add(innerList);
      }
      reader.endArray();
      return result;
    }
  }
}
