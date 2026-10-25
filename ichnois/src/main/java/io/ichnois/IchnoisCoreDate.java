package io.ichnois;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class IchnoisCoreDate implements Comparable<IchnoisCoreDate> {

  /** Returns the date in nanoseconds as long. */
  public abstract long nanoTimestamp();

  /**
   * Calculates a date by using another date.
   *
   * <p>This is a workaround for limited precision offered in some cases (e.g. when using {@link
   * IchnoisCoreNanotimeDate}). This makes it possible to have high precision duration by using
   * nanoseconds for the finish timestamp where normally the start and finish timestamps would only
   * offer millisecond precision.
   *
   * @param otherDate another {@link IchnoisCoreDate}
   * @return date in seconds as long
   */
  public long laterDateNanosTimestampByDiff(final @Nullable IchnoisCoreDate otherDate) {
    if (otherDate != null && compareTo(otherDate) < 0) {
      return otherDate.nanoTimestamp();
    } else {
      return nanoTimestamp();
    }
  }

  /**
   * Difference between two dates in nanoseconds.
   *
   * @param otherDate another {@link IchnoisCoreDate}
   * @return difference in nanoseconds
   */
  public long diff(final @NotNull IchnoisCoreDate otherDate) {
    return nanoTimestamp() - otherDate.nanoTimestamp();
  }

  public final boolean isBefore(final @NotNull IchnoisCoreDate otherDate) {
    return diff(otherDate) < 0;
  }

  public final boolean isAfter(final @NotNull IchnoisCoreDate otherDate) {
    return diff(otherDate) > 0;
  }

  @Override
  public int compareTo(@NotNull IchnoisCoreDate otherDate) {
    return Long.compare(nanoTimestamp(), otherDate.nanoTimestamp());
  }
}
