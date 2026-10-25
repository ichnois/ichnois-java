package io.ichnois;

import java.util.Collection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IchnoisCoreAttribute {

  private final @NotNull String name;
  private final @Nullable IchnoisCoreAttributeType type;
  private final @Nullable Object value;

  private IchnoisCoreAttribute(
      final @NotNull String name,
      final @Nullable IchnoisCoreAttributeType type,
      final @Nullable Object value) {
    this.name = name;
    this.type = type;
    this.value = value;
  }

  public @NotNull String getName() {
    return name;
  }

  public @Nullable IchnoisCoreAttributeType getType() {
    return type;
  }

  public @Nullable Object getValue() {
    return value;
  }

  public static @NotNull IchnoisCoreAttribute named(
      final @NotNull String name, final @Nullable Object value) {
    return new IchnoisCoreAttribute(name, null, value);
  }

  public static @NotNull IchnoisCoreAttribute booleanAttribute(
      final @NotNull String name, final @Nullable Boolean value) {
    return new IchnoisCoreAttribute(name, IchnoisCoreAttributeType.BOOLEAN, value);
  }

  public static @NotNull IchnoisCoreAttribute integerAttribute(
      final @NotNull String name, final @Nullable Integer value) {
    return new IchnoisCoreAttribute(name, IchnoisCoreAttributeType.INTEGER, value);
  }

  public static @NotNull IchnoisCoreAttribute doubleAttribute(
      final @NotNull String name, final @Nullable Double value) {
    return new IchnoisCoreAttribute(name, IchnoisCoreAttributeType.DOUBLE, value);
  }

  public static @NotNull IchnoisCoreAttribute stringAttribute(
      final @NotNull String name, final @Nullable String value) {
    return new IchnoisCoreAttribute(name, IchnoisCoreAttributeType.STRING, value);
  }

  public static @NotNull IchnoisCoreAttribute arrayAttribute(
      final @NotNull String name, final @Nullable Collection<?> value) {
    return new IchnoisCoreAttribute(name, IchnoisCoreAttributeType.ARRAY, value);
  }

  public static @NotNull IchnoisCoreAttribute arrayAttribute(
      final @NotNull String name, final @Nullable Object[] value) {
    return new IchnoisCoreAttribute(name, IchnoisCoreAttributeType.ARRAY, value);
  }
}
