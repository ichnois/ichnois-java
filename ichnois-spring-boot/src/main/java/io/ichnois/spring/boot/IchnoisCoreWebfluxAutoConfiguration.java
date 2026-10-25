package io.ichnois.spring.boot;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.IScope;
import io.ichnois.IScopes;
import io.ichnois.spring.webflux.IchnoisCoreScheduleHook;
import io.ichnois.spring.webflux.IchnoisCoreWebExceptionHandler;
import io.ichnois.spring.webflux.IchnoisCoreWebFilter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import reactor.core.scheduler.Schedulers;

/** Configures IchnoisCore integration for Spring Webflux and Project Reactor. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnBean(IScopes.class)
@ConditionalOnClass(Schedulers.class)
@Open
@ApiStatus.Experimental
public class IchnoisCoreWebfluxAutoConfiguration {
  private static final int ICHNOIS_SPRING_FILTER_PRECEDENCE = Ordered.HIGHEST_PRECEDENCE;

  /** Configures hook that sets correct scopes on the executing thread. */
  @Bean
  public @NotNull ApplicationRunner ichnoisScheduleHookApplicationRunner() {
    return args -> {
      Schedulers.onScheduleHook("ichnois", new IchnoisCoreScheduleHook());
    };
  }

  /** Configures a filter that sets up IchnoisCore {@link IScope} for each request. */
  @Bean
  @Order(ICHNOIS_SPRING_FILTER_PRECEDENCE)
  public @NotNull IchnoisCoreWebFilter ichnoisWebFilter(final @NotNull IScopes scopes) {
    return new IchnoisCoreWebFilter(scopes);
  }

  /** Configures exception handler that handles unhandled exceptions and sends them to IchnoisCore. */
  @Bean
  public @NotNull IchnoisCoreWebExceptionHandler ichnoisWebExceptionHandler(
      final @NotNull IScopes scopes) {
    return new IchnoisCoreWebExceptionHandler(scopes);
  }
}
