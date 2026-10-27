package io.ichnois.spring.boot.jakarta

import com.acme.MainBootClass
import io.opentelemetry.api.OpenTelemetry
import io.ichnois.AsyncHttpTransportFactory
import io.ichnois.Breadcrumb
import io.ichnois.DataCategory
import io.ichnois.EventProcessor
import io.ichnois.FilterString
import io.ichnois.Hint
import io.ichnois.IContinuousProfiler
import io.ichnois.IProfileConverter
import io.ichnois.IScopes
import io.ichnois.ITransportFactory
import io.ichnois.Integration
import io.ichnois.KeyValueCollectionBehavior
import io.ichnois.NoOpContinuousProfiler
import io.ichnois.NoOpProfileConverter
import io.ichnois.NoOpTransportFactory
import io.ichnois.ProfileLifecycle
import io.ichnois.SamplingContext
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreEvent
import io.ichnois.IchnoisCoreIntegrationPackageStorage
import io.ichnois.IchnoisCoreLevel
import io.ichnois.IchnoisCoreLogEvent
import io.ichnois.IchnoisCoreMetricsEvent
import io.ichnois.IchnoisCoreOptions
import io.ichnois.asyncprofiler.profiling.JavaContinuousProfiler
import io.ichnois.asyncprofiler.provider.AsyncProfilerProfileConverterProvider
import io.ichnois.checkEvent
import io.ichnois.clientreport.DiscardReason
import io.ichnois.opentelemetry.IchnoisCoreAutoConfigurationCustomizerProvider
import io.ichnois.opentelemetry.agent.AgentMarker
import io.ichnois.protocol.IchnoisCoreTransaction
import io.ichnois.protocol.User
import io.ichnois.quartz.IchnoisCoreJobListener
import io.ichnois.spring.jakarta.ContextTagsEventProcessor
import io.ichnois.spring.jakarta.HttpServletRequestIchnoisCoreUserProvider
import io.ichnois.spring.jakarta.IchnoisCoreExceptionResolver
import io.ichnois.spring.jakarta.IchnoisCoreUserFilter
import io.ichnois.spring.jakarta.IchnoisCoreUserProvider
import io.ichnois.spring.jakarta.SpringProfilesEventProcessor
import io.ichnois.spring.jakarta.SpringSecurityIchnoisCoreUserProvider
import io.ichnois.spring.jakarta.tracing.IchnoisCoreTracingFilter
import io.ichnois.spring.jakarta.tracing.SpringServletTransactionNameProvider
import io.ichnois.spring.jakarta.tracing.TransactionNameProvider
import io.ichnois.transport.ITransport
import io.ichnois.transport.ITransportGate
import io.ichnois.transport.apache.ApacheHttpClientTransportFactory
import jakarta.servlet.Filter
import java.io.File
import java.lang.RuntimeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.aspectj.lang.ProceedingJoinPoint
import org.assertj.core.api.Assertions.assertThat
import org.mockito.internal.util.MockUtil.isMock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.quartz.JobListener
import org.quartz.Scheduler
import org.quartz.core.QuartzScheduler
import org.slf4j.MDC
import org.springframework.aop.support.NameMatchMethodPointcut
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
import org.springframework.boot.context.annotation.UserConfigurations
import org.springframework.boot.info.GitProperties
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.assertj.ApplicationContextAssert
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.servlet.HandlerExceptionResolver

class IchnoisCoreAutoConfigurationTest {

  // Base context runner with performance optimizations
  private val baseContextRunner =
    WebApplicationContextRunner()
      .withConfiguration(
        AutoConfigurations.of(
          IchnoisCoreAutoConfiguration::class.java,
          WebMvcAutoConfiguration::class.java,
          IchnoisCoreProfilerAutoConfiguration::class.java,
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

  // Specialized context runners for different test categories
  private val dsnEnabledRunner =
    baseContextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(
        NoOpTransportConfiguration::class.java
      ) // Use no-op transport to avoid network calls

  private val tracingEnabledRunner =
    baseContextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj", "ichnois.traces-sample-rate=1.0")
      .withUserConfiguration(
        NoOpTransportConfiguration::class.java
      ) // Use no-op transport to avoid network calls

  @Test
  fun `scopes is not created when auto-configuration dsn is not set`() {
    contextRunner.run { assertThat(it).doesNotHaveBean(IScopes::class.java) }
  }

  @Test
  fun `scopes is created when dsn is provided`() {
    dsnEnabledRunner.run { assertThat(it).hasSingleBean(IScopes::class.java) }
  }

  @Test
  fun `OptionsConfiguration is created if custom one with name ichnoisOptionsConfiguration is not provided`() {
    dsnEnabledRunner.run { assertThat(it).hasSingleBean(IchnoisCore.OptionsConfiguration::class.java) }
  }

  @Test
  fun `OptionsConfiguration with name ichnoisOptionsConfiguration is created if another one with different name is provided`() {
    dsnEnabledRunner
      .withUserConfiguration(CustomOptionsConfigurationConfiguration::class.java)
      .run {
        assertThat(it).getBeans(IchnoisCore.OptionsConfiguration::class.java).hasSize(2)
        assertThat(it)
          .getBean("ichnoisOptionsConfiguration")
          .isNotNull()
          .isInstanceOf(IchnoisCore.OptionsConfiguration::class.java)
        assertThat(it)
          .getBean("customOptionsConfiguration")
          .isNotNull()
          .isInstanceOf(IchnoisCore.OptionsConfiguration::class.java)
      }
  }

  @Test
  fun `ichnoisOptionsConfiguration bean is configured before custom OptionsConfiguration`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(CustomOptionsConfigurationConfiguration::class.java)
      .run {
        val options = it.getBean(IchnoisCoreOptions::class.java)
        assertThat(options.beforeSend).isNull()
      }
  }

  @Test
  fun `OptionsConfiguration is not created if custom one with name ichnoisOptionsConfiguration is provided`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(OverridingOptionsConfigurationConfiguration::class.java)
      .run {
        assertThat(it).hasSingleBean(IchnoisCore.OptionsConfiguration::class.java)
        assertThat(
            it.getBean(IchnoisCore.OptionsConfiguration::class.java, "customOptionsConfiguration")
          )
          .isNotNull
      }
  }

