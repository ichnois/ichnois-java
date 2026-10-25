package io.ichnois;

import io.ichnois.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IchnoisCoreLogEventAttributeValue implements JsonUnknown, JsonSerializable {

  private @NotNull String type;
  private @Nullable Object value;
  private @Nullable Map<String, Object> unknown;

  public IchnoisCoreLogEventAttributeValue(final @NotNull String type, final @Nullable Object value) {
    this.type = type;
    if (value != null && type.equals("string")) {
      this.value = value.toString();
    } else {
      this.value = value;
    }
  }

  public IchnoisCoreLogEventAttributeValue(
      final @NotNull IchnoisCoreAttributeType type, final @Nullable Object value) {
    this(type.apiName(), value);
  }

  /**
   * Creates a {@link IchnoisCoreLogEventAttributeValue} from a {@link IchnoisCoreAttribute}, inferring the
   * type if not explicitly set.
   *
   * @param attribute the attribute
   * @return the attribute value
   */
  public static @NotNull IchnoisCoreLogEventAttributeValue fromAttribute(
      final @NotNull IchnoisCoreAttribute attribute) {
    final @Nullable Object value = attribute.getValue();
    final @NotNull IchnoisCoreAttributeType type =
        attribute.getType() == null ? IchnoisCoreAttributeType.inferFrom(value) : attribute.getType();
    return new IchnoisCoreLogEventAttributeValue(type, value);
  }

  public @NotNull String getType() {
    return type;
  }

  public @Nullable Object getValue() {
    return value;
  }

  // region json
  public static final class JsonKeys {
    public static final String TYPE = "type";
    public static final String VALUE = "value";
  }

  @Override
  @SuppressWarnings("JdkObsolete")
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(JsonKeys.TYPE).value(logger, type);
    writer.name(JsonKeys.VALUE).value(logger, value);

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

  public static final class Deserializer implements JsonDeserializer<IchnoisCoreLogEventAttributeValue> {

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull IchnoisCoreLogEventAttributeValue deserialize(
        final @NotNull ObjectReader reader, final @NotNull ILogger logger) throws Exception {
      @Nullable Map<String, Object> unknown = null;
      @Nullable String type = null;
      @Nullable Object value = null;

      reader.beginObject();
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case JsonKeys.TYPE:
            type = reader.nextStringOrNull();
            break;
          case JsonKeys.VALUE:
            value = reader.nextObjectOrNull();
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

      if (type == null) {
        final String message = "Missing required field \"" + JsonKeys.TYPE + "\"";
        final Exception exception = new IllegalStateException(message);
        logger.log(IchnoisCoreLevel.ERROR, message, exception);
        throw exception;
      }

      final IchnoisCoreLogEventAttributeValue logEvent = new IchnoisCoreLogEventAttributeValue(type, value);

      logEvent.setUnknown(unknown);

      return logEvent;
    }
  }
  // endregion json
}
