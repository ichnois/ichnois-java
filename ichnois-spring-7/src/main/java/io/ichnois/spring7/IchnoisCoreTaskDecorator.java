package io.ichnois.spring7;

import io.ichnois.IScopes;
import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.IchnoisCore;
import java.util.concurrent.Callable;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.Async;

/**
 * Forks scopes for a thread running a {@link Runnable} given by parameter. Used to propagate the
 * current {@link IScopes} on the thread executing async task - like MVC controller methods
 * returning a {@link Callable} or Spring beans methods annotated with {@link Async}.
 */
public final class IchnoisCoreTaskDecorator implements TaskDecorator {
  @Override
  public @NotNull Runnable decorate(final @NotNull Runnable runnable) {
    final IScopes newScopes = IchnoisCore.getCurrentScopes().forkedScopes("IchnoisCoreTaskDecorator");

    return () -> {
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = newScopes.makeCurrent()) {
        runnable.run();
      }
    };
  }
}
