package io.ichnois.spring.boot.jakarta

import io.micrometer.context.ThreadLocalAccessor
import io.ichnois.ITransportFactory
import io.ichnois.NoOpTransportFactory
import io.ichnois.spring.jakarta.webflux.IchnoisCoreWebExceptionHandler
import io.ichnois.spring.jakarta.webflux.IchnoisCoreWebFilter
import io.ichnois.spring.jakarta.webflux.IchnoisCoreWebFilterWithThreadLocalAccessor
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.core.scheduler.Schedulers

class IchnoisCoreWebfluxAutoConfigurationTest {
  // Base context runner with performance optimizations
  private val baseContextRunner =
    ReactiveWebApplicationContextRunner()
      .withConfiguration(
        AutoConfigurations.of(
          WebFluxAutoConfiguration::class.java,
          IchnoisCoreWebfluxAutoConfiguration::class.java,
          IchnoisCoreAutoConfiguration::class.java,
        )
      )
      .withPropertyValues(
        // Speed up tests by reducing timeouts and disabling expensive operations
        "ichnois.shutdownTimeoutMillis=0",
        "ichnois.sessionFlushTimeoutMillis=0",
        "ichnois.flushTimeoutMillis=0",
        "ichnois.readTimeoutMillis=50",
        "ichnois.connectionTimeoutMillis=50",
        "ichnois.send-modules=false", // Disable expensive module sending
        "ichnois.attach-stacktrace=false", // Disable expensive stacktrace collection
        "ichnois.attach-threads=false", // Disable expensive thread info
        "ichnois.enable-backpressure-handling=false",
        "ichnois.enable-spotlight=false",
        "ichnois.debug=false",
        "ichnois.max-breadcrumbs=0", // Disable breadcrumb collection for performance
      )

  // Use the optimized base runner by default
  private val contextRunner =
    baseContextRunner.withUserConfiguration(
      NoOpTransportConfiguration::class.java
    ) // Use no-op transport to avoid network calls

  // Specialized context runner for tests requiring DSN
  private val dsnEnabledRunner =
    baseContextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(
        NoOpTransportConfiguration::class.java
      ) // Use no-op transport to avoid network calls

  @Test
  fun `configures ichnoisWebFilter`() {
    dsnEnabledRunner.run {
      assertThat(it).hasSingleBean(IchnoisCoreWebFilterWithThreadLocalAccessor::class.java)
      assertThat(it).doesNotHaveBean(IchnoisCoreWebFilter::class.java)
    }
  }

  @Test
  fun `configures exception handler`() {
    dsnEnabledRunner.run { assertThat(it).hasSingleBean(IchnoisCoreWebExceptionHandler::class.java) }
  }

  @Test
  fun `does not run when reactor is not on the classpath`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withClassLoader(FilteredClassLoader(Schedulers::class.java))
      .run {
        assertThat(it).doesNotHaveBean(IchnoisCoreWebExceptionHandler::class.java)
        assertThat(it).doesNotHaveBean(IchnoisCoreWebFilter::class.java)
      }
  }

  @Test
  fun `does not run when dsn is not configured`() {
    contextRunner.withClassLoader(FilteredClassLoader(Schedulers::class.java)).run {
      assertThat(it).doesNotHaveBean(IchnoisCoreWebExceptionHandler::class.java)
      assertThat(it).doesNotHaveBean(IchnoisCoreWebFilter::class.java)
    }
  }

  @Test
  fun `configures web filter with ThreadLocalAccessor support if available and enabled`() {
    dsnEnabledRunner.withPropertyValues("ichnois.reactive.thread-local-accessor-enabled=true").run {
      assertThat(it).hasSingleBean(IchnoisCoreWebFilterWithThreadLocalAccessor::class.java)
      assertThat(it).doesNotHaveBean(IchnoisCoreWebFilter::class.java)
    }
  }

  @Test
  fun `does not configure web filter with ThreadLocalAccessor support if disabled`() {
    dsnEnabledRunner.withPropertyValues("ichnois.reactive.thread-local-accessor-enabled=false").run {
      assertThat(it).doesNotHaveBean(IchnoisCoreWebFilterWithThreadLocalAccessor::class.java)
      assertThat(it).hasSingleBean(IchnoisCoreWebFilter::class.java)
    }
  }

  @Test
  fun `does not configure web filter with ThreadLocalAccessor support if not available`() {
    contextRunner
      .withPropertyValues(
        "ichnois.dsn=http://key@localhost/proj",
        "ichnois.reactive.thread-local-accessor-enabled=true",
      )
      .withClassLoader(FilteredClassLoader(ThreadLocalAccessor::class.java))
      .run { assertThat(it).doesNotHaveBean(IchnoisCoreWebFilterWithThreadLocalAccessor::class.java) }
  }

  @Configuration(proxyBeanMethods = false)
  open class NoOpTransportConfiguration {

    @Bean
    open fun noOpTransportFactory(): ITransportFactory {
      return NoOpTransportFactory.getInstance()
    }
  }
}