  @Test
  fun `properties are applied to IchnoisCoreOptions`() {
    contextRunner
      .withPropertyValues(
        "ichnois.dsn=http://key@localhost/proj",
        "ichnois.read-timeout-millis=10",
        "ichnois.shutdown-timeout-millis=20",
        "ichnois.flush-timeout-millis=30",
        "ichnois.debug=true",
        "ichnois.diagnostic-level=INFO",
        "ichnois.ichnois-client-name=my-client",
        "ichnois.max-breadcrumbs=100",
        "ichnois.release=1.0.3",
        "ichnois.environment=production",
        "ichnois.sample-rate=0.2",
        "ichnois.in-app-includes=org.springframework,com.myapp",
        "ichnois.in-app-excludes=org.jboss,com.microsoft",
        "ichnois.dist=my-dist",
        "ichnois.attach-threads=true",
        "ichnois.attach-stacktrace=true",
        "ichnois.server-name=host-001",
        "ichnois.exception-resolver-order=100",
        "ichnois.proxy.host=example.proxy.com",
        "ichnois.proxy.port=8090",
        "ichnois.proxy.user=proxy-user",
        "ichnois.proxy.pass=proxy-pass",
        "ichnois.traces-sample-rate=0.3",
        "ichnois.tags.tag1=tag1-value",
        "ichnois.tags.tag2=tag2-value",
        "ichnois.ignored-exceptions-for-type=java.lang.RuntimeException,java.lang.IllegalStateException,io.ichnois.IchnoisCore",
        "ichnois.trace-propagation-targets=localhost,^(http|https)://api\\..*\$",
        "ichnois.enabled=false",
        "ichnois.send-modules=false",
        "ichnois.ignored-checkins=slug1,slugB",
        "ichnois.ignored-errors=Some error,Another .*",
        "ichnois.ignored-transactions=transactionName1,transactionNameB",
        "ichnois.enable-backpressure-handling=false",
        "ichnois.enable-database-transaction-tracing=true",
        "ichnois.enable-spotlight=true",
        "ichnois.spotlight-connection-url=http://local.ichnois.io:1234",
        "ichnois.force-init=true",
        "ichnois.global-hub-mode=true",
        "ichnois.capture-open-telemetry-events=true",
        "ichnois.cron.default-checkin-margin=10",
        "ichnois.cron.default-max-runtime=30",
        "ichnois.cron.default-timezone=America/New_York",
        "ichnois.cron.default-failure-issue-threshold=40",
        "ichnois.cron.default-recovery-threshold=50",
        "ichnois.logs.enabled=true",
        "ichnois.profile-session-sample-rate=1.0",
        "ichnois.profiling-traces-dir-path=tmp/ichnois/profiling-traces",
        "ichnois.profile-lifecycle=TRACE",
        "ichnois.strict-trace-continuation=true",
        "ichnois.org-id=12345",
      )
      .run {
        val options = it.getBean(IchnoisCoreProperties::class.java)
        assertThat(options.readTimeoutMillis).isEqualTo(10)
        assertThat(options.shutdownTimeoutMillis).isEqualTo(20)
        assertThat(options.flushTimeoutMillis).isEqualTo(30)
        assertThat(options.isDebug).isTrue()
        assertThat(options.diagnosticLevel).isEqualTo(IchnoisCoreLevel.INFO)
        assertThat(options.maxBreadcrumbs).isEqualTo(100)
        assertThat(options.release).isEqualTo("1.0.3")
        assertThat(options.environment).isEqualTo("production")
        assertThat(options.sampleRate).isEqualTo(0.2)
        assertThat(options.inAppIncludes).containsOnly("org.springframework", "com.myapp")
        assertThat(options.inAppExcludes).containsOnly("com.microsoft", "org.jboss")
        assertThat(options.dist).isEqualTo("my-dist")
        assertThat(options.isAttachThreads).isEqualTo(true)
        assertThat(options.isAttachStacktrace).isEqualTo(true)
        assertThat(options.serverName).isEqualTo("host-001")
        assertThat(options.exceptionResolverOrder).isEqualTo(100)
        assertThat(options.proxy).isNotNull
        assertThat(options.proxy!!.host).isEqualTo("example.proxy.com")
        assertThat(options.proxy!!.port).isEqualTo("8090")
        assertThat(options.proxy!!.user).isEqualTo("proxy-user")
        assertThat(options.proxy!!.pass).isEqualTo("proxy-pass")
        assertThat(options.tracesSampleRate).isEqualTo(0.3)
        assertThat(options.tags)
          .containsEntry("tag1", "tag1-value")
          .containsEntry("tag2", "tag2-value")
        assertThat(options.ignoredExceptionsForType)
          .containsOnly(RuntimeException::class.java, IllegalStateException::class.java)
        assertThat(options.tracePropagationTargets)
          .containsOnly("localhost", "^(http|https)://api\\..*\$")
        assertThat(options.isEnabled).isEqualTo(false)
        assertThat(options.isSendModules).isEqualTo(false)
        assertThat(options.ignoredCheckIns)
          .containsOnly(FilterString("slug1"), FilterString("slugB"))
        assertThat(options.ignoredErrors)
          .containsOnly(FilterString("Some error"), FilterString("Another .*"))
        assertThat(options.ignoredTransactions)
          .containsOnly(FilterString("transactionName1"), FilterString("transactionNameB"))
        assertThat(options.isEnableBackpressureHandling).isEqualTo(false)
        assertThat(options.isEnableDatabaseTransactionTracing).isEqualTo(true)
        assertThat(options.isForceInit).isEqualTo(true)
        assertThat(options.isGlobalHubMode).isEqualTo(true)
        assertThat(options.isCaptureOpenTelemetryEvents).isEqualTo(true)
        assertThat(options.isEnableSpotlight).isEqualTo(true)
        assertThat(options.spotlightConnectionUrl).isEqualTo("http://local.ichnois.io:1234")
        assertThat(options.cron).isNotNull
        assertThat(options.cron!!.defaultCheckinMargin).isEqualTo(10L)
        assertThat(options.cron!!.defaultMaxRuntime).isEqualTo(30L)
        assertThat(options.cron!!.defaultTimezone).isEqualTo("America/New_York")
        assertThat(options.cron!!.defaultFailureIssueThreshold).isEqualTo(40L)
        assertThat(options.cron!!.defaultRecoveryThreshold).isEqualTo(50L)
        assertThat(options.logs.isEnabled).isEqualTo(true)
        assertThat(options.profileSessionSampleRate).isEqualTo(1.0)
        assertThat(options.profilingTracesDirPath)
          .startsWith(File("tmp/ichnois/profiling-traces").absolutePath)
        assertThat(options.profileLifecycle).isEqualTo(ProfileLifecycle.TRACE)
        assertThat(options.isStrictTraceContinuation).isEqualTo(true)
        assertThat(options.orgId).isEqualTo("12345")
      }
  }

  @Test
  fun `data collection properties are applied to IchnoisCoreOptions`() {
    contextRunner
      .withPropertyValues(
        "ichnois.dsn=http://key@localhost/proj",
        "ichnois.data-collection.cookies.mode=off",
        "ichnois.data-collection.url-query-params.mode=allow-list",
        "ichnois.data-collection.url-query-params.terms=page,sort",
        "ichnois.data-collection.http-headers.request.mode=deny-list",
        "ichnois.data-collection.http-headers.request.terms=forwarded,-ip",
        "ichnois.data-collection.http-headers.response.mode=allow-list",
        "ichnois.data-collection.http-headers.response.terms=content-type,x-request-id",
        "ichnois.data-collection.file-paths=false",
      )
      .run {
        val dataCollection = it.getBean(IchnoisCoreProperties::class.java).dataCollection
        assertThat(dataCollection.cookies!!.mode).isEqualTo(KeyValueCollectionBehavior.Mode.OFF)
        assertThat(dataCollection.urlQueryParams!!.mode)
          .isEqualTo(KeyValueCollectionBehavior.Mode.ALLOW_LIST)
        assertThat(dataCollection.urlQueryParams!!.terms).containsExactly("page", "sort")
        assertThat(dataCollection.httpHeaders.request!!.mode)
          .isEqualTo(KeyValueCollectionBehavior.Mode.DENY_LIST)
        assertThat(dataCollection.httpHeaders.request!!.terms).containsExactly("forwarded", "-ip")
        assertThat(dataCollection.httpHeaders.response!!.mode)
          .isEqualTo(KeyValueCollectionBehavior.Mode.ALLOW_LIST)
        assertThat(dataCollection.httpHeaders.response!!.terms)
          .containsExactly("content-type", "x-request-id")
        assertThat(dataCollection.filePaths).isFalse()
      }
  }

