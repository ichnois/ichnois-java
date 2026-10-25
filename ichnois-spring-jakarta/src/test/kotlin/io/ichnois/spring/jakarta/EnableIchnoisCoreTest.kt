package io.ichnois.spring.jakarta

import io.ichnois.EventProcessor
import io.ichnois.IScopes
import io.ichnois.ITransportFactory
import io.ichnois.Integration
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreOptions
import io.ichnois.transport.ITransport
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.context.annotation.UserConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean

class EnableIchnoisCoreTest {
  private val contextRunner =
    ApplicationContextRunner().withConfiguration(UserConfigurations.of(AppConfig::class.java))

  @Test
  fun `sets properties from environment on IchnoisCoreOptions`() {
    ApplicationContextRunner()
      .withConfiguration(UserConfigurations.of(AppConfigWithDefaultSendPii::class.java))
      .run {
        assertThat(it).hasSingleBean(IchnoisCoreOptions::class.java)
        val options = it.getBean(IchnoisCoreOptions::class.java)
        assertThat(options.dsn).isEqualTo("http://key@localhost/proj")
        assertThat(options.isSendDefaultPii).isTrue()
      }

    ApplicationContextRunner()
      .withConfiguration(UserConfigurations.of(AppConfigWithEmptyDsn::class.java))
      .run {
        assertThat(it).hasSingleBean(IchnoisCoreOptions::class.java)
        val options = it.getBean(IchnoisCoreOptions::class.java)
        assertThat(options.dsn).isEmpty()
        assertThat(options.isSendDefaultPii).isFalse()
      }
  }

  @Test
  fun `sets client name and SDK version`() {
    contextRunner.run {
      assertThat(it).hasSingleBean(IchnoisCoreOptions::class.java)
      val options = it.getBean(IchnoisCoreOptions::class.java)
      assertThat(options.ichnoisClientName).isEqualTo("ichnois.java.spring.jakarta")
      assertThat(options.sdkVersion).isNotNull
      assertThat(options.sdkVersion!!.name).isEqualTo("ichnois.java.spring.jakarta")
      assertThat(options.sdkVersion!!.version).isEqualTo(BuildConfig.VERSION_NAME)
      assertThat(options.sdkVersion!!.packageSet.map { pkg -> pkg.name })
        .contains("maven:io.ichnois:ichnois-spring-jakarta")
      assertThat(options.sdkVersion!!.integrationSet).contains("Spring6")
    }
  }

  @Test
  fun `enables external configuration on IchnoisCoreOptions`() {
    contextRunner.run {
      assertThat(it).hasSingleBean(IchnoisCoreOptions::class.java)
      val options = it.getBean(IchnoisCoreOptions::class.java)
      assertThat(options.isEnableExternalConfiguration).isTrue()
    }
  }

  @Test
  fun `creates IchnoisCore Hub`() {
    contextRunner.run { assertThat(it).hasSingleBean(IScopes::class.java) }
  }

  @Test
  fun `creates IchnoisCoreExceptionResolver`() {
    contextRunner.run {
      assertThat(it).hasSingleBean(IchnoisCoreExceptionResolver::class.java)
      assertThat(it)
        .getBean(IchnoisCoreExceptionResolver::class.java)
        .hasFieldOrPropertyWithValue("order", 1)
    }
  }

  @Test
  fun `creates IchnoisCoreExceptionResolver with order set in the @EnableIchnoisCore annotation`() {
    ApplicationContextRunner()
      .withConfiguration(
        UserConfigurations.of(AppConfigWithExceptionResolverOrderIntegerMaxValue::class.java)
      )
      .run {
        assertThat(it).hasSingleBean(IchnoisCoreExceptionResolver::class.java)
        assertThat(it)
          .getBean(IchnoisCoreExceptionResolver::class.java)
          .hasFieldOrPropertyWithValue("order", Integer.MAX_VALUE)
      }
  }

  @Test
  fun `configures custom TracesSamplerCallback`() {
    ApplicationContextRunner()
      .withConfiguration(
        UserConfigurations.of(AppConfigWithCustomTracesSamplerCallback::class.java)
      )
      .run {
        val options = it.getBean(IchnoisCoreOptions::class.java)
        val samplerCallback = it.getBean(IchnoisCoreOptions.TracesSamplerCallback::class.java)
        assertThat(options.tracesSampler).isEqualTo(samplerCallback)
      }
  }

  @Test
  fun `configures custom TransportFactory`() {
    ApplicationContextRunner()
      .withConfiguration(UserConfigurations.of(AppConfigWithCustomTransportFactory::class.java))
      .run {
        val options = it.getBean(IchnoisCoreOptions::class.java)
        val transportFactory = it.getBean(ITransportFactory::class.java)
        assertThat(options.transportFactory).isEqualTo(transportFactory)
      }
  }

  @Test
  fun `configures options with options configuration`() {
    ApplicationContextRunner()
      .withConfiguration(UserConfigurations.of(AppConfigWithCustomOptionsConfiguration::class.java))
      .run {
        val options = it.getBean(IchnoisCoreOptions::class.java)
        assertThat(options.environment).isEqualTo("from-options-configuration")
      }
  }

