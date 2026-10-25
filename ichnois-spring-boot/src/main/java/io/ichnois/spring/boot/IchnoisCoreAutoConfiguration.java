package io.ichnois.spring.boot;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.EventProcessor;
import io.ichnois.IScopes;
import io.ichnois.ISpanFactory;
import io.ichnois.ITransportFactory;
import io.ichnois.InitPriority;
import io.ichnois.Integration;
import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCore;
import io.ichnois.IchnoisCoreIntegrationPackageStorage;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.protocol.SdkVersion;
import io.ichnois.quartz.IchnoisCoreJobListener;
import io.ichnois.spring.ContextTagsEventProcessor;
import io.ichnois.spring.IchnoisCoreExceptionResolver;
import io.ichnois.spring.IchnoisCoreRequestResolver;
import io.ichnois.spring.IchnoisCoreSpringFilter;
import io.ichnois.spring.IchnoisCoreUserFilter;
import io.ichnois.spring.IchnoisCoreUserProvider;
import io.ichnois.spring.IchnoisCoreWebConfiguration;
import io.ichnois.spring.SpringProfilesEventProcessor;
import io.ichnois.spring.SpringSecurityIchnoisCoreUserProvider;
import io.ichnois.spring.boot.graphql.IchnoisCoreGraphqlAutoConfiguration;
import io.ichnois.spring.cache.IchnoisCoreCacheBeanPostProcessor;
import io.ichnois.spring.checkin.IchnoisCoreCheckInAdviceConfiguration;
import io.ichnois.spring.checkin.IchnoisCoreCheckInPointcutConfiguration;
import io.ichnois.spring.checkin.IchnoisCoreQuartzConfiguration;
import io.ichnois.spring.exception.IchnoisCoreCaptureExceptionParameterPointcutConfiguration;
import io.ichnois.spring.exception.IchnoisCoreExceptionParameterAdviceConfiguration;
import io.ichnois.spring.kafka.IchnoisCoreKafkaConsumerBeanPostProcessor;
import io.ichnois.spring.kafka.IchnoisCoreKafkaProducerBeanPostProcessor;
import io.ichnois.spring.opentelemetry.IchnoisCoreOpenTelemetryAgentWithoutAutoInitConfiguration;
import io.ichnois.spring.opentelemetry.IchnoisCoreOpenTelemetryNoAgentConfiguration;
import io.ichnois.spring.tracing.CombinedTransactionNameProvider;
import io.ichnois.spring.tracing.IchnoisCoreAdviceConfiguration;
import io.ichnois.spring.tracing.IchnoisCoreSpanPointcutConfiguration;
import io.ichnois.spring.tracing.IchnoisCoreTracingFilter;
import io.ichnois.spring.tracing.IchnoisCoreTransactionPointcutConfiguration;
import io.ichnois.spring.tracing.SpringMvcTransactionNameProvider;
import io.ichnois.spring.tracing.SpringServletTransactionNameProvider;
import io.ichnois.spring.tracing.TransactionNameProvider;
import io.ichnois.transport.ITransportGate;
import io.ichnois.transport.apache.ApacheHttpClientTransportFactory;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.jetbrains.annotations.NotNull;
import org.quartz.core.QuartzScheduler;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ichnois.dsn")
@Open
public class IchnoisCoreAutoConfiguration {

  static {
    IchnoisCoreIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.ichnois:ichnois-spring-boot-starter", BuildConfig.VERSION_NAME);
  }

