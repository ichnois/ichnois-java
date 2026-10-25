package io.ichnois.android.core;

import io.ichnois.IchnoisCoreDate;
import io.ichnois.IchnoisCoreDateProvider;
import io.ichnois.IchnoisCoreNanotimeDateProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This {@link IchnoisCoreDateProvider} will use a combination of {@link java.util.Date} and
 * System.nanoTime() on Android. During testing we discovered that {@link java.time.Instant} only
 * offers ms precision if the build has desugaring enabled. Event without desugaring only API 33
 * offered higher precision. More info can be found here:
 * https://github.com/getsentry/sentry-java/pull/2451
 */
@ApiStatus.Internal
public final class IchnoisCoreAndroidDateProvider implements IchnoisCoreDateProvider {

  private @NotNull IchnoisCoreDateProvider dateProvider = new IchnoisCoreNanotimeDateProvider();

  @Override
  public IchnoisCoreDate now() {
    return dateProvider.now();
  }
}
