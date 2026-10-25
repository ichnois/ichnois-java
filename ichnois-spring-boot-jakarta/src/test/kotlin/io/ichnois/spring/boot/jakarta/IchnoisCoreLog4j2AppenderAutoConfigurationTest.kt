package io.ichnois.spring.boot.jakarta

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ichnois.ITransportFactory
import io.ichnois.NoOpTransportFactory
import io.ichnois.ScopesAdapter
import io.ichnois.log4j2.IchnoisCoreAppender
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.DefaultConfiguration
import org.apache.logging.log4j.core.config.LoggerConfig
import org.assertj.core.api.Assertions.assertThat
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class IchnoisCoreLog4j2AppenderAutoConfigurationTest {

  private val baseContextRunner =
    ApplicationContextRunner()
      .withConfiguration(
        AutoConfigurations.of(
          IchnoisCoreLog4j2AppenderAutoConfiguration::class.java,
          IchnoisCoreAutoConfiguration::class.java,
        )
      )
      .withPropertyValues(
        "ichnois.shutdownTimeoutMillis=0",
        "ichnois.sessionFlushTimeoutMillis=0",
        "ichnois.flushTimeoutMillis=0",
        "ichnois.readTimeoutMillis=50",
        "ichnois.connectionTimeoutMillis=50",
        "ichnois.send-modules=false",
        "ichnois.attach-stacktrace=false",
        "ichnois.attach-threads=false",
        "ichnois.enable-backpressure-handling=false",
        "ichnois.enable-spotlight=false",
        "ichnois.debug=false",
        "ichnois.max-breadcrumbs=0",
      )

  private val contextRunner =
    baseContextRunner
      .withLog4j2CoreProvider()
      .withPropertyValues("ichnois.logging.enabled=true")
      .withUserConfiguration(NoOpTransportConfiguration::class.java)

  private val dsnOnlyRunner =
    baseContextRunner
      .withLog4j2CoreProvider()
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(NoOpTransportConfiguration::class.java)

  private val dsnEnabledRunner = dsnOnlyRunner.withPropertyValues("ichnois.logging.enabled=true")

  // Hide the Log4j2 Core provider so LogManager uses the Log4j-to-SLF4J bridge.
  private val log4j2BridgeDsnEnabledRunner =
    baseContextRunner
      .withClassLoader(FilteredClassLoader("org.apache.logging.log4j.core.impl.Log4jProvider"))
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj", "ichnois.logging.enabled=true")
      .withUserConfiguration(NoOpTransportConfiguration::class.java)

  private val originalLogManagerFactory = LogManager.getFactory()

  private val loggerContext: LoggerContext
    get() = LogManager.getContext(false) as LoggerContext

  private val configuration
    get() = loggerContext.configuration

  private val rootLogger
    get() = configuration.rootLogger

  @BeforeTest
  fun `reset Log4j2 context`() {
    useLog4j2Core()
    resetLog4j2Context()
  }

  @AfterTest
  fun `restore Log4j2 context`() {
    useLog4j2Core()
    resetLog4j2Context()
    LogManager.setFactory(originalLogManagerFactory)
  }

  @Test
  fun `does not configure IchnoisCoreAppender when auto-configuration dsn is not set`() {
    contextRunner.run { assertThat(rootLogger.getAppenders(IchnoisCoreAppender::class.java)).isEmpty() }
  }

  @Test
  fun `does not configure IchnoisCoreAppender when logging is not enabled`() {
    dsnOnlyRunner.run { assertThat(rootLogger.getAppenders(IchnoisCoreAppender::class.java)).isEmpty() }
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
        val fooBarLogger = configuration.getLoggerConfig("foo.bar")
        val bazLogger = configuration.getLoggerConfig("baz")

        assertThat(rootLogger.getAppenders(IchnoisCoreAppender::class.java)).hasSize(0)
        assertThat(fooBarLogger.getAppenders(IchnoisCoreAppender::class.java)).hasSize(1)
        assertThat(bazLogger.getAppenders(IchnoisCoreAppender::class.java)).hasSize(1)
      }
  }

  @Test
  fun `configures IchnoisCoreAppender for descendant logger with additivity disabled`() {
    val loggerConfig = LoggerConfig("com.example", null, false)
    configuration.addLogger("com.example", loggerConfig)
    loggerContext.updateLoggers(configuration)

    dsnEnabledRunner
      .withPropertyValues("ichnois.logging.loggers[0]=ROOT", "ichnois.logging.loggers[1]=com.example")
      .run {
        assertThat(rootLogger.getAppenders(IchnoisCoreAppender::class.java)).hasSize(1)
        assertThat(
            configuration.getLoggerConfig("com.example").getAppenders(IchnoisCoreAppender::class.java)
          )
          .hasSize(1)
      }
  }

  @Test
  fun `configures IchnoisCoreAppender for none of the loggers if so configured`() {
    dsnEnabledRunner.withPropertyValues("ichnois.logging.loggers=").run {
      val fooBarLogger = configuration.getLoggerConfig("foo.bar")
      val bazLogger = configuration.getLoggerConfig("baz")

      assertThat(rootLogger.getAppenders(IchnoisCoreAppender::class.java)).hasSize(0)
      assertThat(fooBarLogger.getAppenders(IchnoisCoreAppender::class.java)).hasSize(0)
      assertThat(bazLogger.getAppenders(IchnoisCoreAppender::class.java)).hasSize(0)
    }
  }

  @Test
  fun `does not overwrite Spring Boot IchnoisCore options`() {
    dsnEnabledRunner.withPropertyValues("ichnois.environment=boot-env").run {
      assertThat(rootLogger.getAppenders(IchnoisCoreAppender::class.java)).hasSize(1)
      assertThat(ScopesAdapter.getInstance().options.environment).isEqualTo("boot-env")
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
    dsnEnabledRunner.withPropertyValues("ichnois.logging.enabled=false").run {
      assertThat(rootLogger.getAppenders(IchnoisCoreAppender::class.java)).isEmpty()
    }
  }

  @Test
  fun `does not configure IchnoisCoreAppender when appender is already configured`() {
    val ichnoisAppender =
      IchnoisCoreAppender(
        "customAppender",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        io.ichnois.ScopesAdapter.getInstance(),
        null,
      )
    ichnoisAppender.start()
    configuration.addAppender(ichnoisAppender)
    rootLogger.addAppender(ichnoisAppender, null, null)
    loggerContext.updateLoggers()

    dsnEnabledRunner.run {
      val appenders = rootLogger.getAppenders(IchnoisCoreAppender::class.java)
      assertThat(appenders).hasSize(1)
      assertThat(appenders.first().name).isEqualTo("customAppender")
    }
  }

  @Test
  fun `does not configure IchnoisCoreAppender when active Log4j2 context is not Log4j2 Core`() {
    val logbackLogger = LoggerFactory.getLogger(IchnoisCoreLog4j2Initializer::class.java) as Logger
    val listAppender = ListAppender<ILoggingEvent>()
    listAppender.start()
    logbackLogger.addAppender(listAppender)

    try {
      useSlf4jBridge()
      log4j2BridgeDsnEnabledRunner.run {
        assertThat(listAppender.list.map { it.formattedMessage }).anyMatch {
          it.contains("IchnoisCore Log4j2 appender was not configured")
        }
      }
    } finally {
      logbackLogger.detachAppender(listAppender)
      listAppender.stop()
    }
  }

  @Test
  fun `does not configure IchnoisCoreAppender when log4j2 is not on the classpath`() {
    baseContextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj", "ichnois.logging.enabled=true")
      .withClassLoader(FilteredClassLoader(LoggerContext::class.java))
      .run { assertThat(rootLogger.getAppenders(IchnoisCoreAppender::class.java)).isEmpty() }
  }

  @Test
  fun `does not configure IchnoisCoreAppender when ichnois-log4j2 module is not on the classpath`() {
    baseContextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj", "ichnois.logging.enabled=true")
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

fun <T> org.apache.logging.log4j.core.config.LoggerConfig.getAppenders(
  clazz: Class<T>
): List<Appender> {
  return this.appenders.values.filter { it.javaClass == clazz }
}

private fun ApplicationContextRunner.withLog4j2CoreProvider(): ApplicationContextRunner =
  // Hide the Log4j-to-SLF4J provider so LogManager uses Log4j2 Core in these tests.
  withClassLoader(FilteredClassLoader("org.apache.logging.slf4j"))

private fun useLog4j2Core() {
  LogManager.setFactory(org.apache.logging.log4j.core.impl.Log4jContextFactory())
}

private fun useSlf4jBridge() {
  val factory =
    Class.forName("org.apache.logging.slf4j.SLF4JLoggerContextFactory")
      .getDeclaredConstructor()
      .newInstance() as org.apache.logging.log4j.spi.LoggerContextFactory
  LogManager.setFactory(factory)
}

private fun resetLog4j2Context() {
  val loggerContext = LogManager.getContext(false) as? LoggerContext ?: return
  loggerContext.reconfigure(DefaultConfiguration())
}