  /** Registers general purpose IchnoisCore related beans. */
  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(IchnoisCoreProperties.class)
  @Open
  static class HubConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "ichnoisOptionsConfiguration")
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public @NotNull IchnoisCore.OptionsConfiguration<IchnoisCoreOptions> ichnoisOptionsConfiguration(
        final @NotNull ObjectProvider<IchnoisCoreOptions.BeforeSendCallback> beforeSendCallback,
        final @NotNull ObjectProvider<IchnoisCoreOptions.BeforeSendTransactionCallback>
                beforeSendTransactionCallback,
        final @NotNull ObjectProvider<IchnoisCoreOptions.Logs.BeforeSendLogCallback>
                beforeSendLogsCallback,
        final @NotNull ObjectProvider<IchnoisCoreOptions.Metrics.BeforeSendMetricCallback>
                beforeSendMetricCallback,
        final @NotNull ObjectProvider<IchnoisCoreOptions.BeforeBreadcrumbCallback>
                beforeBreadcrumbCallback,
        final @NotNull ObjectProvider<IchnoisCoreOptions.OnDiscardCallback> onDiscardCallback,
        final @NotNull ObjectProvider<IchnoisCoreOptions.TracesSamplerCallback> tracesSamplerCallback,
        final @NotNull List<EventProcessor> eventProcessors,
        final @NotNull List<Integration> integrations,
        final @NotNull ObjectProvider<ITransportGate> transportGate,
        final @NotNull ObjectProvider<ITransportFactory> transportFactory,
        final @NotNull InAppIncludesResolver inAppPackagesResolver) {
      return options -> {
        beforeSendCallback.ifAvailable(options::setBeforeSend);
        beforeSendTransactionCallback.ifAvailable(options::setBeforeSendTransaction);
        beforeSendLogsCallback.ifAvailable(callback -> options.getLogs().setBeforeSend(callback));
        beforeSendMetricCallback.ifAvailable(
            callback -> options.getMetrics().setBeforeSend(callback));
        beforeBreadcrumbCallback.ifAvailable(options::setBeforeBreadcrumb);
        onDiscardCallback.ifAvailable(options::setOnDiscard);
        tracesSamplerCallback.ifAvailable(options::setTracesSampler);
        eventProcessors.forEach(options::addEventProcessor);
        integrations.forEach(options::addIntegration);
        transportGate.ifAvailable(options::setTransportGate);
        transportFactory.ifAvailable(options::setTransportFactory);
        inAppPackagesResolver.resolveInAppIncludes().forEach(options::addInAppInclude);
      };
    }

    @Bean
    public @NotNull InAppIncludesResolver inAppPackagesResolver() {
      return new InAppIncludesResolver();
    }

    @Configuration(proxyBeanMethods = false)
    @Import(IchnoisCoreOpenTelemetryAgentWithoutAutoInitConfiguration.class)
    @Open
    @ConditionalOnProperty(name = "ichnois.auto-init", havingValue = "false")
    @ConditionalOnClass(name = {"io.ichnois.opentelemetry.agent.AgentMarker"})
    static class OpenTelemetryAgentWithoutAutoInitConfiguration {}

    @Configuration(proxyBeanMethods = false)
    @Import(IchnoisCoreOpenTelemetryNoAgentConfiguration.class)
    @Open
    @ConditionalOnClass(
        name = {
          "io.opentelemetry.api.OpenTelemetry",
          "io.ichnois.opentelemetry.IchnoisCoreAutoConfigurationCustomizerProvider"
        })
    @ConditionalOnMissingClass("io.ichnois.opentelemetry.agent.AgentMarker")
    static class OpenTelemetryNoAgentConfiguration {}

    @Bean
    public @NotNull IScopes ichnoisHub(
        final @NotNull List<IchnoisCore.OptionsConfiguration<IchnoisCoreOptions>> optionsConfigurations,
        final @NotNull IchnoisCoreProperties options,
        final @NotNull ObjectProvider<ISpanFactory> spanFactory,
        final @NotNull ObjectProvider<GitProperties> gitProperties) {
      optionsConfigurations.forEach(
          optionsConfiguration -> optionsConfiguration.configure(options));
      gitProperties.ifAvailable(
          git -> {
            if (options.getRelease() == null && options.isUseGitCommitIdAsRelease()) {
              options.setRelease(git.getCommitId());
            }
          });
      spanFactory.ifAvailable(options::setSpanFactory);

      options.setIchnoisClientName(
          BuildConfig.ICHNOIS_SPRING_BOOT_SDK_NAME + "/" + BuildConfig.VERSION_NAME);
      options.setSdkVersion(createSdkVersion(options));
      options.setInitPriority(InitPriority.LOW);
      addPackageAndIntegrationInfo();
      // Spring Boot sets ignored exceptions in runtime using reflection - where the generic
      // information is lost
      // its technically possible to set non-throwable class to `ignoredExceptionsForType` set
      // here we make sure that only classes that extend throwable are set on this field
      options.getIgnoredExceptionsForType().removeIf(it -> !Throwable.class.isAssignableFrom(it));
      IchnoisCore.init(options);
      return ScopesAdapter.getInstance();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MDC.class)
    @Open
    static class ContextTagsEventProcessorConfiguration {

      @Bean
      public @NotNull ContextTagsEventProcessor contextTagsEventProcessor(
          final @NotNull IchnoisCoreOptions ichnoisOptions) {
        return new ContextTagsEventProcessor(ichnoisOptions);
      }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(IchnoisCoreGraphqlAutoConfiguration.class)
    @Open
    @ConditionalOnClass(
        name = {
          "io.ichnois.graphql.IchnoisCoreGraphqlExceptionHandler",
          "org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter",
          "graphql.GraphQLError"
        })
    static class GraphqlConfiguration {}

    @Configuration(proxyBeanMethods = false)
    @Import(IchnoisCoreQuartzConfiguration.class)
    @Open
    @ConditionalOnClass({
      IchnoisCoreJobListener.class,
      QuartzScheduler.class,
      SchedulerFactoryBean.class
    })
    static class QuartzConfiguration {}

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(CacheManager.class)
    @ConditionalOnProperty(name = "ichnois.enable-cache-tracing", havingValue = "true")
    @Open
    static class IchnoisCoreCacheConfiguration {

      @Bean
      public static @NotNull IchnoisCoreCacheBeanPostProcessor ichnoisCacheBeanPostProcessor() {
        IchnoisCoreIntegrationPackageStorage.getInstance().addIntegration("SpringCache");
        return new IchnoisCoreCacheBeanPostProcessor();
      }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(
        name = {
          "org.springframework.kafka.core.KafkaTemplate",
          "io.ichnois.kafka.IchnoisCoreKafkaProducer"
        })
    @ConditionalOnProperty(name = "ichnois.enable-queue-tracing", havingValue = "true")
    @ConditionalOnMissingClass({
      "io.ichnois.opentelemetry.IchnoisCoreAutoConfigurationCustomizerProvider",
      "io.ichnois.opentelemetry.agent.AgentMarker"
    })
    @Open
    static class IchnoisCoreKafkaQueueConfiguration {

      @Bean
      public static @NotNull IchnoisCoreKafkaProducerBeanPostProcessor
          ichnoisKafkaProducerBeanPostProcessor() {
        IchnoisCoreIntegrationPackageStorage.getInstance().addIntegration("SpringKafka");
        return new IchnoisCoreKafkaProducerBeanPostProcessor();
      }

      @Bean
      public static @NotNull IchnoisCoreKafkaConsumerBeanPostProcessor
          ichnoisKafkaConsumerBeanPostProcessor() {
        return new IchnoisCoreKafkaConsumerBeanPostProcessor();
      }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(
        name = {
          "okhttp3.OkHttpClient",
          "io.ichnois.okhttp.IchnoisCoreOkHttpInterceptor",
          "io.ichnois.okhttp.IchnoisCoreOkHttpEventListener"
        })
    @ConditionalOnProperty(name = "ichnois.clients.ok-http-enabled", havingValue = "true")
    @ConditionalOnMissingClass("io.ichnois.opentelemetry.agent.AgentMarker")
    @Open
    static class IchnoisCoreOkHttpConfiguration {

      @Bean
      public static @NotNull IchnoisCoreOkHttpClientBeanPostProcessor
          ichnoisOkHttpClientBeanPostProcessor() {
        return new IchnoisCoreOkHttpClientBeanPostProcessor();
      }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ProceedingJoinPoint.class)
    @ConditionalOnProperty(
        value = "ichnois.enable-aot-compatibility",
        havingValue = "false",
        matchIfMissing = true)
    @Import(IchnoisCoreCheckInAdviceConfiguration.class)
    @Open
    static class IchnoisCoreCheckInAspectsConfiguration {
      @Configuration(proxyBeanMethods = false)
      @ConditionalOnMissingBean(name = "ichnoisCheckInPointcut")
      @Import(IchnoisCoreCheckInPointcutConfiguration.class)
      @Open
      static class IchnoisCoreCheckInPointcutAutoConfiguration {}
    }

    /** Registers beans specific to Spring MVC. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @Import(IchnoisCoreWebConfiguration.class)
    @Open
    static class IchnoisCoreWebMvcConfiguration {

      private static final int ICHNOIS_SPRING_FILTER_PRECEDENCE = Ordered.HIGHEST_PRECEDENCE;

      @Configuration(proxyBeanMethods = false)
      @ConditionalOnClass(SecurityContextHolder.class)
      @Open
      static class IchnoisCoreSecurityConfiguration {

        /**
         * Configures {@link SpringSecurityIchnoisCoreUserProvider} only if Spring Security is on the
         * classpath. Its order is set to be higher than {@link
         * IchnoisCoreWebConfiguration#httpServletRequestIchnoisCoreUserProvider(IchnoisCoreOptions)}
         *
         * @param ichnoisOptions the IchnoisCore options
         * @return {@link SpringSecurityIchnoisCoreUserProvider}
         */
        @Bean
        @Order(1)
        public @NotNull SpringSecurityIchnoisCoreUserProvider springSecurityIchnoisCoreUserProvider(
            final @NotNull IchnoisCoreOptions ichnoisOptions) {
          return new SpringSecurityIchnoisCoreUserProvider(ichnoisOptions);
        }
      }

      /**
       * Configures {@link IchnoisCoreUserFilter}. By default it runs as the last filter in order to make
       * sure that all potential authentication information is propagated to {@link
       * HttpServletRequest#getUserPrincipal()}. If Spring Security is auto-configured, its order is
       * set to run after Spring Security.
       *
       * @param scopes the IchnoisCore scopes
       * @param ichnoisProperties the IchnoisCore properties
       * @param ichnoisUserProvider the user provider
       * @return {@link IchnoisCoreUserFilter} registration bean
       */
      @Bean
      @ConditionalOnBean(IchnoisCoreUserProvider.class)
      public @NotNull FilterRegistrationBean<IchnoisCoreUserFilter> ichnoisUserFilter(
          final @NotNull IScopes scopes,
          final @NotNull IchnoisCoreProperties ichnoisProperties,
          final @NotNull List<IchnoisCoreUserProvider> ichnoisUserProvider) {
        final FilterRegistrationBean<IchnoisCoreUserFilter> filter = new FilterRegistrationBean<>();
        filter.setFilter(new IchnoisCoreUserFilter(scopes, ichnoisUserProvider));
        filter.setOrder(resolveUserFilterOrder(ichnoisProperties));
        return filter;
      }

      private @NotNull Integer resolveUserFilterOrder(
          final @NotNull IchnoisCoreProperties ichnoisProperties) {
        return Optional.ofNullable(ichnoisProperties.getUserFilterOrder())
            .orElse(Ordered.LOWEST_PRECEDENCE);
      }

      @Bean
      public @NotNull IchnoisCoreRequestResolver ichnoisRequestResolver(final @NotNull IScopes scopes) {
        return new IchnoisCoreRequestResolver(scopes);
      }

      @Bean
      @ConditionalOnMissingBean(name = "ichnoisSpringFilter")
      public @NotNull FilterRegistrationBean<IchnoisCoreSpringFilter> ichnoisSpringFilter(
          final @NotNull IScopes scopes,
          final @NotNull IchnoisCoreRequestResolver requestResolver,
          final @NotNull TransactionNameProvider transactionNameProvider) {
        FilterRegistrationBean<IchnoisCoreSpringFilter> filter =
            new FilterRegistrationBean<>(
                new IchnoisCoreSpringFilter(scopes, requestResolver, transactionNameProvider));
        filter.setOrder(ICHNOIS_SPRING_FILTER_PRECEDENCE);
        return filter;
      }

      @Bean
      @ConditionalOnMissingBean(name = "ichnoisTracingFilter")
      public FilterRegistrationBean<IchnoisCoreTracingFilter> ichnoisTracingFilter(
          final @NotNull IScopes scopes,
          final @NotNull TransactionNameProvider transactionNameProvider,
          final @NotNull IchnoisCoreProperties ichnoisProperties) {
        FilterRegistrationBean<IchnoisCoreTracingFilter> filter =
            new FilterRegistrationBean<>(
                new IchnoisCoreTracingFilter(
                    scopes,
                    transactionNameProvider,
                    ichnoisProperties.isKeepTransactionsOpenForAsyncResponses()));
        filter.setOrder(ICHNOIS_SPRING_FILTER_PRECEDENCE + 1); // must run after IchnoisCoreSpringFilter
        return filter;
      }

      @Configuration(proxyBeanMethods = false)
      @ConditionalOnClass(HandlerExceptionResolver.class)
      @Open
      static class IchnoisCoreMvcModeConfig {

        @Bean
        @ConditionalOnMissingBean
        public @NotNull IchnoisCoreExceptionResolver ichnoisExceptionResolver(
            final @NotNull IScopes scopes,
            final @NotNull TransactionNameProvider transactionNameProvider,
            final @NotNull IchnoisCoreProperties options) {
          return new IchnoisCoreExceptionResolver(
              scopes, transactionNameProvider, options.getExceptionResolverOrder());
        }

        @Bean
        @ConditionalOnMissingBean(TransactionNameProvider.class)
        public @NotNull TransactionNameProvider transactionNameProvider() {
          return new CombinedTransactionNameProvider(
              Arrays.asList(
                  new SpringMvcTransactionNameProvider(),
                  new SpringServletTransactionNameProvider()));
        }
      }

      @Configuration(proxyBeanMethods = false)
      @ConditionalOnMissingClass("org.springframework.web.servlet.HandlerExceptionResolver")
      @Open
      static class IchnoisCoreServletModeConfig {

        @Bean
        @ConditionalOnMissingBean(TransactionNameProvider.class)
        public @NotNull TransactionNameProvider transactionNameProvider() {
          return new SpringServletTransactionNameProvider();
        }
      }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ProceedingJoinPoint.class)
    @ConditionalOnProperty(
        value = "ichnois.enable-aot-compatibility",
        havingValue = "false",
        matchIfMissing = true)
    @Import(IchnoisCoreExceptionParameterAdviceConfiguration.class)
    @Open
    static class IchnoisCoreErrorAspectsConfiguration {
      @Configuration(proxyBeanMethods = false)
      @ConditionalOnMissingBean(name = "ichnoisCaptureExceptionParameterPointcut")
      @Import(IchnoisCoreCaptureExceptionParameterPointcutConfiguration.class)
      @Open
      static class IchnoisCoreCaptureExceptionParameterPointcutAutoConfiguration {}
    }

    @Configuration(proxyBeanMethods = false)
    @Conditional(IchnoisCoreTracingCondition.class)
    @ConditionalOnClass(ProceedingJoinPoint.class)
    @Import(IchnoisCoreAdviceConfiguration.class)
    @Open
    static class IchnoisCorePerformanceAspectsConfiguration {

      @Configuration(proxyBeanMethods = false)
      @ConditionalOnMissingBean(name = "ichnoisTransactionPointcut")
      @Import(IchnoisCoreTransactionPointcutConfiguration.class)
      @Open
      static class IchnoisCoreTransactionPointcutAutoConfiguration {}

      @Configuration(proxyBeanMethods = false)
      @ConditionalOnMissingBean(name = "ichnoisSpanPointcut")
      @Import(IchnoisCoreSpanPointcutConfiguration.class)
      @Open
      static class IchnoisCoreSpanPointcutAutoConfiguration {}
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureBefore(RestTemplateAutoConfiguration.class)
    @ConditionalOnClass(RestTemplate.class)
    @Open
    static class IchnoisCorePerformanceRestTemplateConfiguration {
      @Bean
      public IchnoisCoreSpanRestTemplateCustomizer ichnoisSpanRestTemplateCustomizer(IScopes scopes) {
        return new IchnoisCoreSpanRestTemplateCustomizer(scopes);
      }
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigureBefore(WebClientAutoConfiguration.class)
    @ConditionalOnClass(WebClient.class)
    @Open
    static class IchnoisCorePerformanceWebClientConfiguration {
      @Bean
      public IchnoisCoreSpanWebClientCustomizer ichnoisSpanWebClientCustomizer(IScopes scopes) {
        return new IchnoisCoreSpanWebClientCustomizer(scopes);
      }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(ITransportFactory.class)
    @ConditionalOnClass(ApacheHttpClientTransportFactory.class)
    @Open
    static class ApacheHttpClientTransportFactoryAutoconfiguration {

      @Bean
      public @NotNull ApacheHttpClientTransportFactory apacheHttpClientTransportFactory() {
        return new ApacheHttpClientTransportFactory();
      }
    }

    private static @NotNull SdkVersion createSdkVersion(
        final @NotNull IchnoisCoreOptions ichnoisOptions) {
      SdkVersion sdkVersion = ichnoisOptions.getSdkVersion();

      final String name = BuildConfig.ICHNOIS_SPRING_BOOT_SDK_NAME;
      final String version = BuildConfig.VERSION_NAME;
      sdkVersion = SdkVersion.updateSdkVersion(sdkVersion, name, version);

      return sdkVersion;
    }

    private static void addPackageAndIntegrationInfo() {
      IchnoisCoreIntegrationPackageStorage.getInstance().addIntegration("SpringBoot");
    }
  }

  static final class IchnoisCoreTracingCondition extends AnyNestedCondition {

    public IchnoisCoreTracingCondition() {
      super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(name = "ichnois.traces-sample-rate")
    @SuppressWarnings("UnusedNestedClass")
    private static class IchnoisCoreTracesSampleRateCondition {}

    @ConditionalOnBean(IchnoisCoreOptions.TracesSamplerCallback.class)
    @SuppressWarnings("UnusedNestedClass")
    private static class IchnoisCoreTracesSamplerBeanCondition {}
  }

  @Configuration(proxyBeanMethods = false)
  @Open
  static class SpringProfilesEventProcessorConfiguration {
    @Bean
    public @NotNull SpringProfilesEventProcessor springProfilesEventProcessor(
        final Environment environment) {
      return new SpringProfilesEventProcessor(environment);
    }
  }
}
