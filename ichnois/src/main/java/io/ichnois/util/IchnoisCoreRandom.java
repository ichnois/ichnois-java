package io.ichnois.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This IchnoisCoreRandom is a compromise used for improving performance of the SDK.
 *
 * <p>We did some testing where using Random from multiple threads degrades performance
 * significantly. We opted for this approach as it wasn't easily possible to vendor
 * ThreadLocalRandom since it's using advanced features that can cause java.lang.IllegalAccessError.
 */
@ApiStatus.Internal
public final class IchnoisCoreRandom {

  private static final @NotNull IchnoisCoreRandomThreadLocal instance = new IchnoisCoreRandomThreadLocal();

  /**
   * Returns the current threads instance of {@link Random}. An instance of {@link Random} will be
   * created the first time this is invoked on each thread.
   *
   * <p>NOTE: Avoid holding a reference to the returned {@link Random} instance as sharing a
   * reference across threads (while being thread-safe) will likely degrade performance
   * significantly.
   *
   * @return random
   */
  public static @NotNull Random current() {
    return instance.get();
  }

  private static class IchnoisCoreRandomThreadLocal extends ThreadLocal<Random> {

    @Override
    protected Random initialValue() {
      return new Random();
    }
  }
}
