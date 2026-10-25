package io.ichnois.logger;

import io.ichnois.IchnoisCoreAttributes;
import io.ichnois.IchnoisCoreDate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IchnoisCoreLogParameters {

  private @Nullable IchnoisCoreDate timestamp;
  private @Nullable IchnoisCoreAttributes attributes;
  private @NotNull String origin = "manual";

  public @Nullable IchnoisCoreDate getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(final @Nullable IchnoisCoreDate timestamp) {
    this.timestamp = timestamp;
  }

  public @Nullable IchnoisCoreAttributes getAttributes() {
    return attributes;
  }

  public void setAttributes(final @Nullable IchnoisCoreAttributes attributes) {
    this.attributes = attributes;
  }

  public @NotNull String getOrigin() {
    return origin;
  }

  public void setOrigin(final @NotNull String origin) {
    this.origin = origin;
  }

  public static @NotNull IchnoisCoreLogParameters create(
      final @Nullable IchnoisCoreDate timestamp, final @Nullable IchnoisCoreAttributes attributes) {
    final @NotNull IchnoisCoreLogParameters params = new IchnoisCoreLogParameters();

    params.setTimestamp(timestamp);
    params.setAttributes(attributes);

    return params;
  }

  public static @NotNull IchnoisCoreLogParameters create(final @Nullable IchnoisCoreAttributes attributes) {
    return create(null, attributes);
  }
}
