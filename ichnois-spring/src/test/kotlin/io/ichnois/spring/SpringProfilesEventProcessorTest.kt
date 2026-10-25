package io.ichnois.spring

import io.ichnois.ITransportFactory
import io.ichnois.IchnoisCore
import io.ichnois.checkEvent
import io.ichnois.protocol.Spring
import io.ichnois.transport.ITransport
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment

class SpringProfilesEventProcessorTest {
  private val contextRunner =
    ApplicationContextRunner()
      .withUserConfiguration(AppConfiguration::class.java)
      .withUserConfiguration(SpringProfilesEventProcessorConfiguration::class.java)
      .withUserConfiguration(MockTransportConfiguration::class.java)

  @Test
  fun `when default Spring profile is active, sets active_profiles in Spring context to empty list on sent event`() {
    contextRunner.run {
      IchnoisCore.captureMessage("test")
      val transport = it.getBean(ITransport::class.java)
      verify(transport)
        .send(
          checkEvent { event ->
            val expected = Spring()
            expected.activeProfiles = listOf<String>().toTypedArray()
            assertThat(event.contexts.spring).isEqualTo(expected)
          },
          anyOrNull(),
        )
    }
  }

  @Test
  fun `when non-default Spring profiles are active, sets active profiles in Spring context to list of profile names`() {
    contextRunner.withPropertyValues("spring.profiles.active=test1,test2").run {
      IchnoisCore.captureMessage("test")
      val transport = it.getBean(ITransport::class.java)
      verify(transport)
        .send(
          checkEvent { event ->
            val expected = Spring()
            expected.activeProfiles = listOf("test1", "test2").toTypedArray()
            assertThat(event.contexts.spring).isEqualTo(expected)
          },
          anyOrNull(),
        )
    }
  }

  @EnableIchnoisCore(dsn = "http://key@localhost/proj") class AppConfiguration

  @Configuration(proxyBeanMethods = false)
  open class SpringProfilesEventProcessorConfiguration {
    @Bean
    open fun springProfilesEventProcessor(environment: Environment): SpringProfilesEventProcessor =
      SpringProfilesEventProcessor(environment)
  }

  @Configuration(proxyBeanMethods = false)
  open class MockTransportConfiguration {
    private val transport = mock<ITransport>()

    @Bean
    open fun mockTransportFactory(): ITransportFactory {
      val factory = mock<ITransportFactory>()
      whenever(factory.create(any(), any())).thenReturn(transport)
      return factory
    }

    @Bean open fun ichnoisTransport() = transport
  }
}
