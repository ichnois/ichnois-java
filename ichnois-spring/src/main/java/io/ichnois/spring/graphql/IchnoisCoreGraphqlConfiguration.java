package io.ichnois.spring.graphql;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.IchnoisCoreIntegrationPackageStorage;
import io.ichnois.graphql.IchnoisCoreGraphqlInstrumentation;
import io.ichnois.graphql.IchnoisCoreInstrumentation;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration(proxyBeanMethods = false)
@Open
public class IchnoisCoreGraphqlConfiguration {

  @Bean(name = "ichnoisInstrumentation")
  @ConditionalOnMissingBean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  public IchnoisCoreInstrumentation ichnoisInstrumentationWebMvc(
      final @NotNull ObjectProvider<IchnoisCoreGraphqlInstrumentation.BeforeSpanCallback>
              beforeSpanCallback) {
    IchnoisCoreIntegrationPackageStorage.getInstance().addIntegration("Spring5GraphQLWebMVC");
    return createInstrumentation(beforeSpanCallback, false);
  }

  @Bean(name = "ichnoisInstrumentation")
  @ConditionalOnMissingBean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
  public IchnoisCoreInstrumentation ichnoisInstrumentationWebflux(
      final @NotNull ObjectProvider<IchnoisCoreGraphqlInstrumentation.BeforeSpanCallback>
              beforeSpanCallback) {
    IchnoisCoreIntegrationPackageStorage.getInstance().addIntegration("Spring5GraphQLWebFlux");
    return createInstrumentation(beforeSpanCallback, true);
  }

  /**
   * We're not setting defaultDataFetcherExceptionHandler here on purpose and instead use the
   * resolver adapter below. This way Springs handler can still forward to other resolver adapters.
   */
  private IchnoisCoreInstrumentation createInstrumentation(
      final @NotNull ObjectProvider<IchnoisCoreGraphqlInstrumentation.BeforeSpanCallback>
              beforeSpanCallback,
      final boolean captureRequestBody) {
    return new IchnoisCoreInstrumentation(
        beforeSpanCallback.getIfAvailable(),
        new IchnoisCoreSpringSubscriptionHandler(),
        captureRequestBody);
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public IchnoisCoreDataFetcherExceptionResolverAdapter exceptionResolverAdapter() {
    return new IchnoisCoreDataFetcherExceptionResolverAdapter();
  }

  @Bean
  public IchnoisCoreGraphqlBeanPostProcessor graphqlBeanPostProcessor() {
    return new IchnoisCoreGraphqlBeanPostProcessor();
  }
}
