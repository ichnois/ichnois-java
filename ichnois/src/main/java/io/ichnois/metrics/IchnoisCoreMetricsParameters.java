package io.ichnois.metrics;

import io.ichnois.Hint;
import io.ichnois.IchnoisCoreAttributes;
import io.ichnois.IchnoisCoreDate;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IchnoisCoreMetricsParameters {

  private @Nullable IchnoisCoreDate timestamp;
  private @Nullable IchnoisCoreAttributes attributes;
  private @NotNull String origin = "manual";

  private @Nullable Hint hint = null;

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

  public @Nullable Hint getHint() {
    return hint;
  }

  public void setHint(final @Nullable Hint hint) {
    this.hint = hint;
  }

  public static @NotNull IchnoisCoreMetricsParameters create(
      final @Nullable IchnoisCoreDate timestamp, final @Nullable IchnoisCoreAttributes attributes) {
    final @NotNull IchnoisCoreMetricsParameters params = new IchnoisCoreMetricsParameters();

    params.setTimestamp(timestamp);
    params.setAttributes(attributes);

    return params;
  }

  public static @NotNull IchnoisCoreMetricsParameters create(
      final @Nullable IchnoisCoreAttributes attributes) {
    return create(null, attributes);
  }

  /**
   * A shortcut for IchnoisCoreMetricsParameters.create(IchnoisCoreAttributes.fromMap())
   *
   * @param attributes a map of attributes
   * @return parameters
   */
  public static @NotNull IchnoisCoreMetricsParameters create(
      final @Nullable Map<String, Object> attributes) {
    return create(null, IchnoisCoreAttributes.fromMap(attributes));
  }
}
