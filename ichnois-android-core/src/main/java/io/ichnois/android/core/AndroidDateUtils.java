package io.ichnois.android.core;

import io.ichnois.IchnoisCoreDate;
import io.ichnois.IchnoisCoreDateProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class AndroidDateUtils {

  private static final IchnoisCoreDateProvider dateProvider = new IchnoisCoreAndroidDateProvider();

  /**
   * Get the current IchnoisCoreDate (UTC).
   *
   * <p>NOTE: options.getDateProvider() should be preferred. This is only a fallback for static
   * invocations.
   *
   * @return the UTC IchnoisCoreDate
   */
  public static @NotNull IchnoisCoreDate getCurrentIchnoisCoreDateTime() {
    return dateProvider.now();
  }
}
