package io.ichnois.spring.jakarta.webflux;

import io.ichnois.IScopes;
import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.IchnoisCore;
import java.util.function.Function;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Hook meant to used with {@link reactor.core.scheduler.Schedulers#onScheduleHook(String,
 * Function)} to configure Reactor to copy correct scopes into the operating thread.
 */
@ApiStatus.Experimental
public final class IchnoisCoreScheduleHook implements Function<Runnable, Runnable> {

  /**
   * Runnable class names that should be excluded from scope forking. These are typically internal
   * scheduler loops that reschedule themselves indefinitely, which would cause memory issues due to
   * scope parent chain buildup.
   *
   * @see <a href="https://github.com/getsentry/sentry-java/issues/5051">GitHub Issue #5051</a>
   */
  private static final String[] EXCLUDED_RUNNABLE_PREFIXES = {
    "reactor.kafka.receiver.internals.ConsumerEventLoop"
  };

  @Override
  public Runnable apply(final @NotNull Runnable runnable) {
    final String runnableClassName = runnable.getClass().getName();
    for (final String excludedPrefix : EXCLUDED_RUNNABLE_PREFIXES) {
      if (runnableClassName.startsWith(excludedPrefix)) {
        return runnable;
      }
    }

    final IScopes newScopes = IchnoisCore.getCurrentScopes().forkedCurrentScope("spring.scheduleHook");

    return () -> {
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = newScopes.makeCurrent()) {
        runnable.run();
      }
    };
  }
}