  @Test
  fun `configures custom before send callback`() {
    ApplicationContextRunner()
      .withConfiguration(UserConfigurations.of(AppConfigWithCustomBeforeSendCallback::class.java))
      .run {
        val beforeSendCallback = it.getBean(IchnoisCoreOptions.BeforeSendCallback::class.java)
        val options = it.getBean(IchnoisCoreOptions::class.java)
        assertThat(options.beforeSend).isEqualTo(beforeSendCallback)
      }
  }

  @Test
  fun `configures custom before breadcrumb callback`() {
    ApplicationContextRunner()
      .withConfiguration(
        UserConfigurations.of(AppConfigWithCustomBeforeBreadcrumbCallback::class.java)
      )
      .run {
        val beforeBreadcrumbCallback =
          it.getBean(IchnoisCoreOptions.BeforeBreadcrumbCallback::class.java)
        val options = it.getBean(IchnoisCoreOptions::class.java)
        assertThat(options.beforeBreadcrumb).isEqualTo(beforeBreadcrumbCallback)
      }
  }

  @Test
  fun `configures custom event processors`() {
    ApplicationContextRunner()
      .withConfiguration(UserConfigurations.of(AppConfigWithCustomEventProcessors::class.java))
      .run {
        val firstProcessor = it.getBean("firstProcessor", EventProcessor::class.java)
        val secondProcessor = it.getBean("secondProcessor", EventProcessor::class.java)
        val options = it.getBean(IchnoisCoreOptions::class.java)
        assertThat(options.eventProcessors).contains(firstProcessor, secondProcessor)
      }
  }

  @Test
  fun `configures custom integrations`() {
    ApplicationContextRunner()
      .withConfiguration(UserConfigurations.of(AppConfigWithCustomIntegrations::class.java))
      .run {
        val firstIntegration = it.getBean("firstIntegration", Integration::class.java)
        val secondIntegration = it.getBean("secondIntegration", Integration::class.java)
        val options = it.getBean(IchnoisCoreOptions::class.java)
        assertThat(options.integrations).contains(firstIntegration, secondIntegration)
      }
  }

  @EnableIchnoisCore(dsn = "http://key@localhost/proj") class AppConfig

  @EnableIchnoisCore(dsn = "") class AppConfigWithEmptyDsn

  @EnableIchnoisCore(dsn = "http://key@localhost/proj", sendDefaultPii = true)
  class AppConfigWithDefaultSendPii

  @EnableIchnoisCore(dsn = "http://key@localhost/proj", exceptionResolverOrder = Integer.MAX_VALUE)
  class AppConfigWithExceptionResolverOrderIntegerMaxValue

  @EnableIchnoisCore(dsn = "http://key@localhost/proj")
  class AppConfigWithCustomTracesSamplerCallback {
    @Bean
    fun tracesSampler(): IchnoisCoreOptions.TracesSamplerCallback {
      return IchnoisCoreOptions.TracesSamplerCallback {
        return@TracesSamplerCallback 1.0
      }
    }
  }

  @EnableIchnoisCore(dsn = "http://key@localhost/proj")
  class AppConfigWithCustomTransportFactory {
    @Bean
    fun transport() =
      mock<ITransportFactory>().also {
        whenever(it.create(any(), any())).thenReturn(mock<ITransport>())
      }
  }

  @EnableIchnoisCore(dsn = "http://key@localhost/proj")
  class AppConfigWithCustomOptionsConfiguration {
    @Bean
    fun optionsConfiguration() =
      IchnoisCore.OptionsConfiguration<IchnoisCoreOptions> { it.environment = "from-options-configuration" }
  }

  @EnableIchnoisCore(dsn = "http://key@localhost/proj")
  class AppConfigWithCustomBeforeSendCallback {
    @Bean fun beforeSendCallback() = mock<IchnoisCoreOptions.BeforeSendCallback>()
  }

  @EnableIchnoisCore(dsn = "http://key@localhost/proj")
  class AppConfigWithCustomBeforeBreadcrumbCallback {
    @Bean fun beforeBreadcrumbCallback() = mock<IchnoisCoreOptions.BeforeBreadcrumbCallback>()
  }

  @EnableIchnoisCore(dsn = "http://key@localhost/proj")
  class AppConfigWithCustomOnDiscardCallback {
    @Bean fun onDiscardCallback() = mock<IchnoisCoreOptions.OnDiscardCallback>()
  }

  @EnableIchnoisCore(dsn = "http://key@localhost/proj")
  class AppConfigWithCustomEventProcessors {
    @Bean fun firstProcessor() = mock<EventProcessor>()

    @Bean fun secondProcessor() = mock<EventProcessor>()
  }

  @EnableIchnoisCore(dsn = "http://key@localhost/proj")
  class AppConfigWithCustomIntegrations {
    @Bean fun firstIntegration() = mock<Integration>()

    @Bean fun secondIntegration() = mock<Integration>()
  }
}
