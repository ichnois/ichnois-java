package io.ichnois;

import io.ichnois.ITransaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Ichnois-owned handle for a timed operation. */
public final class IchnoisSpan {
  private final @NotNull ITransaction transaction;
  private boolean finished;

  IchnoisSpan(@NotNull ITransaction transaction) {
    this.transaction = transaction;
  }

  /** Finishes the operation once. Repeated calls are safe. */
  public synchronized void finish() {
    if (!finished) {
      transaction.finish();
      finished = true;
    }
  }

  /** Adds a tag to this operation. */
  public void setTag(@NotNull String key, @NotNull String value) {
    transaction.setTag(key, value);
  }

  /** Returns whether this operation has been finished. */
  public synchronized boolean isFinished() {
    return finished;
  }
}
