package io.ichnois.android.core;

import io.ichnois.IAppStartExtender;
import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.ISpan;
import io.ichnois.ITransaction;
import io.ichnois.IchnoisCore;
import io.ichnois.IchnoisCoreDate;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.SpanStatus;
import io.ichnois.android.core.performance.AppStartMetrics;
import io.ichnois.util.AutoClosableReentrantLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class AppStartExtension implements IAppStartExtender {

  public static final class ExtendedAppStart {
    public final @NotNull ITransaction transaction;
    public final @NotNull ISpan span;

    public ExtendedAppStart(final @NotNull ITransaction transaction, final @NotNull ISpan span) {
      this.transaction = transaction;
      this.span = span;
    }
  }

  public interface ExtendAppStartListener {
    @Nullable
    ExtendedAppStart onExtendAppStartRequested();
  }

  private final @NotNull AppStartMetrics metrics;
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();
  // Serializes the two finish paths against each other. Deliberately separate from `lock`:
  // finishing re-enters the SDK and that re-entrant path takes `lock`, so the finish cannot run
  // under `lock` (see finishTransaction). When both are held the order is finishLock, then `lock`.
  private final @NotNull AutoClosableReentrantLock finishLock = new AutoClosableReentrantLock();

  private @Nullable ExtendAppStartListener extendAppStartListener;
  // We hold onto both the span and its transaction because they mean different things and finish
  // at different times:
  //
  //  - extendedSpan is what the app developer works with: they get it from
  //    getExtendedAppStartSpan(), add their own child spans to it, and finish it by calling
  //    finishExtendedAppStart(). Its end time is what extends the app start measurement.
  //
  //  - extendedTransaction is the standalone "app.start" transaction that actually gets sent to
  //    IchnoisCore. It carries the span and the screen name. The SDK asks it to finish at the first
  //    frame (or headless end), but because it uses waitForChildren it stays open until the span
  //    finishes (or the deadline is hit).
  //
  // A span doesn't expose its transaction, and pulling the span back out of the transaction would
  // be fragile, so we just keep a reference to each.
  private @Nullable ISpan extendedSpan;
  private @Nullable ITransaction extendedTransaction;

  public AppStartExtension(final @NotNull AppStartMetrics metrics) {
    this.metrics = metrics;
  }

  public void setExtendAppStartListener(final @Nullable ExtendAppStartListener listener) {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
      this.extendAppStartListener = listener;
    }
  }

  @Override
  public void extendAppStart() {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
      if (extendedSpan != null) {
        IchnoisCore.getCurrentScopes()
            .getOptions()
            .getLogger()
            .log(IchnoisCoreLevel.WARNING, "App start is already being extended.");
        return;
      }
      if (!metrics.canExtendAppStart()) {
        IchnoisCore.getCurrentScopes()
            .getOptions()
            .getLogger()
            .log(
                IchnoisCoreLevel.WARNING,
                "Cannot extend app start: the app start window has already passed.");
        return;
      }
      final @Nullable ExtendAppStartListener listener = extendAppStartListener;
      if (listener != null) {
        final @Nullable ExtendedAppStart extended = listener.onExtendAppStartRequested();
        if (extended != null) {
          this.extendedTransaction = extended.transaction;
          this.extendedSpan = extended.span;
        }
      }
    }
  }

  /**
   * Sets data on the owned (eager) transaction if it is still open. Used to attach the screen name
   * once the first activity is known, since the transaction is created in {@code onCreate} before
   * any activity exists.
   */
  public void setData(final @NotNull String key, final @Nullable Object value) {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
      if (extendedTransaction != null && !extendedTransaction.isFinished()) {
        extendedTransaction.setData(key, value);
      }
    }
  }

  @Override
  public void finishExtendedAppStart() {
    try (final @NotNull IIchnoisCoreLifecycleToken ignoredFinish = finishLock.acquire()) {
      final @Nullable ISpan span;
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
        span = extendedSpan;
      }
      // Finishing runs outside `lock`, see the note on finishTransaction.
      if (span != null && !span.isFinished()) {
        span.finish(SpanStatus.OK);
      }
    }
  }

  @Override
  public @Nullable ISpan getExtendedAppStartSpan() {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
      final @Nullable ISpan span = extendedSpan;
      // Mirrors getExtendedEndTime(): the finish date is set before isFinished() flips.
      if (span != null && span.getFinishDate() == null) {
        return span;
      }
      return null;
    }
  }

  public boolean isActive() {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
      return extendedTransaction != null && !extendedTransaction.isFinished();
    }
  }

  /**
   * Whether this app start was extended at all, regardless of finish or deadline state. Used by the
   * event processor to decide whether to apply the never-shorten vital logic.
   */
  public boolean isExtended() {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
      return extendedSpan != null;
    }
  }

  public void finishTransaction(final @NotNull IchnoisCoreDate endTimestamp) {
    // Finishing has to run outside `lock`: it captures the transaction synchronously, which runs
    // PerformanceAndroidEventProcessor, which calls back into isExtended()/getExtendedEndTime()
    // while holding its own lock. Holding `lock` across the call would let the two be taken in
    // opposite orders and deadlock. finishLock still serializes this against
    // finishExtendedAppStart, so the end-time clamp below and the finish stay atomic.
    try (final @NotNull IIchnoisCoreLifecycleToken ignoredFinish = finishLock.acquire()) {
      final @Nullable ITransaction transaction;
      final @Nullable ISpan span;
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
        transaction = extendedTransaction;
        span = extendedSpan;
      }
      if (transaction != null && !transaction.isFinished()) {
        final @Nullable IchnoisCoreDate spanEnd = span == null ? null : span.getFinishDate();
        final @NotNull IchnoisCoreDate end =
            spanEnd != null && spanEnd.isAfter(endTimestamp) ? spanEnd : endTimestamp;
        transaction.finish(SpanStatus.OK, end);
      }
    }
  }

  public @Nullable IchnoisCoreDate getExtendedEndTime() {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
      final @Nullable ISpan span = extendedSpan;
      if (span == null) {
        return null;
      }
      // A deadline timeout would report an artificially inflated duration; suppress the vital
      // instead.
      if (span.getStatus() == SpanStatus.DEADLINE_EXCEEDED) {
        return null;
      }
      // Read the finish date, not isFinished(): finishing the extended span completes the
      // waitForChildren transaction and runs the event processor re-entrantly before the span's
      // finished flag is set, but the finish timestamp is already in place. Null until finished.
      return span.getFinishDate();
    }
  }

  public void clear() {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
      extendedSpan = null;
      extendedTransaction = null;
    }
  }
}