  @Test
  fun `when tracePropagationTargets are not set, default is returned`() {
    contextRunner.withPropertyValues("ichnois.dsn=http://key@localhost/proj").run {
      val options = it.getBean(IchnoisCoreProperties::class.java)
      assertThat(options.tracePropagationTargets).isNotNull().containsOnly(".*")
    }
  }

  @Test
  fun `when tracePropagationTargets property is set to empty list, empty list is returned`() {
    contextRunner
      .withPropertyValues(
        "ichnois.dsn=http://key@localhost/proj",
        "ichnois.trace-propagation-targets=",
      )
      .run {
        val options = it.getBean(IchnoisCoreProperties::class.java)
        assertThat(options.tracePropagationTargets).isNotNull().isEmpty()
      }
  }

  @Test
  fun `when traces sample rate is set to null and tracing is enabled, traces sample rate should be set to 0`() {
    contextRunner.withPropertyValues("ichnois.dsn=http://key@localhost/proj").run {
      val options = it.getBean(IchnoisCoreProperties::class.java)
      assertThat(options.tracesSampleRate).isNull()
    }
  }

  @Test
  fun `when traces sample rate is set to a value and tracing is enabled, traces sample rate should not be overwritten`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj", "ichnois.traces-sample-rate=0.3")
      .run {
        val options = it.getBean(IchnoisCoreProperties::class.java)
        assertThat(options.tracesSampleRate).isNotNull().isEqualTo(0.3)
      }
  }

  @Test
  fun `sets ichnoisClientName property on IchnoisCoreOptions`() {
    contextRunner.withPropertyValues("ichnois.dsn=http://key@localhost/proj").run {
      assertThat(it.getBean(IchnoisCoreOptions::class.java).ichnoisClientName)
        .isEqualTo("ichnois.java.spring-boot.jakarta/${BuildConfig.VERSION_NAME}")
    }
  }

  @Test
  fun `sets SDK version on sent events`() {
    baseContextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(MockTransportConfiguration::class.java)
      .run {
        IchnoisCore.captureMessage("Some message")
        val transport = it.getBean(ITransport::class.java)
        verify(transport)
          .send(
            checkEvent { event ->
              assertThat(event.sdk).isNotNull
              val sdk = event.sdk!!
              assertThat(sdk.version).isEqualTo(BuildConfig.VERSION_NAME)
              assertThat(sdk.name).isEqualTo(BuildConfig.ICHNOIS_SPRING_BOOT_JAKARTA_SDK_NAME)
              assertThat(sdk.packageSet).anyMatch { pkg ->
                pkg.name == "maven:io.github.ichnois:ichnois-spring-boot-starter-jakarta" &&
                  pkg.version == BuildConfig.VERSION_NAME
              }
              assertTrue(sdk.integrationSet.contains("SpringBoot3"))
            },
            anyOrNull(),
          )
      }
  }

  @Test
  fun `registers beforeSendCallback on IchnoisCoreOptions`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(CustomBeforeSendCallbackConfiguration::class.java)
      .run {
        assertThat(it.getBean(IchnoisCoreOptions::class.java).beforeSend)
          .isInstanceOf(CustomBeforeSendCallback::class.java)
      }
  }

  @Test
  fun `registers beforeSendTransactionCallback on IchnoisCoreOptions`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(CustomBeforeSendTransactionCallbackConfiguration::class.java)
      .run {
        assertThat(it.getBean(IchnoisCoreOptions::class.java).beforeSendTransaction)
          .isInstanceOf(CustomBeforeSendTransactionCallback::class.java)
      }
  }

  @Test
  fun `registers logs beforeSendCallback on IchnoisCoreOptions`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(CustomBeforeSendLogsCallbackConfiguration::class.java)
      .run {
        assertThat(it.getBean(IchnoisCoreOptions::class.java).logs.beforeSend)
          .isInstanceOf(CustomBeforeSendLogsCallback::class.java)
      }
  }

  @Test
  fun `registers metrics beforeSendCallback on IchnoisCoreOptions`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(CustomBeforeSendMetricCallbackConfiguration::class.java)
      .run {
        assertThat(it.getBean(IchnoisCoreOptions::class.java).metrics.beforeSend)
          .isInstanceOf(CustomBeforeSendMetricCallback::class.java)
      }
  }

  @Test
  fun `registers beforeBreadcrumbCallback on IchnoisCoreOptions`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(CustomBeforeBreadcrumbCallbackConfiguration::class.java)
      .run {
        assertThat(it.getBean(IchnoisCoreOptions::class.java).beforeBreadcrumb)
          .isInstanceOf(CustomBeforeBreadcrumbCallback::class.java)
      }
  }

  @Test
  fun `registers onDiscardCallback on IchnoisCoreOptions`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(CustomOnDiscardCallbackConfiguration::class.java)
      .run {
        assertThat(it.getBean(IchnoisCoreOptions::class.java).onDiscard)
          .isInstanceOf(CustomOnDiscardCallback::class.java)
      }
  }

  @Test
  fun `registers event processor on IchnoisCoreOptions`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(CustomEventProcessorConfiguration::class.java)
      .run {
        assertThat(it.getBean(IchnoisCoreOptions::class.java).eventProcessors).anyMatch { processor ->
          processor.javaClass == CustomEventProcessor::class.java
        }
      }
  }

  @Test
  fun `registers transport gate on IchnoisCoreOptions`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(CustomTransportGateConfiguration::class.java)
      .run {
        assertThat(it.getBean(IchnoisCoreOptions::class.java).transportGate)
          .isInstanceOf(CustomTransportGate::class.java)
      }
  }

  @Test
  fun `registers custom integration on IchnoisCoreOptions`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(CustomIntegration::class.java)
      .run {
        assertThat(it.getBean(IchnoisCoreOptions::class.java).integrations).anyMatch { integration ->
          integration.javaClass == CustomIntegration::class.java
        }
      }
  }

  @Test
  fun `sets release on IchnoisCoreEvents if Git integration is configured`() {
    baseContextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(
        MockTransportConfiguration::class.java,
        MockGitPropertiesConfiguration::class.java,
      )
      .run {
        IchnoisCore.captureMessage("Some message")
        val transport = it.getBean(ITransport::class.java)
        verify(transport)
          .send(
            checkEvent { event -> assertThat(event.release).isEqualTo("git-commit-id") },
            anyOrNull(),
          )
      }
  }

  @Test
  fun `sets custom release on IchnoisCoreEvents if release property is set and Git integration is configured`() {
    baseContextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj", "ichnois.release=my-release")
      .withUserConfiguration(
        MockTransportConfiguration::class.java,
        MockGitPropertiesConfiguration::class.java,
      )
      .run {
        IchnoisCore.captureMessage("Some message")
        val transport = it.getBean(ITransport::class.java)

        verify(transport)
          .send(
            checkEvent { event -> assertThat(event.release).isEqualTo("my-release") },
            anyOrNull(),
          )
      }
  }

  @Test
  fun `sets inAppIncludes on IchnoisCoreOptions from a class annotated with @SpringBootApplication`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(MainBootClass::class.java)
      .run {
        assertThat(it.getBean(IchnoisCoreProperties::class.java).inAppIncludes).containsOnly("com.acme")
      }
  }

  @Test
  fun `when custom IchnoisCoreUserProvider bean is configured, it's added after HttpServletRequestIchnoisCoreUserProvider`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj", "ichnois.send-default-pii=true")
      .withConfiguration(UserConfigurations.of(IchnoisCoreUserProviderConfiguration::class.java))
      .run {
        val userProviders = it.getIchnoisCoreUserProviders()
        assertEquals(3, userProviders.size)
        assertTrue(userProviders[0] is HttpServletRequestIchnoisCoreUserProvider)
        assertTrue(userProviders[1] is SpringSecurityIchnoisCoreUserProvider)
        assertTrue(userProviders[2] is CustomIchnoisCoreUserProvider)
      }
  }

  @Test
  fun `when custom IchnoisCoreUserProvider bean with higher order is configured, it's added before HttpServletRequestIchnoisCoreUserProvider`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj", "ichnois.send-default-pii=true")
      .withConfiguration(
        UserConfigurations.of(IchnoisCoreHighestOrderUserProviderConfiguration::class.java)
      )
      .run {
        val userProviders = it.getIchnoisCoreUserProviders()
        assertEquals(3, userProviders.size)
        assertTrue(userProviders[0] is CustomIchnoisCoreUserProvider)
        assertTrue(userProviders[1] is HttpServletRequestIchnoisCoreUserProvider)
        assertTrue(userProviders[2] is SpringSecurityIchnoisCoreUserProvider)
      }
  }

  @Test
  fun `when Spring Security is not on the classpath, SpringSecurityIchnoisCoreUserProvider is not configured`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj", "ichnois.send-default-pii=true")
      .withClassLoader(FilteredClassLoader(SecurityContextHolder::class.java))
      .run { ctx ->
        val userProviders = ctx.getIchnoisCoreUserProviders()
        assertTrue(userProviders.isNotEmpty())
        userProviders.forEach { assertFalse(it is SpringSecurityIchnoisCoreUserProvider) }
      }
  }

  @Test
  fun `when Spring MVC is not on the classpath, IchnoisCoreExceptionResolver is not configured`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj", "ichnois.send-default-pii=true")
      .withClassLoader(FilteredClassLoader(HandlerExceptionResolver::class.java))
      .run { assertThat(it).doesNotHaveBean(IchnoisCoreExceptionResolver::class.java) }
  }

  @Test
  fun `when Spring MVC is not on the classpath, fallback TransactionNameProvider is configured`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj", "ichnois.send-default-pii=true")
      .withClassLoader(FilteredClassLoader(HandlerExceptionResolver::class.java))
      .run {
        assertThat(it.getBean(TransactionNameProvider::class.java))
          .isInstanceOf(SpringServletTransactionNameProvider::class.java)
      }
  }

  @Test
  fun `when tracing is enabled, creates tracing filter`() {
    tracingEnabledRunner.run { assertThat(it).hasBean("ichnoisTracingFilter") }
  }

  @Test
  fun `when traces sample rate is set, creates tracing filter`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj", "ichnois.traces-sample-rate=0.2")
      .run { assertThat(it).hasBean("ichnoisTracingFilter") }
  }

  @Test
  fun `when traces sample rate is set to 0, creates tracing filter`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj", "ichnois.traces-sample-rate=0.0")
      .run { assertThat(it).hasBean("ichnoisTracingFilter") }
  }

  @Test
  fun `when custom traces sampler callback is registered, creates tracing filter`() {
    contextRunner
      .withUserConfiguration(CustomTracesSamplerCallbackConfiguration::class.java)
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .run { assertThat(it).hasBean("ichnoisTracingFilter") }
  }

  @Test
  fun `creates tracing filter`() {
    contextRunner.withPropertyValues("ichnois.dsn=http://key@localhost/proj").run {
      assertThat(it).hasBean("ichnoisTracingFilter")
    }
  }

  @Test
  fun `when tracing is enabled and ichnoisTracingFilter already exists, does not create tracing filter`() {
    tracingEnabledRunner.withUserConfiguration(CustomIchnoisCoreTracingFilter::class.java).run {
      assertThat(it).hasBean("ichnoisTracingFilter")
      val filter = it.getBean("ichnoisTracingFilter")

      if (filter is FilterRegistrationBean<*>) {
        assertThat(filter.filter).isNotInstanceOf(IchnoisCoreTracingFilter::class.java)
      } else {
        assertThat(filter).isNotInstanceOf(IchnoisCoreTracingFilter::class.java)
      }
    }
  }

  @Test
  fun `creates AOP beans to support @IchnoisCoreCaptureExceptionParameter`() {
    contextRunner.withPropertyValues("ichnois.dsn=http://key@localhost/proj").run {
      assertThat(it).hasIchnoisCoreExceptionParameterAdviceBeans()
    }
  }

  @Test
  fun `does not create AOP beans to support @IchnoisCoreCaptureExceptionParameter if AOP class is missing`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withClassLoader(FilteredClassLoader(ProceedingJoinPoint::class.java))
      .run { assertThat(it).doesNotHaveIchnoisCoreExceptionParameterAdviceBeans() }
  }

  @Test
  fun `when tracing is enabled creates AOP beans to support @IchnoisCoreTransaction`() {
    tracingEnabledRunner.run { assertThat(it).hasIchnoisCoreTransactionBeans() }
  }

  @Test
  fun `when traces sample rate is set to 0, creates AOP beans to support @IchnoisCoreTransaction`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj", "ichnois.traces-sample-rate=0.0")
      .run { assertThat(it).hasIchnoisCoreTransactionBeans() }
  }

  @Test
  fun `when custom traces sampler callback is registered, creates AOP beans to support @IchnoisCoreTransaction`() {
    contextRunner
      .withUserConfiguration(CustomTracesSamplerCallbackConfiguration::class.java)
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .run { assertThat(it).hasIchnoisCoreTransactionBeans() }
  }

  @Test
  fun `when tracing is disabled, does not create AOP beans to support @IchnoisCoreTransaction`() {
    contextRunner.withPropertyValues("ichnois.dsn=http://key@localhost/proj").run {
      assertThat(it).doesNotHaveIchnoisCoreTransactionBeans()
    }
  }

  @Test
  fun `when Spring AOP is not on the classpath, does not create AOP beans to support @IchnoisCoreTransaction`() {
    tracingEnabledRunner.withClassLoader(FilteredClassLoader(ProceedingJoinPoint::class.java)).run {
      assertThat(it).doesNotHaveIchnoisCoreTransactionBeans()
    }
  }

  @Test
  fun `when tracing is enabled and custom ichnoisTransactionPointcut is provided, ichnoisTransactionPointcut bean is not created`() {
    tracingEnabledRunner
      .withUserConfiguration(CustomIchnoisCorePerformancePointcutConfiguration::class.java)
      .run {
        assertThat(it).hasBean("ichnoisTransactionPointcut")
        val pointcut = it.getBean("ichnoisTransactionPointcut")
        assertThat(pointcut).isInstanceOf(NameMatchMethodPointcut::class.java)
      }
  }

  @Test
  fun `when tracing is enabled creates AOP beans to support @IchnoisCoreSpan`() {
    tracingEnabledRunner.run { assertThat(it).hasIchnoisCoreSpanBeans() }
  }

  @Test
  fun `when traces sample rate is set to 0, creates AOP beans to support @IchnoisCoreSpan`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj", "ichnois.traces-sample-rate=0.0")
      .run { assertThat(it).hasIchnoisCoreSpanBeans() }
  }

  @Test
  fun `when custom traces sampler callback is registered, creates AOP beans to support @IchnoisCoreSpan`() {
    contextRunner
      .withUserConfiguration(CustomTracesSamplerCallbackConfiguration::class.java)
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .run { assertThat(it).hasIchnoisCoreSpanBeans() }
  }

  @Test
  fun `when tracing is disabled, does not create AOP beans to support @Span`() {
    contextRunner.withPropertyValues("ichnois.dsn=http://key@localhost/proj").run {
      assertThat(it).doesNotHaveIchnoisCoreSpanBeans()
    }
  }

  @Test
  fun `when Spring AOP is not on the classpath, does not create AOP beans to support @IchnoisCoreSpan`() {
    tracingEnabledRunner.withClassLoader(FilteredClassLoader(ProceedingJoinPoint::class.java)).run {
      assertThat(it).doesNotHaveIchnoisCoreSpanBeans()
    }
  }

  @Test
  fun `when tracing is enabled and custom ichnoisSpanPointcut is provided, ichnoisSpanPointcut bean is not created`() {
    tracingEnabledRunner
      .withUserConfiguration(CustomIchnoisCorePerformancePointcutConfiguration::class.java)
      .run {
        assertThat(it).hasBean("ichnoisSpanPointcut")
        val pointcut = it.getBean("ichnoisSpanPointcut")
        assertThat(pointcut).isInstanceOf(NameMatchMethodPointcut::class.java)
      }
  }

  @Test
  fun `when tracing is enabled and RestTemplate is on the classpath, IchnoisCoreSpanRestTemplateCustomizer bean is created`() {
    tracingEnabledRunner.run {
      assertThat(it).hasSingleBean(IchnoisCoreSpanRestTemplateCustomizer::class.java)
    }
  }

  @Test
  fun `when tracing is enabled and RestTemplate is not on the classpath, IchnoisCoreSpanRestTemplateCustomizer bean is not created`() {
    tracingEnabledRunner.withClassLoader(FilteredClassLoader(RestTemplate::class.java)).run {
      assertThat(it).doesNotHaveBean(IchnoisCoreSpanRestTemplateCustomizer::class.java)
    }
  }

  @Test
  fun `when tracing is enabled and RestClient is on the classpath, IchnoisCoreSpanRestClientCustomizer bean is created`() {
    tracingEnabledRunner.run {
      assertThat(it).hasSingleBean(IchnoisCoreSpanRestClientCustomizer::class.java)
    }
  }

  @Test
  fun `when tracing is enabled and RestClient is not on the classpath, IchnoisCoreSpanRestClientCustomizer bean is not created`() {
    tracingEnabledRunner.withClassLoader(FilteredClassLoader(RestClient::class.java)).run {
      assertThat(it).doesNotHaveBean(IchnoisCoreSpanRestClientCustomizer::class.java)
    }
  }

  @Test
  fun `when tracing is enabled and WebClient is on the classpath, IchnoisCoreSpanWebClientCustomizer bean is created`() {
    tracingEnabledRunner.run {
      assertThat(it).hasSingleBean(IchnoisCoreSpanWebClientCustomizer::class.java)
    }
  }

  @Test
  fun `when tracing is enabled and WebClient is not on the classpath, IchnoisCoreSpanWebClientCustomizer bean is not created`() {
    tracingEnabledRunner.withClassLoader(FilteredClassLoader(WebClient::class.java)).run {
      assertThat(it).doesNotHaveBean(IchnoisCoreSpanWebClientCustomizer::class.java)
    }
  }

  @Test
  fun `registers tracesSamplerCallback on IchnoisCoreOptions`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(CustomTracesSamplerCallbackConfiguration::class.java)
      .run {
        assertThat(it.getBean(IchnoisCoreOptions::class.java).tracesSampler)
          .isInstanceOf(CustomTracesSamplerCallback::class.java)
      }
  }

  @Test
  fun `when ichnois-apache-http-client-5 is on the classpath, creates apache transport factory`() {
    baseContextRunner.withPropertyValues("ichnois.dsn=http://key@localhost/proj").run {
      assertThat(it.getBean(IchnoisCoreOptions::class.java).transportFactory)
        .isInstanceOf(ApacheHttpClientTransportFactory::class.java)
    }
  }

  @Test
  fun `when ichnois-apache-http-client-5 is not on the classpath, does not create apache transport factory`() {
    baseContextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withClassLoader(FilteredClassLoader(ApacheHttpClientTransportFactory::class.java))
      .run {
        assertThat(it.getBean(IchnoisCoreOptions::class.java).transportFactory)
          .isInstanceOf(AsyncHttpTransportFactory::class.java)
      }
  }

  @Test
  fun `when ichnois-apache-http-client-5 is on the classpath and custom transport factory bean is set, does not create apache transport factory`() {
    baseContextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(MockTransportConfiguration::class.java)
      .run {
        assertThat(it.getBean(IchnoisCoreOptions::class.java).transportFactory)
          .isNotInstanceOf(ApacheHttpClientTransportFactory::class.java)
          .isNotInstanceOf(NoOpTransportFactory::class.java)
      }
  }

  @Test
  fun `when MDC is on the classpath, creates ContextTagsEventProcessor`() {
    contextRunner.withPropertyValues("ichnois.dsn=http://key@localhost/proj").run {
      assertThat(it).hasSingleBean(ContextTagsEventProcessor::class.java)
      val options = it.getBean(IchnoisCoreOptions::class.java)
      assertThat(options.eventProcessors).anyMatch { processor ->
        processor.javaClass == ContextTagsEventProcessor::class.java
      }
    }
  }

  @Test
  fun `when MDC is not on the classpath, does not create ContextTagsEventProcessor`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withClassLoader(FilteredClassLoader(MDC::class.java))
      .run {
        assertThat(it).doesNotHaveBean(ContextTagsEventProcessor::class.java)
        val options = it.getBean(IchnoisCoreOptions::class.java)
        assertThat(options.eventProcessors).noneMatch { processor ->
          processor.javaClass == ContextTagsEventProcessor::class.java
        }
      }
  }

  @Test
  fun `when AgentMarker is on the classpath and auto init off, runs IchnoisCoreOpenTelemetryAgentWithoutAutoInitConfiguration`() {
    IchnoisCoreIntegrationPackageStorage.getInstance().clearStorage()
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj", "ichnois.auto-init=false")
      .run {
        assertTrue(
          IchnoisCoreIntegrationPackageStorage.getInstance()
            .integrations
            .contains("SpringBoot3OpenTelemetryAgentWithoutAutoInit")
        )
      }
  }

  @Test
  fun `when AgentMarker is on the classpath and auto init on, does not run IchnoisCoreOpenTelemetryAgentWithoutAutoInitConfiguration`() {
    IchnoisCoreIntegrationPackageStorage.getInstance().clearStorage()
    contextRunner.withPropertyValues("ichnois.dsn=http://key@localhost/proj").run {
      assertFalse(
        IchnoisCoreIntegrationPackageStorage.getInstance()
          .integrations
          .contains("SpringBoot3OpenTelemetryAgentWithoutAutoInit")
      )
    }
  }

  @Test
  fun `when AgentMarker is not on the classpath and auto init off, does not run IchnoisCoreOpenTelemetryAgentWithoutAutoInitConfiguration`() {
    IchnoisCoreIntegrationPackageStorage.getInstance().clearStorage()
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj", "ichnois.auto-init=false")
      .withClassLoader(FilteredClassLoader(AgentMarker::class.java))
      .run {
        assertFalse(
          IchnoisCoreIntegrationPackageStorage.getInstance()
            .integrations
            .contains("SpringBoot3OpenTelemetryAgentWithoutAutoInit")
        )
      }
  }

  @Test
  fun `when AgentMarker is not on the classpath but OpenTelemetry is, runs SpringBoot3OpenTelemetryNoAgent`() {
    IchnoisCoreIntegrationPackageStorage.getInstance().clearStorage()
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withClassLoader(FilteredClassLoader(AgentMarker::class.java))
      .withUserConfiguration(OtelBeanConfig::class.java)
      .run {
        assertTrue(
          IchnoisCoreIntegrationPackageStorage.getInstance()
            .integrations
            .contains("SpringBoot3OpenTelemetryNoAgent")
        )
      }
  }

  @Test
  fun `when AgentMarker and OpenTelemetry are not on the classpath, does not run SpringBoot3OpenTelemetryNoAgent`() {
    IchnoisCoreIntegrationPackageStorage.getInstance().clearStorage()
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withClassLoader(FilteredClassLoader(AgentMarker::class.java, OpenTelemetry::class.java))
      .run {
        assertFalse(
          IchnoisCoreIntegrationPackageStorage.getInstance()
            .integrations
            .contains("SpringBoot3OpenTelemetryNoAgent")
        )
      }
  }

  @Test
  fun `when AgentMarker and IchnoisCoreAutoConfigurationCustomizerProvider are not on the classpath, does not run SpringBoot3OpenTelemetryNoAgent`() {
    IchnoisCoreIntegrationPackageStorage.getInstance().clearStorage()
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withClassLoader(
        FilteredClassLoader(
          AgentMarker::class.java,
          IchnoisCoreAutoConfigurationCustomizerProvider::class.java,
        )
      )
      .withUserConfiguration(OtelBeanConfig::class.java)
      .run {
        assertFalse(
          IchnoisCoreIntegrationPackageStorage.getInstance()
            .integrations
            .contains("SpringBoot3OpenTelemetryNoAgent")
        )
      }
  }

  @Test
  fun `when AgentMarker is not on the classpath and auto init on, does not run IchnoisCoreOpenTelemetryAgentWithoutAutoInitConfiguration`() {
    IchnoisCoreIntegrationPackageStorage.getInstance().clearStorage()
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withClassLoader(FilteredClassLoader(AgentMarker::class.java))
      .run {
        assertFalse(
          IchnoisCoreIntegrationPackageStorage.getInstance()
            .integrations
            .contains("SpringBoot3OpenTelemetryAgentWithoutAutoInit")
        )
      }
  }

  @Test
  fun `creates quartz config`() {
    contextRunner
      .withPropertyValues(
        "ichnois.dsn=http://key@localhost/proj",
        "ichnois.enable-automatic-checkins=true",
      )
      .run { assertThat(it).hasSingleBean(SchedulerFactoryBeanCustomizer::class.java) }
  }

  @Test
  fun `does not create quartz config if quartz lib missing`() {
    contextRunner
      .withPropertyValues(
        "ichnois.dsn=http://key@localhost/proj",
        "ichnois.enable-automatic-checkins=true",
      )
      .withClassLoader(FilteredClassLoader(QuartzScheduler::class.java))
      .run { assertThat(it).doesNotHaveBean(SchedulerFactoryBeanCustomizer::class.java) }
  }

  @Test
  fun `does not create quartz config if spring-quartz lib missing`() {
    contextRunner
      .withPropertyValues(
        "ichnois.dsn=http://key@localhost/proj",
        "ichnois.enable-automatic-checkins=true",
      )
      .withClassLoader(FilteredClassLoader(SchedulerFactoryBean::class.java))
      .run { assertThat(it).doesNotHaveBean(SchedulerFactoryBeanCustomizer::class.java) }
  }

  @Test
  fun `does not create quartz config if ichnois-quartz lib missing`() {
    contextRunner
      .withPropertyValues(
        "ichnois.dsn=http://key@localhost/proj",
        "ichnois.enable-automatic-checkins=true",
      )
      .withClassLoader(FilteredClassLoader(IchnoisCoreJobListener::class.java))
      .run { assertThat(it).doesNotHaveBean(SchedulerFactoryBeanCustomizer::class.java) }
  }

  @Test
  fun `does not create any graphql config if no ichnois-graphql lib on classpath`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withClassLoader(
        FilteredClassLoader(
          io.ichnois.graphql.IchnoisCoreInstrumentation::class.java,
          io.ichnois.graphql22.IchnoisCoreInstrumentation::class.java,
        )
      )
      .run {
        assertThat(it).doesNotHaveBean(io.ichnois.graphql.IchnoisCoreInstrumentation::class.java)
        assertThat(it).doesNotHaveBean(io.ichnois.graphql22.IchnoisCoreInstrumentation::class.java)
      }
  }

  @Test
  fun `ichnois-graphql22 configuration takes precedence over ichnois-graphql if both on classpath`() {
    contextRunner.withPropertyValues("ichnois.dsn=http://key@localhost/proj").run {
      assertThat(it).hasSingleBean(io.ichnois.graphql22.IchnoisCoreInstrumentation::class.java)
      assertThat(it).doesNotHaveBean(io.ichnois.graphql.IchnoisCoreInstrumentation::class.java)
    }
  }

  @Test
  fun `ichnois graphql configuration is created if graphql22 not on classpath`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withClassLoader(FilteredClassLoader(io.ichnois.graphql22.IchnoisCoreInstrumentation::class.java))
      .run {
        assertThat(it).hasSingleBean(io.ichnois.graphql.IchnoisCoreInstrumentation::class.java)
        assertThat(it).doesNotHaveBean(io.ichnois.graphql22.IchnoisCoreInstrumentation::class.java)
      }
  }

  @Test
  fun `ichnois graphql22 configuration is created if graphql not on classpath`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withClassLoader(FilteredClassLoader(io.ichnois.graphql.IchnoisCoreInstrumentation::class.java))
      .run {
        assertThat(it).doesNotHaveBean(io.ichnois.graphql.IchnoisCoreInstrumentation::class.java)
        assertThat(it).hasSingleBean(io.ichnois.graphql22.IchnoisCoreInstrumentation::class.java)
      }
  }

  @Test
  fun `IchnoisCore quartz job listener is added`() {
    contextRunner
      .withPropertyValues(
        "ichnois.dsn=http://key@localhost/proj",
        "ichnois.enable-automatic-checkins=true",
      )
      .withUserConfiguration(QuartzAutoConfiguration::class.java)
      .run {
        val jobListeners = it.getBean(Scheduler::class.java).listenerManager.jobListeners
        assertThat(jobListeners).hasSize(1)
        assertThat(jobListeners[0])
          .matches({ it.name == "ichnois-job-listener" }, "is ichnois job listener")
      }
  }

  @Test
  fun `user defined SchedulerFactoryBeanCustomizer overrides IchnoisCore Customizer`() {
    contextRunner
      .withPropertyValues(
        "ichnois.dsn=http://key@localhost/proj",
        "ichnois.enable-automatic-checkins=true",
      )
      .withUserConfiguration(
        QuartzAutoConfiguration::class.java,
        CustomSchedulerFactoryBeanCustomizerConfiguration::class.java,
      )
      .run {
        val jobListeners = it.getBean(Scheduler::class.java).listenerManager.jobListeners
        assertThat(jobListeners).hasSize(1)
        assertThat(jobListeners[0])
          .matches({ it.name == "custom-job-listener" }, "is custom job listener")
      }
  }

  @Test
  fun `registers SpringProfilesEventProcessor on IchnoisCoreOptions`() {
    contextRunner.withPropertyValues("ichnois.dsn=http://key@localhost/proj").run {
      assertThat(it.getBean(IchnoisCoreOptions::class.java).eventProcessors).anyMatch { processor ->
        processor.javaClass == SpringProfilesEventProcessor::class.java
      }
    }
  }

  @Test
  fun `when AgentMarker is on the classpath and ContinuousProfiling is enabled IContinuousProfiler and IProfileConverter beans are created and set on options`() {
    IchnoisCoreIntegrationPackageStorage.getInstance().clearStorage()
    contextRunner
      .withPropertyValues(
        "ichnois.dsn=http://key@localhost/proj",
        "ichnois.profile-session-sample-rate=1.0",
      )
      .run {
        assertThat(it).hasSingleBean(IContinuousProfiler::class.java)
        assertThat(it).hasSingleBean(IProfileConverter::class.java)
        assertThat(it)
          .getBean(IProfileConverter::class.java)
          .isInstanceOf(
            AsyncProfilerProfileConverterProvider.AsyncProfilerProfileConverter::class.java
          )
        assertThat(it)
          .getBean(IContinuousProfiler::class.java)
          .isInstanceOf(JavaContinuousProfiler::class.java)
        assertThat(it)
          .getBean(IProfileConverter::class.java)
          .isSameAs(IchnoisCore.getGlobalScope().options.profilerConverter)
        assertThat(it)
          .getBean(IContinuousProfiler::class.java)
          .isSameAs(IchnoisCore.getGlobalScope().options.continuousProfiler)
      }
  }

  @Test
  fun `when AgentMarker is on the classpath and ContinuousProfiling is enabled IContinuousProfiler and IProfileConverter exist beans are taken from options`() {
    IchnoisCoreIntegrationPackageStorage.getInstance().clearStorage()

    contextRunner
      .withPropertyValues(
        "ichnois.dsn=http://key@localhost/proj",
        "ichnois.profile-session-sample-rate=1.0",
        "ichnois.auto-init=false",
        "debug=true",
      )
      .withUserConfiguration(CustomProfilerOptionsConfigurationConfiguration::class.java)
      .run {
        val profiler = it.getBean(IContinuousProfiler::class.java)
        assertTrue(isMock(profiler))
        assertThat(it).hasSingleBean(IContinuousProfiler::class.java)
        assertThat(it).hasSingleBean(IProfileConverter::class.java)
        assertThat(it)
          .getBean(IProfileConverter::class.java)
          .isSameAs(IchnoisCore.getGlobalScope().options.profilerConverter)
        assertThat(it)
          .getBean(IContinuousProfiler::class.java)
          .isSameAs(IchnoisCore.getGlobalScope().options.continuousProfiler)
      }
  }

  @Test
  fun `when AgentMarker is on the classpath and ContinuousProfiling is disabled NoOp Beans are created`() {
    IchnoisCoreIntegrationPackageStorage.getInstance().clearStorage()

    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj", "ichnois.auto-init=false")
      .run {
        assertThat(it).hasSingleBean(IContinuousProfiler::class.java)
        assertThat(it).hasSingleBean(IProfileConverter::class.java)
        assertThat(it)
          .getBean(IProfileConverter::class.java)
          .isInstanceOf(NoOpProfileConverter::class.java)
        assertThat(it)
          .getBean(IContinuousProfiler::class.java)
          .isInstanceOf(NoOpContinuousProfiler::class.java)
      }
  }

  @Test
  fun `when AgentMarker is not on the classpath and ContinuousProfiling is enabled IContinuousProfiler and IProfileConverter beans are not created`() {
    IchnoisCoreIntegrationPackageStorage.getInstance().clearStorage()
    contextRunner
      .withPropertyValues(
        "ichnois.dsn=http://key@localhost/proj",
        "ichnois.profile-session-sample-rate=1.0",
        "debug=true",
      )
      .withClassLoader(FilteredClassLoader(AgentMarker::class.java, OpenTelemetry::class.java))
      .run {
        assertThat(it).doesNotHaveBean(IContinuousProfiler::class.java)
        assertThat(it).doesNotHaveBean(IProfileConverter::class.java)
      }
  }

  @Test
  fun `when JavaContinuousProfiler is not on the classpath and ContinuousProfiling is enabled IProfileConverter beans are not created`() {
    IchnoisCoreIntegrationPackageStorage.getInstance().clearStorage()
    contextRunner
      .withPropertyValues(
        "ichnois.dsn=http://key@localhost/proj",
        "ichnois.profile-session-sample-rate=1.0",
        "debug=true",
      )
      .withClassLoader(FilteredClassLoader(JavaContinuousProfiler::class.java))
      .run {
        assertThat(it).doesNotHaveBean(IContinuousProfiler::class.java)
        assertThat(it).doesNotHaveBean(IProfileConverter::class.java)
      }
  }

  @Configuration(proxyBeanMethods = false)
  open class CustomSchedulerFactoryBeanCustomizerConfiguration {
    class MyJobListener : JobListener {
      override fun getName() = "custom-job-listener"

      override fun jobToBeExecuted(context: JobExecutionContext?) {
        // do nothing
      }

      override fun jobExecutionVetoed(context: JobExecutionContext?) {
        // do nothing
      }

      override fun jobWasExecuted(
        context: JobExecutionContext?,
        jobException: JobExecutionException?,
      ) {
        // do nothing
      }
    }

    @Bean
    open fun mySchedulerFactoryBeanCustomizer(): SchedulerFactoryBeanCustomizer {
      return SchedulerFactoryBeanCustomizer { schedulerFactoryBean ->
        schedulerFactoryBean.setGlobalJobListeners(MyJobListener())
      }
    }
  }

  @Configuration(proxyBeanMethods = false)
  open class CustomOptionsConfigurationConfiguration {

    @Bean
    open fun customOptionsConfiguration() =
      IchnoisCore.OptionsConfiguration<IchnoisCoreOptions> { it.setBeforeSend(null) }

    @Bean open fun beforeSendCallback() = CustomBeforeSendCallback()
  }

  @Configuration(proxyBeanMethods = false)
  open class OverridingOptionsConfigurationConfiguration {

    @Bean open fun ichnoisOptionsConfiguration() = IchnoisCore.OptionsConfiguration<IchnoisCoreOptions> {}
  }

  @Configuration(proxyBeanMethods = false)
  open class CustomProfilerOptionsConfigurationConfiguration {
    private val profiler = mock<IContinuousProfiler>()

    @Bean
    open fun customOptionsConfiguration() =
      IchnoisCore.OptionsConfiguration<IchnoisCoreOptions> { it.setContinuousProfiler(profiler) }

    @Bean open fun beforeSendCallback() = CustomBeforeSendCallback()
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

  @Configuration(proxyBeanMethods = false)
  open class NoOpTransportConfiguration {

    @Bean
    open fun noOpTransportFactory(): ITransportFactory {
      return NoOpTransportFactory.getInstance()
    }
  }

  @Configuration(proxyBeanMethods = false)
  open class CustomBeforeSendCallbackConfiguration {

    @Bean open fun beforeSendCallback() = CustomBeforeSendCallback()
  }

  class CustomBeforeSendCallback : IchnoisCoreOptions.BeforeSendCallback {
    override fun execute(event: IchnoisCoreEvent, hint: Hint): IchnoisCoreEvent? = null
  }

  @Configuration(proxyBeanMethods = false)
  open class CustomBeforeSendLogsCallbackConfiguration {

    @Bean open fun beforeSendCallback() = CustomBeforeSendLogsCallback()
  }

  class CustomBeforeSendLogsCallback : IchnoisCoreOptions.Logs.BeforeSendLogCallback {
    override fun execute(event: IchnoisCoreLogEvent): IchnoisCoreLogEvent? = null
  }

  @Configuration(proxyBeanMethods = false)
  open class CustomBeforeSendMetricCallbackConfiguration {

    @Bean open fun beforeSendCallback() = CustomBeforeSendMetricCallback()
  }

  class CustomBeforeSendMetricCallback : IchnoisCoreOptions.Metrics.BeforeSendMetricCallback {
    override fun execute(metric: IchnoisCoreMetricsEvent, hint: Hint): IchnoisCoreMetricsEvent? = null
  }

  @Configuration(proxyBeanMethods = false)
  open class CustomBeforeSendTransactionCallbackConfiguration {

    @Bean open fun beforeSendTransactionCallback() = CustomBeforeSendTransactionCallback()
  }

  class CustomBeforeSendTransactionCallback : IchnoisCoreOptions.BeforeSendTransactionCallback {
    override fun execute(event: IchnoisCoreTransaction, hint: Hint): IchnoisCoreTransaction? = null
  }

  @Configuration(proxyBeanMethods = false)
  open class CustomBeforeBreadcrumbCallbackConfiguration {

    @Bean open fun beforeBreadcrumbCallback() = CustomBeforeBreadcrumbCallback()
  }

  class CustomBeforeBreadcrumbCallback : IchnoisCoreOptions.BeforeBreadcrumbCallback {
    override fun execute(breadcrumb: Breadcrumb, hint: Hint): Breadcrumb? = null
  }

  @Configuration(proxyBeanMethods = false)
  open class CustomOnDiscardCallbackConfiguration {

    @Bean open fun onDiscardCallback() = CustomOnDiscardCallback()
  }

  class CustomOnDiscardCallback : IchnoisCoreOptions.OnDiscardCallback {
    override fun execute(reason: DiscardReason, category: DataCategory, countToAdd: Long) {}
  }

  @Configuration(proxyBeanMethods = false)
  open class CustomEventProcessorConfiguration {

    @Bean open fun customEventProcessor() = CustomEventProcessor()
  }

  class CustomEventProcessor : EventProcessor {
    override fun process(event: IchnoisCoreEvent, hint: Hint) = null
  }

  @Configuration(proxyBeanMethods = false)
  open class CustomIntegrationConfiguration {

    @Bean open fun customIntegration() = CustomIntegration()
  }

  class CustomIntegration : Integration {
    override fun register(scopes: IScopes, options: IchnoisCoreOptions) {}
  }

  @Configuration(proxyBeanMethods = false)
  open class CustomTransportGateConfiguration {

    @Bean open fun customTransportGate() = CustomTransportGate()
  }

  class CustomTransportGate : ITransportGate {
    override fun isConnected() = true
  }

  @Configuration(proxyBeanMethods = false)
  open class MockGitPropertiesConfiguration {

    @Bean
    open fun gitProperties(): GitProperties {
      val git = mock<GitProperties>()
      whenever(git.commitId).thenReturn("git-commit-id")
      return git
    }
  }

  @Configuration
  open class IchnoisCoreUserProviderConfiguration {

    @Bean open fun userProvider() = CustomIchnoisCoreUserProvider()
  }

  @Configuration
  open class IchnoisCoreHighestOrderUserProviderConfiguration {

    @Bean @Order(Ordered.HIGHEST_PRECEDENCE) open fun userProvider() = CustomIchnoisCoreUserProvider()
  }

  @Configuration
  open class CustomIchnoisCoreTracingFilter {

    @Bean open fun ichnoisTracingFilter() = mock<Filter>()
  }

  @Configuration
  open class CustomIchnoisCorePerformancePointcutConfiguration {

    @Bean open fun ichnoisTransactionPointcut() = NameMatchMethodPointcut()

    @Bean open fun ichnoisSpanPointcut() = NameMatchMethodPointcut()
  }

  @Configuration
  open class CustomTracesSamplerCallbackConfiguration {

    @Bean open fun tracingSamplerCallback() = CustomTracesSamplerCallback()
  }

  /** this should be taken care of by the otel spring starter in a real application */
  @Configuration
  open class OtelBeanConfig {

    @Bean open fun openTelemetry() = OpenTelemetry.noop()
  }

  class CustomTracesSamplerCallback : IchnoisCoreOptions.TracesSamplerCallback {
    override fun sample(samplingContext: SamplingContext) = 1.0
  }

  open class CustomIchnoisCoreUserProvider : IchnoisCoreUserProvider {
    override fun provideUser(): User? {
      val user = User()
      user.username = "john.smith"
      return user
    }
  }

  private fun <C : ApplicationContext> ApplicationContextAssert<C>.hasIchnoisCoreTransactionBeans():
    ApplicationContextAssert<C> {
    this.hasBean("ichnoisTransactionPointcut")
    this.hasBean("ichnoisTransactionAdvice")
    this.hasBean("ichnoisTransactionAdvisor")
    return this
  }

  private fun <C : ApplicationContext> ApplicationContextAssert<C>
    .doesNotHaveIchnoisCoreTransactionBeans(): ApplicationContextAssert<C> {
    this.doesNotHaveBean("ichnoisTransactionPointcut")
    this.doesNotHaveBean("ichnoisTransactionAdvice")
    this.doesNotHaveBean("ichnoisTransactionAdvisor")
    return this
  }

  private fun <C : ApplicationContext> ApplicationContextAssert<C>.hasIchnoisCoreSpanBeans():
    ApplicationContextAssert<C> {
    this.hasBean("ichnoisSpanPointcut")
    this.hasBean("ichnoisSpanAdvice")
    this.hasBean("ichnoisSpanAdvisor")
    return this
  }

  private fun <C : ApplicationContext> ApplicationContextAssert<C>.doesNotHaveIchnoisCoreSpanBeans():
    ApplicationContextAssert<C> {
    this.doesNotHaveBean("ichnoisSpanPointcut")
    this.doesNotHaveBean("ichnoisSpanAdvice")
    this.doesNotHaveBean("ichnoisSpanAdvisor")
    return this
  }

  private fun <C : ApplicationContext> ApplicationContextAssert<C>
    .hasIchnoisCoreExceptionParameterAdviceBeans(): ApplicationContextAssert<C> {
    this.hasBean("ichnoisCaptureExceptionParameterPointcut")
    this.hasBean("ichnoisCaptureExceptionParameterAdvice")
    this.hasBean("ichnoisCaptureExceptionParameterAdvisor")
    return this
  }

  private fun <C : ApplicationContext> ApplicationContextAssert<C>
    .doesNotHaveIchnoisCoreExceptionParameterAdviceBeans(): ApplicationContextAssert<C> {
    this.doesNotHaveBean("ichnoisCaptureExceptionParameterPointcut")
    this.doesNotHaveBean("ichnoisCaptureExceptionParameterAdvice")
    this.doesNotHaveBean("ichnoisCaptureExceptionParameterAdvisor")
    return this
  }

  private fun ApplicationContext.getIchnoisCoreUserProviders(): List<IchnoisCoreUserProvider> {
    val userFilter =
      this.getBean("ichnoisUserFilter", FilterRegistrationBean::class.java).filter
        as IchnoisCoreUserFilter
    return userFilter.ichnoisUserProviders
  }
}
