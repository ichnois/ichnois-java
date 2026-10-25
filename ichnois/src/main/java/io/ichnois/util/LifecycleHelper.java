package io.ichnois.util;

import io.ichnois.IIchnoisCoreLifecycleToken;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class LifecycleHelper {

  public static void close(final @Nullable Object tokenObject) {
    if (tokenObject instanceof IIchnoisCoreLifecycleToken) {
      final @NotNull IIchnoisCoreLifecycleToken token = (IIchnoisCoreLifecycleToken) tokenObject;
      token.close();
    }
  }
}
