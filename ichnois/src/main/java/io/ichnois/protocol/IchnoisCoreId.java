package io.ichnois.protocol;

import io.ichnois.ILogger;
import io.ichnois.JsonDeserializer;
import io.ichnois.JsonSerializable;
import io.ichnois.ObjectReader;
import io.ichnois.ObjectWriter;
import io.ichnois.IchnoisCoreUUID;
import io.ichnois.util.StringUtils;
import io.ichnois.util.UUIDStringUtils;
import java.io.IOException;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IchnoisCoreId implements JsonSerializable {

  public static final IchnoisCoreId EMPTY_ID =
      new IchnoisCoreId(StringUtils.PROPER_NIL_UUID.replace("-", ""));

  private volatile @Nullable String value;
  private final @Nullable UUID uuid;

  public IchnoisCoreId() {
    this((UUID) null);
  }

  public IchnoisCoreId(@Nullable UUID uuid) {
    this.uuid = uuid;
  }

  public IchnoisCoreId(final @NotNull String ichnoisIdString) {
    final @NotNull String normalized = StringUtils.normalizeUUID(ichnoisIdString);
    if (normalized.length() != 32 && normalized.length() != 36) {
      throw new IllegalArgumentException(
          "String representation of IchnoisCoreId has either 32 (UUID no dashes) "
              + "or 36 characters long (completed UUID). Received: "
              + ichnoisIdString);
    }
    this.uuid = null;
    this.value = normalized.length() == 36 ? normalized.replace("-", "") : normalized;
  }

  private @NotNull String getValue() {
    String result = value;
    if (result == null) {
      synchronized (this) {
        result = value;
        if (result == null) {
          result =
              uuid != null
                  ? normalize(UUIDStringUtils.toIchnoisCoreIdString(uuid))
                  : IchnoisCoreUUID.generateIchnoisCoreId();
          value = result;
        }
      }
    }
    return result;
  }

  @Override
  public String toString() {
    return getValue();
  }

  @Override
  public boolean equals(final @Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IchnoisCoreId ichnoisId = (IchnoisCoreId) o;
    return getValue().equals(ichnoisId.getValue());
  }

  @Override
  public int hashCode() {
    return getValue().hashCode();
  }

  private @NotNull String normalize(@NotNull String uuidString) {
    return StringUtils.normalizeUUID(uuidString).replace("-", "");
  }

  // JsonSerializable

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.value(toString());
  }

  // JsonElementDeserializer

  public static final class Deserializer implements JsonDeserializer<IchnoisCoreId> {
    @Override
    public @NotNull IchnoisCoreId deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      return new IchnoisCoreId(reader.nextString());
    }
  }
}
