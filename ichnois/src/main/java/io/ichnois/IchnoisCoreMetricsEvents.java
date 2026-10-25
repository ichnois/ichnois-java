package io.ichnois;

import io.ichnois.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IchnoisCoreMetricsEvents implements JsonUnknown, JsonSerializable {

  private @NotNull List<IchnoisCoreMetricsEvent> items;
  private @Nullable Map<String, Object> unknown;

  public IchnoisCoreMetricsEvents(final @NotNull List<IchnoisCoreMetricsEvent> items) {
    this.items = items;
  }

  public @NotNull List<IchnoisCoreMetricsEvent> getItems() {
    return items;
  }

  // region json
  public static final class JsonKeys {
    public static final String ITEMS = "items";
  }

  @Override
  @SuppressWarnings("JdkObsolete")
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();

    writer.name(JsonKeys.ITEMS).value(logger, items);

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
  public void setUnknown(final @Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class Deserializer implements JsonDeserializer<IchnoisCoreMetricsEvents> {

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull IchnoisCoreMetricsEvents deserialize(
        final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws Exception {
      @Nullable Map<String, Object> unknown = null;
      @Nullable List<IchnoisCoreMetricsEvent> items = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.ITEMS:
            items = reader.nextListOrNull(logger, new IchnoisCoreMetricsEvent.Deserializer());
            break;
          default:
            if (unknown == null) {
              unknown = new HashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      reader.endObject();

      if (items == null) {
        final String message = "Missing required field \"" + JsonKeys.ITEMS + "\"";
        final Exception exception = new IllegalStateException(message);
        logger.log(IchnoisCoreLevel.ERROR, message, exception);
        throw exception;
      }

      final IchnoisCoreMetricsEvents metricsEvent = new IchnoisCoreMetricsEvents(items);

      metricsEvent.setUnknown(unknown);

      return metricsEvent;
    }
  }
  // endregion json
}
