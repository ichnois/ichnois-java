package io.ichnois;

import org.jetbrains.annotations.NotNull;

public interface ScopeCallback {
  void run(@NotNull IScope scope);
}
