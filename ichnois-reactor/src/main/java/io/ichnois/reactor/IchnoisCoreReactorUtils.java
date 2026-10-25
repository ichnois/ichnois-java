package io.ichnois.reactor;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.IScopes;
import io.ichnois.IchnoisCore;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Open
public class IchnoisCoreReactorUtils {

  /**
   * Writes the current IchnoisCore {@link IScopes} to the {@link Context} and uses {@link
   * io.micrometer.context.ThreadLocalAccessor} to propagate it.
   *
   * <p>This requires - reactor.core.publisher.Hooks#enableAutomaticContextPropagation() to be
   * enabled - having `io.micrometer:context-propagation:1.0.2+` (provided by Spring Boot 3.0.3+) -
   * having `io.projectreactor:reactor-core:3.5.3+` (provided by Spring Boot 3.0.3+)
   */
  public static <T> Mono<T> withIchnoisCore(final @NotNull Mono<T> mono) {
    final @NotNull IScopes oldScopes = IchnoisCore.getCurrentScopes();
    final @NotNull IScopes forkedScopes = oldScopes.forkedCurrentScope("reactor.withIchnoisCore");
    return withIchnoisCoreScopes(mono, forkedScopes);
  }

  /**
   * Writes a new IchnoisCore {@link IScopes} forked from the main scopes to the {@link Context} and uses
   * {@link io.micrometer.context.ThreadLocalAccessor} to propagate it.
   *
   * <p>This requires - reactor.core.publisher.Hooks#enableAutomaticContextPropagation() to be
   * enabled - having `io.micrometer:context-propagation:1.0.2+` (provided by Spring Boot 3.0.3+) -
   * having `io.projectreactor:reactor-core:3.5.3+` (provided by Spring Boot 3.0.3+)
   */
  public static <T> Mono<T> withIchnoisCoreForkedRoots(final @NotNull Mono<T> mono) {
    final @NotNull IScopes scopes = IchnoisCore.forkedRootScopes("reactor");
    return withIchnoisCoreScopes(mono, scopes);
  }

  /**
   * Writes the given IchnoisCore {@link IScopes} to the {@link Context} and uses {@link
   * io.micrometer.context.ThreadLocalAccessor} to propagate it.
   *
   * <p>This requires - reactor.core.publisher.Hooks#enableAutomaticContextPropagation() to be
   * enabled - having `io.micrometer:context-propagation:1.0.2+` (provided by Spring Boot 3.0.3+) -
   * having `io.projectreactor:reactor-core:3.5.3+` (provided by Spring Boot 3.0.3+)
   */
  public static <T> Mono<T> withIchnoisCoreScopes(
      final @NotNull Mono<T> mono, final @NotNull IScopes scopes) {
    /*
     * WARNING: Cannot set the scopes as current. It would be used by others to clone again causing
     * shared scopes and thus leading to issues like unrelated breadcrumbs showing up in events.
     */
    // IchnoisCore.setCurrentScopes(forkedScopes);

    return Mono.deferContextual(ctx -> mono)
        .contextWrite(Context.of(IchnoisCoreReactorThreadLocalAccessor.KEY, scopes));
  }

  /**
   * Writes the current IchnoisCore {@link IScopes} to the {@link Context} and uses {@link
   * io.micrometer.context.ThreadLocalAccessor} to propagate it.
   *
   * <p>This requires - reactor.core.publisher.Hooks#enableAutomaticContextPropagation() to be
   * enabled - having `io.micrometer:context-propagation:1.0.2+` (provided by Spring Boot 3.0.3+) -
   * having `io.projectreactor:reactor-core:3.5.3+` (provided by Spring Boot 3.0.3+)
   */
  public static <T> Flux<T> withIchnoisCore(final @NotNull Flux<T> flux) {
    final @NotNull IScopes oldScopes = IchnoisCore.getCurrentScopes();
    final @NotNull IScopes forkedScopes = oldScopes.forkedCurrentScope("reactor.withIchnoisCore");

    return withIchnoisCoreScopes(flux, forkedScopes);
  }

  /**
   * Writes a new IchnoisCore {@link IScopes} forked from the main scopes to the {@link Context} and uses
   * {@link io.micrometer.context.ThreadLocalAccessor} to propagate it.
   *
   * <p>This requires - reactor.core.publisher.Hooks#enableAutomaticContextPropagation() to be
   * enabled - having `io.micrometer:context-propagation:1.0.2+` (provided by Spring Boot 3.0.3+) -
   * having `io.projectreactor:reactor-core:3.5.3+` (provided by Spring Boot 3.0.3+)
   */
  public static <T> Flux<T> withIchnoisCoreForkedRoots(final @NotNull Flux<T> flux) {
    final @NotNull IScopes scopes = IchnoisCore.forkedRootScopes("reactor");
    return withIchnoisCoreScopes(flux, scopes);
  }

  /**
   * Writes the given IchnoisCore {@link IScopes} to the {@link Context} and uses {@link
   * io.micrometer.context.ThreadLocalAccessor} to propagate it.
   *
   * <p>This requires - reactor.core.publisher.Hooks#enableAutomaticContextPropagation() to be
   * enabled - having `io.micrometer:context-propagation:1.0.2+` (provided by Spring Boot 3.0.3+) -
   * having `io.projectreactor:reactor-core:3.5.3+` (provided by Spring Boot 3.0.3+)
   */
  public static <T> Flux<T> withIchnoisCoreScopes(
      final @NotNull Flux<T> flux, final @NotNull IScopes scopes) {
    /*
     * WARNING: Cannot set the scopes as current. It would be used by others to fork again causing
     * shared scopes and thus leading to issues like unrelated breadcrumbs showing up in events.
     */
    // IchnoisCore.setCurrentScopes(forkedScopes);

    return Flux.deferContextual(ctx -> flux)
        .contextWrite(Context.of(IchnoisCoreReactorThreadLocalAccessor.KEY, scopes));
  }
}
