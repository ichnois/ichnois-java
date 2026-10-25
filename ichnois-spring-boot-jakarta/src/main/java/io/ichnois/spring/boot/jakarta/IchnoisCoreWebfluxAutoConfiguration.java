package io.ichnois.spring.boot.jakarta;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.IScope;
import io.ichnois.IScopes;
import io.ichnois.spring.jakarta.webflux.IchnoisCoreScheduleHook;
import io.ichnois.spring.jakarta.webflux.IchnoisCoreWebExceptionHandler;
import io.ichnois.spring.jakarta.webflux.IchnoisCoreWebFilter;
import io.ichnois.spring.jakarta.webflux.IchnoisCoreWebFilterWithThreadLocalAccessor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import reactor.core.publisher.Hooks;
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

  @Configuration(proxyBeanMethods = false)
  @Conditional(IchnoisCoreThreadLocalAccessorCondition.class)
  @Open
  static class IchnoisCoreWebfluxFilterThreadLocalAccessorConfiguration {

    /**
     * Configures a filter that sets up IchnoisCore {@link IScope} for each request.
     *
     * <p>Makes use of newer reactor-core and context-propagation library feature
     * ThreadLocalAccessor to propagate the IchnoisCore scopes.
     */
    @Bean
    @Order(ICHNOIS_SPRING_FILTER_PRECEDENCE)
    public @NotNull IchnoisCoreWebFilterWithThreadLocalAccessor ichnoisWebFilterWithContextPropagation(
        final @NotNull IScopes scopes) {
      Hooks.enableAutomaticContextPropagation();
      return new IchnoisCoreWebFilterWithThreadLocalAccessor(scopes);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @Conditional(IchnoisCoreLegacyFilterConfigurationCondition.class)
  @Open
  static class IchnoisCoreWebfluxFilterConfiguration {

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
  }

  /** Configures exception handler that handles unhandled exceptions and sends them to IchnoisCore. */
  @Bean
  public @NotNull IchnoisCoreWebExceptionHandler ichnoisWebExceptionHandler(
      final @NotNull IScopes scopes) {
    return new IchnoisCoreWebExceptionHandler(scopes);
  }

  static final class IchnoisCoreLegacyFilterConfigurationCondition extends AnyNestedCondition {

    public IchnoisCoreLegacyFilterConfigurationCondition() {
      super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(
        name = "ichnois.reactive.thread-local-accessor-enabled",
        havingValue = "false")
    @SuppressWarnings("UnusedNestedClass")
    private static class IchnoisCoreDisableThreadLocalAccessorCondition {}

    @ConditionalOnMissingClass("io.micrometer.context.ThreadLocalAccessor")
    @SuppressWarnings("UnusedNestedClass")
    private static class ThreadLocalAccessorClassCondition {}
  }

  static final class IchnoisCoreThreadLocalAccessorCondition extends AllNestedConditions {

    public IchnoisCoreThreadLocalAccessorCondition() {
      super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(
        name = "ichnois.reactive.thread-local-accessor-enabled",
        havingValue = "true",
        matchIfMissing = true)
    @SuppressWarnings("UnusedNestedClass")
    private static class IchnoisCoreEnableThreadLocalAccessorCondition {}

    @ConditionalOnClass(io.micrometer.context.ThreadLocalAccessor.class)
    @SuppressWarnings("UnusedNestedClass")
    private static class ThreadLocalAccessorClassCondition {}
  }
}
