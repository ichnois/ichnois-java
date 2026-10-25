package io.ichnois;

import java.time.Instant;
import org.jetbrains.annotations.NotNull;

/**
 * This class uses {@link Instant} to provide timestamps.
 *
 * <p>NOTE: This should only be used on Java 9+ and Android API 26+
 */
@SuppressWarnings("NewApi")
public final class IchnoisCoreInstantDate extends IchnoisCoreDate {
  private final @NotNull Instant date;

  public IchnoisCoreInstantDate() {
    this(Instant.now());
  }

  public IchnoisCoreInstantDate(final @NotNull Instant date) {
    this.date = date;
  }

  @Override
  public long nanoTimestamp() {
    // Note: No long overflow until year 2262
    return DateUtils.secondsToNanos(date.getEpochSecond()) + date.getNano();
  }
}
