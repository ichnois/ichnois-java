package io.ichnois;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class IchnoisCoreAttributes {

  private final @NotNull Map<String, IchnoisCoreAttribute> attributes;

  private IchnoisCoreAttributes(final @NotNull Map<String, IchnoisCoreAttribute> attributes) {
    this.attributes = attributes;
  }

  public void add(final @Nullable IchnoisCoreAttribute attribute) {
    if (attribute == null) {
      return;
    }
    attributes.put(attribute.getName(), attribute);
  }

  public @NotNull Map<String, IchnoisCoreAttribute> getAttributes() {
    return attributes;
  }

  public static @NotNull IchnoisCoreAttributes of(final @Nullable IchnoisCoreAttribute... attributes) {
    if (attributes == null) {
      return new IchnoisCoreAttributes(new ConcurrentHashMap<>());
    }
    final @NotNull IchnoisCoreAttributes ichnoisAttributes =
        new IchnoisCoreAttributes(new ConcurrentHashMap<>(attributes.length));
    for (IchnoisCoreAttribute attribute : attributes) {
      ichnoisAttributes.add(attribute);
    }
    return ichnoisAttributes;
  }

  public static @NotNull IchnoisCoreAttributes fromMap(final @Nullable Map<String, Object> attributes) {
    if (attributes == null) {
      return new IchnoisCoreAttributes(new ConcurrentHashMap<>());
    }
    final @NotNull IchnoisCoreAttributes ichnoisAttributes =
        new IchnoisCoreAttributes(new ConcurrentHashMap<>(attributes.size()));
    for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
      final @Nullable String key = attribute.getKey();
      if (key != null) {
        ichnoisAttributes.add(IchnoisCoreAttribute.named(key, attribute.getValue()));
      }
    }

    return ichnoisAttributes;
  }
}
