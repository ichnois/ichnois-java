package io.ichnois.opentelemetry;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.internal.shaded.WeakConcurrentMap;
import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.util.AutoClosableReentrantLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Weakly references wrappers for OpenTelemetry spans meaning they'll be cleaned up when the
 * OpenTelemetry span is garbage collected.
 */
@ApiStatus.Internal
public final class IchnoisCoreWeakSpanStorage {
  private static volatile @Nullable IchnoisCoreWeakSpanStorage INSTANCE;
  private static final @NotNull AutoClosableReentrantLock staticLock =
      new AutoClosableReentrantLock();

  public static @NotNull IchnoisCoreWeakSpanStorage getInstance() {
    if (INSTANCE == null) {
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = staticLock.acquire()) {
        if (INSTANCE == null) {
          INSTANCE = new IchnoisCoreWeakSpanStorage();
        }
      }
    }

    return INSTANCE;
  }

  // weak keys, spawns a thread to clean up values that have been garbage collected
  private final @NotNull WeakConcurrentMap<SpanContext, IOtelSpanWrapper> ichnoisSpans =
      new WeakConcurrentMap<>(true);

  private IchnoisCoreWeakSpanStorage() {}

  public @Nullable IOtelSpanWrapper getIchnoisCoreSpan(final @NotNull SpanContext spanContext) {
    return ichnoisSpans.get(spanContext);
  }

  public void storeIchnoisCoreSpan(
      final @NotNull SpanContext otelSpan, final @NotNull IOtelSpanWrapper ichnoisSpan) {
    this.ichnoisSpans.put(otelSpan, ichnoisSpan);
  }

  @TestOnly
  public void clear() {
    ichnoisSpans.clear();
  }
}
