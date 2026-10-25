package io.ichnois.spring.boot4

import io.ichnois.kafka.IchnoisCoreKafkaProducer
import io.ichnois.opentelemetry.IchnoisCoreAutoConfigurationCustomizerProvider
import io.ichnois.opentelemetry.agent.AgentMarker
import io.ichnois.spring7.kafka.IchnoisCoreKafkaConsumerBeanPostProcessor
import io.ichnois.spring7.kafka.IchnoisCoreKafkaProducerBeanPostProcessor
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.kafka.core.KafkaTemplate

class IchnoisCoreKafkaAutoConfigurationTest {

  private val contextRunner =
    ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(IchnoisCoreAutoConfiguration::class.java))
      .withPropertyValues(
        "ichnois.dsn=http://key@localhost/proj",
        "ichnois.traces-sample-rate=1.0",
        "ichnois.shutdownTimeoutMillis=0",
        "ichnois.sessionFlushTimeoutMillis=0",
        "ichnois.flushTimeoutMillis=0",
        "ichnois.readTimeoutMillis=50",
        "ichnois.connectionTimeoutMillis=50",
        "ichnois.send-modules=false",
        "ichnois.debug=false",
      )

  private val noOtelClassLoader =
    FilteredClassLoader(
      IchnoisCoreAutoConfigurationCustomizerProvider::class.java,
      AgentMarker::class.java,
    )

  private val noOtelCustomizerClassLoader =
    FilteredClassLoader(IchnoisCoreAutoConfigurationCustomizerProvider::class.java)

  private val noIchnoisCoreKafkaClassLoader =
    FilteredClassLoader(
      IchnoisCoreKafkaProducer::class.java,
      IchnoisCoreAutoConfigurationCustomizerProvider::class.java,
      AgentMarker::class.java,
    )

  private val noSpringKafkaClassLoader =
    FilteredClassLoader(
      KafkaTemplate::class.java,
      IchnoisCoreAutoConfigurationCustomizerProvider::class.java,
      AgentMarker::class.java,
    )

  @Test
  fun `registers Kafka BPPs when queue tracing is enabled`() {
    contextRunner
      .withClassLoader(noOtelClassLoader)
      .withPropertyValues("ichnois.enable-queue-tracing=true")
      .run { context ->
        assertThat(context).hasSingleBean(IchnoisCoreKafkaProducerBeanPostProcessor::class.java)
        assertThat(context).hasSingleBean(IchnoisCoreKafkaConsumerBeanPostProcessor::class.java)
      }
  }

  @Test
  fun `does not register Kafka BPPs when queue tracing is disabled`() {
    contextRunner.withClassLoader(noOtelClassLoader).run { context ->
      assertThat(context).doesNotHaveBean(IchnoisCoreKafkaProducerBeanPostProcessor::class.java)
      assertThat(context).doesNotHaveBean(IchnoisCoreKafkaConsumerBeanPostProcessor::class.java)
    }
  }

  @Test
  fun `does not register Kafka BPPs when ichnois-kafka is not present`() {
    contextRunner
      .withClassLoader(noIchnoisCoreKafkaClassLoader)
      .withPropertyValues("ichnois.enable-queue-tracing=true")
      .run { context ->
        assertThat(context).doesNotHaveBean(IchnoisCoreKafkaProducerBeanPostProcessor::class.java)
        assertThat(context).doesNotHaveBean(IchnoisCoreKafkaConsumerBeanPostProcessor::class.java)
      }
  }

  @Test
  fun `does not register Kafka BPPs when spring-kafka is not present`() {
    contextRunner
      .withClassLoader(noSpringKafkaClassLoader)
      .withPropertyValues("ichnois.enable-queue-tracing=true")
      .run { context ->
        assertThat(context).doesNotHaveBean(IchnoisCoreKafkaProducerBeanPostProcessor::class.java)
        assertThat(context).doesNotHaveBean(IchnoisCoreKafkaConsumerBeanPostProcessor::class.java)
      }
  }

  @Test
  fun `does not register Kafka BPPs when queue tracing is explicitly false`() {
    contextRunner
      .withClassLoader(noOtelClassLoader)
      .withPropertyValues("ichnois.enable-queue-tracing=false")
      .run { context ->
        assertThat(context).doesNotHaveBean(IchnoisCoreKafkaProducerBeanPostProcessor::class.java)
        assertThat(context).doesNotHaveBean(IchnoisCoreKafkaConsumerBeanPostProcessor::class.java)
      }
  }

  @Test
  fun `does not register Kafka BPPs when OpenTelemetry agent is present`() {
    contextRunner
      .withClassLoader(noOtelCustomizerClassLoader)
      .withPropertyValues("ichnois.enable-queue-tracing=true")
      .run { context ->
        assertThat(context).doesNotHaveBean(IchnoisCoreKafkaProducerBeanPostProcessor::class.java)
        assertThat(context).doesNotHaveBean(IchnoisCoreKafkaConsumerBeanPostProcessor::class.java)
      }
  }

  @Test
  fun `does not register Kafka BPPs when OpenTelemetry integration is present`() {
    contextRunner.withPropertyValues("ichnois.enable-queue-tracing=true").run { context ->
      assertThat(context).doesNotHaveBean(IchnoisCoreKafkaProducerBeanPostProcessor::class.java)
      assertThat(context).doesNotHaveBean(IchnoisCoreKafkaConsumerBeanPostProcessor::class.java)
    }
  }
}
