package io.ichnois;

import io.ichnois.util.AutoClosableReentrantLock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Has been moved to `ichnois` gradle module to include it in the bootstrap classloader without
 * having to introduce yet another module for OpenTelemetry support.
 *
 * @deprecated please use IchnoisCoreWeakSpanStorage (from ichnois-opentelemetry-bootstrap) instead.
 */
@Deprecated
@ApiStatus.Internal
public final class IchnoisCoreSpanStorage {
  private static volatile @Nullable IchnoisCoreSpanStorage INSTANCE;
  private static final @NotNull AutoClosableReentrantLock staticLock =
      new AutoClosableReentrantLock();

  public static @NotNull IchnoisCoreSpanStorage getInstance() {
    if (INSTANCE == null) {
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = staticLock.acquire()) {
        if (INSTANCE == null) {
          INSTANCE = new IchnoisCoreSpanStorage();
        }
      }
    }

    return INSTANCE;
  }

  private final @NotNull Map<String, ISpan> spans = new ConcurrentHashMap<>();

  private IchnoisCoreSpanStorage() {}

  public void store(final @NotNull String spanId, final @NotNull ISpan span) {
    spans.put(spanId, span);
  }

  public @Nullable ISpan get(final @Nullable String spanId) {
    return spans.get(spanId);
  }

  public @Nullable ISpan removeAndGet(final @Nullable String spanId) {
    return spans.remove(spanId);
  }
}
