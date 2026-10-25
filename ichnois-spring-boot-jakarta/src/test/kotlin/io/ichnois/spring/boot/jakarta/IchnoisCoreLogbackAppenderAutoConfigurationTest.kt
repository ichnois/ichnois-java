package io.ichnois.spring.boot.jakarta

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import io.ichnois.ITransportFactory
import io.ichnois.NoOpTransportFactory
import io.ichnois.logback.IchnoisCoreAppender
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class IchnoisCoreLogbackAppenderAutoConfigurationTest {

  // Base context runner with performance optimizations
  private val baseContextRunner =
    ApplicationContextRunner()
      .withConfiguration(
        AutoConfigurations.of(
          IchnoisCoreLogbackAppenderAutoConfiguration::class.java,
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

  private val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger

  @BeforeTest
  fun `reset Logback context`() {
    val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
    loggerContext.reset()
  }

  @Test
  fun `does not configure IchnoisCoreAppender when auto-configuration dsn is not set`() {
    contextRunner.run { assertThat(rootLogger.getAppenders(IchnoisCoreAppender::class.java)).isEmpty() }
  }

  @Test
  fun `configures IchnoisCoreAppender`() {
    dsnEnabledRunner.run {
      assertThat(rootLogger.getAppenders(IchnoisCoreAppender::class.java)).hasSize(1)
    }
  }

  @Test
  fun `configures IchnoisCoreAppender for configured loggers`() {
    dsnEnabledRunner
      .withPropertyValues("ichnois.logging.loggers[0]=foo.bar", "ichnois.logging.loggers[1]=baz")
      .run {
        val fooBarLogger = LoggerFactory.getLogger("foo.bar") as Logger
        val bazLogger = LoggerFactory.getLogger("baz") as Logger

        assertThat(rootLogger.getAppenders(IchnoisCoreAppender::class.java)).hasSize(0)
        assertThat(fooBarLogger.getAppenders(IchnoisCoreAppender::class.java)).hasSize(1)
        assertThat(bazLogger.getAppenders(IchnoisCoreAppender::class.java)).hasSize(1)
      }
  }

  @Test
  fun `configures IchnoisCoreAppender for none of the loggers if so configured`() {
    dsnEnabledRunner.withPropertyValues("ichnois.logging.loggers=").run {
      val fooBarLogger = LoggerFactory.getLogger("foo.bar") as Logger
      val bazLogger = LoggerFactory.getLogger("baz") as Logger

      assertThat(rootLogger.getAppenders(IchnoisCoreAppender::class.java)).hasSize(0)
      assertThat(fooBarLogger.getAppenders(IchnoisCoreAppender::class.java)).hasSize(0)
      assertThat(bazLogger.getAppenders(IchnoisCoreAppender::class.java)).hasSize(0)
    }
  }

  @Test
  fun `sets IchnoisCoreAppender properties`() {
    dsnEnabledRunner
      .withPropertyValues(
        "ichnois.logging.minimum-event-level=info",
        "ichnois.logging.minimum-breadcrumb-level=debug",
        "ichnois.logging.minimum-level=error",
      )
      .run {
        val appenders = rootLogger.getAppenders(IchnoisCoreAppender::class.java)
        assertThat(appenders).hasSize(1)
        val ichnoisAppender = appenders[0] as IchnoisCoreAppender

        assertThat(ichnoisAppender.minimumBreadcrumbLevel).isEqualTo(Level.DEBUG)
        assertThat(ichnoisAppender.minimumEventLevel).isEqualTo(Level.INFO)
        assertThat(ichnoisAppender.minimumLevel).isEqualTo(Level.ERROR)
      }
  }

  @Test
  fun `does not configure IchnoisCoreAppender when logging is disabled`() {
    contextRunner.withPropertyValues("ichnois.logging.enabled=false").run {
      assertThat(rootLogger.getAppenders(IchnoisCoreAppender::class.java)).isEmpty()
    }
  }

  @Test
  fun `does not configure IchnoisCoreAppender when appender is already configured`() {
    val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
    val ichnoisAppender = IchnoisCoreAppender()
    ichnoisAppender.name = "customAppender"
    ichnoisAppender.context = loggerContext
    ichnoisAppender.start()
    rootLogger.addAppender(ichnoisAppender)

    dsnEnabledRunner.run {
      val appenders = rootLogger.getAppenders(IchnoisCoreAppender::class.java)
      assertThat(appenders).hasSize(1)
      assertThat(appenders.first().name).isEqualTo("customAppender")
    }
  }

  @Test
  fun `does not configure IchnoisCoreAppender when logback is not on the classpath`() {
    baseContextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withClassLoader(FilteredClassLoader(LoggerContext::class.java))
      .run { assertThat(rootLogger.getAppenders(IchnoisCoreAppender::class.java)).isEmpty() }
  }

  @Test
  fun `does not configure IchnoisCoreAppender when ichnois-logback module is not on the classpath`() {
    baseContextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withClassLoader(FilteredClassLoader(IchnoisCoreAppender::class.java))
      .run { assertThat(rootLogger.getAppenders(IchnoisCoreAppender::class.java)).isEmpty() }
  }

  @Configuration(proxyBeanMethods = false)
  open class NoOpTransportConfiguration {

    @Bean
    open fun noOpTransportFactory(): ITransportFactory {
      return NoOpTransportFactory.getInstance()
    }
  }
}

fun <T> Logger.getAppenders(clazz: Class<T>): List<Appender<ILoggingEvent>> {
  return this.iteratorForAppenders().asSequence().toList().filter { it.javaClass == clazz }
}
