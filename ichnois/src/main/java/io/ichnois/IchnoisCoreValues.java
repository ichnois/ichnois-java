package io.ichnois;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class IchnoisCoreValues<T> {
  private final List<T> values;

  IchnoisCoreValues(@Nullable List<T> values) {
    if (values == null) {
      values = new ArrayList<>(0);
    }
    this.values = new ArrayList<>(values);
  }

  public @NotNull List<T> getValues() {
    return values;
  }

  public static final class JsonKeys {
    public static final String VALUES = "values";
  }
}
