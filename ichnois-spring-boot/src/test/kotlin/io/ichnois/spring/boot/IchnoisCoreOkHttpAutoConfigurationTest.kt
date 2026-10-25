package io.ichnois.spring.boot

import com.google.common.truth.Truth.assertThat
import io.ichnois.ITransportFactory
import io.ichnois.NoOpTransportFactory
import io.ichnois.okhttp.IchnoisCoreOkHttpEventListener
import io.ichnois.okhttp.IchnoisCoreOkHttpInterceptor
import io.ichnois.opentelemetry.IchnoisCoreAutoConfigurationCustomizerProvider
import io.ichnois.opentelemetry.agent.AgentMarker
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class IchnoisCoreOkHttpAutoConfigurationTest {

  private val baseContextRunner =
    ApplicationContextRunner()
      .withUserConfiguration(TestApplication::class.java, NoOpTransportConfiguration::class.java)
      .withPropertyValues(
        "ichnois.shutdownTimeoutMillis=0",
        "ichnois.sessionFlushTimeoutMillis=0",
        "ichnois.flushTimeoutMillis=0",
        "ichnois.send-modules=false",
        "ichnois.enable-backpressure-handling=false",
        "ichnois.enable-spotlight=false",
      )

  private val contextRunner =
    baseContextRunner.withPropertyValues("ichnois.clients.ok-http-enabled=true")

  private val noOtelClassLoader =
    FilteredClassLoader(
      IchnoisCoreAutoConfigurationCustomizerProvider::class.java,
      AgentMarker::class.java,
    )

  private val noOtelContextRunner = contextRunner.withClassLoader(noOtelClassLoader)

  @Test
  fun `does not modify clients unless explicitly enabled`() {
    for (properties in
      listOf(emptyArray<String>(), arrayOf("ichnois.clients.ok-http-enabled=false"))) {
      val plainClient = OkHttpClient()
      val manualClient =
        OkHttpClient.Builder()
          .addInterceptor(IchnoisCoreOkHttpInterceptor())
          .eventListener(IchnoisCoreOkHttpEventListener())
          .build()

      baseContextRunner
        .withClassLoader(noOtelClassLoader)
        .withPropertyValues("ichnois.dsn=http://key@localhost/proj", *properties)
        .withBean("plainClient", OkHttpClient::class.java, { plainClient })
        .withBean("manualClient", OkHttpClient::class.java, { manualClient })
        .run { context ->
          assertThat(context.getBean("plainClient")).isSameInstanceAs(plainClient)
          assertThat(context.getBean("manualClient")).isSameInstanceAs(manualClient)
          assertThat(context.getBeansOfType(IchnoisCoreOkHttpClientBeanPostProcessor::class.java))
            .isEmpty()
          assertThat(context.getBean(IchnoisCoreProperties::class.java).clients.isOkHttpEnabled)
            .isFalse()
        }
    }
  }

  @Test
  fun `instruments a Spring managed OkHttpClient`() {
    noOtelContextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(OkHttpClientConfiguration::class.java)
      .run { context ->
        val client = context.getBean(OkHttpClient::class.java)
        val existingInterceptor = context.getBean("existingInterceptor", Interceptor::class.java)

        assertThat(context.getBean(IchnoisCoreProperties::class.java).clients.isOkHttpEnabled).isTrue()
        assertThat(client.connectTimeoutMillis).isEqualTo(1234)
        assertThat(client.interceptors).contains(existingInterceptor)
        assertThat(client.interceptors.filterIsInstance<IchnoisCoreOkHttpInterceptor>()).hasSize(1)
        assertThat(client.interceptors.last()).isInstanceOf(IchnoisCoreOkHttpInterceptor::class.java)

        val call = mock<Call>()
        whenever(call.request()).thenReturn(Request.Builder().url("https://example.com").build())
        val listener = client.eventListenerFactory.create(call)
        assertThat(listener).isInstanceOf(IchnoisCoreOkHttpEventListener::class.java)

        listener.callStart(call)
        assertThat(context.getBean(RecordingEventListener::class.java).callStarted.get()).isTrue()
        listener.callEnd(call)
      }
  }

  @Test
  fun `instruments every Spring managed OkHttpClient`() {
    noOtelContextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(MultipleOkHttpClientsConfiguration::class.java)
      .run { context ->
        val clients = context.getBeansOfType(OkHttpClient::class.java)

        assertThat(clients).hasSize(2)
        clients.values.forEach { client ->
          assertThat(client.interceptors.filterIsInstance<IchnoisCoreOkHttpInterceptor>()).hasSize(1)
          assertThat(client.interceptors.last()).isInstanceOf(IchnoisCoreOkHttpInterceptor::class.java)
        }
      }
  }

  @Test
  fun `forwards canceled before callStart to the original event listener`() {
    noOtelContextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(OkHttpClientConfiguration::class.java)
      .run { context ->
        val client = context.getBean(OkHttpClient::class.java)

        // OkHttp creates the listener in the Call constructor and reports canceled() even if
        // the call is never executed, i.e. before callStart() ever happens
        client.newCall(Request.Builder().url("https://example.com").build()).cancel()

        assertThat(context.getBean(RecordingEventListener::class.java).callCanceled.get()).isTrue()
      }
  }

  @Test
  fun `does not wrap a IchnoisCore listener created by the original factory`() {
    val ichnoisListener = IchnoisCoreOkHttpEventListener()
    val client = OkHttpClient.Builder().eventListenerFactory { ichnoisListener }.build()

    noOtelContextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withBean("okHttpClient", OkHttpClient::class.java, { client })
      .run { context ->
        val instrumented = context.getBean(OkHttpClient::class.java)

        assertThat(instrumented.eventListenerFactory.create(mock()))
          .isSameInstanceAs(ichnoisListener)
        assertThat(instrumented.interceptors.filterIsInstance<IchnoisCoreOkHttpInterceptor>()).hasSize(1)
      }
  }

  @Test
  fun `does not duplicate an existing IchnoisCore interceptor`() {
    noOtelContextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(ManuallyInstrumentedOkHttpClientConfiguration::class.java)
      .run { context ->
        val client = context.getBean(OkHttpClient::class.java)

        assertThat(client.interceptors.filterIsInstance<IchnoisCoreOkHttpInterceptor>()).hasSize(1)
        assertThat(client.eventListenerFactory.create(mock()))
          .isInstanceOf(IchnoisCoreOkHttpEventListener::class.java)
      }
  }

  @Test
  fun `post processor is idempotent`() {
    val processor = IchnoisCoreOkHttpClientBeanPostProcessor()
    val firstResult =
      processor.postProcessAfterInitialization(OkHttpClient(), "okHttpClient") as OkHttpClient
    val secondResult =
      processor.postProcessAfterInitialization(firstResult, "okHttpClient") as OkHttpClient

    assertThat(secondResult).isSameInstanceAs(firstResult)
    assertThat(secondResult.interceptors.filterIsInstance<IchnoisCoreOkHttpInterceptor>()).hasSize(1)
  }

  @Test
  fun `does not replace unrelated beans`() {
    val processor = IchnoisCoreOkHttpClientBeanPostProcessor()
    val bean = Any()

    assertThat(processor.postProcessAfterInitialization(bean, "bean")).isSameInstanceAs(bean)
  }

  @Test
  fun `does not replace OkHttpClient subclasses`() {
    val processor = IchnoisCoreOkHttpClientBeanPostProcessor()
    val client = CustomOkHttpClient()

    assertThat(processor.postProcessAfterInitialization(client, "okHttpClient"))
      .isSameInstanceAs(client)
    assertThat(client.interceptors).isEmpty()
  }

  @Test
  fun `does not instrument when OpenTelemetry agent is present`() {
    contextRunner
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(OkHttpClientConfiguration::class.java)
      .run { context ->
        val client = context.getBean(OkHttpClient::class.java)

        assertThat(client.interceptors.filterIsInstance<IchnoisCoreOkHttpInterceptor>()).isEmpty()
        assertThat(client.eventListenerFactory.create(mock()))
          .isNotInstanceOf(IchnoisCoreOkHttpEventListener::class.java)
      }
  }

  @Test
  fun `instruments when IchnoisCore OpenTelemetry integration is used without the agent`() {
    contextRunner
      .withClassLoader(FilteredClassLoader(AgentMarker::class.java))
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(OkHttpClientConfiguration::class.java)
      .run { context ->
        val client = context.getBean(OkHttpClient::class.java)

        assertThat(client.interceptors.filterIsInstance<IchnoisCoreOkHttpInterceptor>()).hasSize(1)
        assertThat(client.eventListenerFactory.create(mock()))
          .isInstanceOf(IchnoisCoreOkHttpEventListener::class.java)
      }
  }

  @Test
  fun `does not instrument OkHttpClient without a dsn`() {
    noOtelContextRunner.withUserConfiguration(OkHttpClientConfiguration::class.java).run { context
      ->
      val client = context.getBean(OkHttpClient::class.java)

      assertThat(client.interceptors.filterIsInstance<IchnoisCoreOkHttpInterceptor>()).isEmpty()
      assertThat(client.eventListenerFactory.create(mock()))
        .isNotInstanceOf(IchnoisCoreOkHttpEventListener::class.java)
    }
  }

  @Test
  fun `does not create a default OkHttpClient`() {
    noOtelContextRunner.withPropertyValues("ichnois.dsn=http://key@localhost/proj").run { context ->
      assertThat(context.getBeansOfType(OkHttpClient::class.java)).isEmpty()
    }
  }

  @Test
  fun `does not instrument when ichnois-okhttp is not on the classpath`() {
    contextRunner
      .withClassLoader(
        FilteredClassLoader(
          IchnoisCoreOkHttpInterceptor::class.java,
          IchnoisCoreAutoConfigurationCustomizerProvider::class.java,
          AgentMarker::class.java,
        )
      )
      .withPropertyValues("ichnois.dsn=http://key@localhost/proj")
      .withUserConfiguration(OkHttpClientConfiguration::class.java)
      .run { context ->
        val client = context.getBean(OkHttpClient::class.java)
        assertThat(client.interceptors).hasSize(1)
        assertThat(client.interceptors.first().javaClass.name)
          .isEqualTo(OkHttpClientConfiguration.ExistingInterceptor::class.java.name)
      }
  }

  @Configuration(proxyBeanMethods = false) @EnableAutoConfiguration open class TestApplication

  @Configuration(proxyBeanMethods = false)
  open class NoOpTransportConfiguration {
    @Bean open fun noOpTransportFactory(): ITransportFactory = NoOpTransportFactory.getInstance()
  }

  @Configuration(proxyBeanMethods = false)
  open class OkHttpClientConfiguration {
    @Bean open fun existingInterceptor(): Interceptor = ExistingInterceptor()

    @Bean open fun recordingEventListener(): RecordingEventListener = RecordingEventListener()

    @Bean
    open fun okHttpClient(
      existingInterceptor: Interceptor,
      recordingEventListener: RecordingEventListener,
    ): OkHttpClient =
      OkHttpClient.Builder()
        .connectTimeout(1234, TimeUnit.MILLISECONDS)
        .addInterceptor(existingInterceptor)
        .eventListener(recordingEventListener)
        .build()

    class ExistingInterceptor : Interceptor {
      override fun intercept(chain: Interceptor.Chain) = chain.proceed(chain.request())
    }
  }

  @Configuration(proxyBeanMethods = false)
  open class MultipleOkHttpClientsConfiguration {
    @Bean open fun firstOkHttpClient(): OkHttpClient = OkHttpClient()

    @Bean open fun secondOkHttpClient(): OkHttpClient = OkHttpClient()
  }

  @Configuration(proxyBeanMethods = false)
  open class ManuallyInstrumentedOkHttpClientConfiguration {
    @Bean
    open fun okHttpClient(): OkHttpClient =
      OkHttpClient.Builder().addInterceptor(IchnoisCoreOkHttpInterceptor()).build()
  }

  class CustomOkHttpClient : OkHttpClient()

  class RecordingEventListener : EventListener() {
    val callStarted = AtomicBoolean(false)
    val callCanceled = AtomicBoolean(false)

    override fun callStart(call: Call) {
      callStarted.set(true)
    }

    override fun canceled(call: Call) {
      callCanceled.set(true)
    }
  }
}
