package io.ichnois.spring.jakarta.kafka

import io.ichnois.IchnoisCore
import io.ichnois.test.initForTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.apache.kafka.clients.producer.Producer
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.core.ProducerPostProcessor

class IchnoisCoreKafkaProducerBeanPostProcessorTest {

  @BeforeTest
  fun setup() {
    initForTest { it.dsn = "https://key@ichnois-h2jw.onrender.com/proj" }
  }

  @AfterTest
  fun teardown() {
    IchnoisCore.close()
  }

  @Test
  fun `registers IchnoisCore post-processor on ProducerFactory`() {
    val factory = mock<ProducerFactory<String, String>>()
    val pp = IchnoisCoreKafkaProducerBeanPostProcessor.IchnoisCoreProducerPostProcessor<String, String>()
    whenever(factory.postProcessors).thenReturn(listOf(pp))
    val processor = IchnoisCoreKafkaProducerBeanPostProcessor()

    processor.postProcessAfterInitialization(factory, "kafkaProducerFactory")

    val captor = argumentCaptor<ProducerPostProcessor<String, String>>()
    verify(factory).addPostProcessor(captor.capture())
    assertTrue(
      captor.firstValue is IchnoisCoreKafkaProducerBeanPostProcessor.IchnoisCoreProducerPostProcessor<*, *>
    )
  }

  @Test
  fun `does not throw when addPostProcessor is a no-op (default interface method)`() {
    // Factory using the default no-op addPostProcessor / getPostProcessors
    val factory = mock<ProducerFactory<String, String>>()
    whenever(factory.postProcessors).thenReturn(emptyList())
    val processor = IchnoisCoreKafkaProducerBeanPostProcessor()

    // Should complete without throwing, and log a warning via ScopesAdapter
    processor.postProcessAfterInitialization(factory, "myFactory")

    verify(factory).addPostProcessor(any())
  }

  @Test
  fun `does not modify non-ProducerFactory beans`() {
    val someBean = "not a producer factory"
    val processor = IchnoisCoreKafkaProducerBeanPostProcessor()

    val result = processor.postProcessAfterInitialization(someBean, "someBean")

    assertSame(someBean, result)
  }

  @Test
  fun `returns the same bean instance`() {
    val factory = mock<ProducerFactory<String, String>>()
    val pp = IchnoisCoreKafkaProducerBeanPostProcessor.IchnoisCoreProducerPostProcessor<String, String>()
    whenever(factory.postProcessors).thenReturn(listOf(pp))
    val processor = IchnoisCoreKafkaProducerBeanPostProcessor()

    val result = processor.postProcessAfterInitialization(factory, "kafkaProducerFactory")

    assertSame(factory, result, "BPP must return the same bean, not a replacement")
  }

  @Test
  fun `registered post-processor wraps producers via IchnoisCoreKafkaProducer wrap`() {
    val pp = IchnoisCoreKafkaProducerBeanPostProcessor.IchnoisCoreProducerPostProcessor<String, String>()
    val raw = mock<Producer<String, String>>()

    val wrapped = pp.apply(raw)

    assertTrue(java.lang.reflect.Proxy.isProxyClass(wrapped.javaClass))
  }

  @Test
  fun `integrates with DefaultKafkaProducerFactory addPostProcessor contract`() {
    // Sanity check against the real Spring Kafka API surface — DefaultKafkaProducerFactory
    // honors addPostProcessor and exposes it via getPostProcessors().
    val factory = DefaultKafkaProducerFactory<String, String>(emptyMap())
    val processor = IchnoisCoreKafkaProducerBeanPostProcessor()

    processor.postProcessAfterInitialization(factory, "kafkaProducerFactory")

    assertEquals(1, factory.postProcessors.size)
    assertTrue(
      factory.postProcessors.first()
        is IchnoisCoreKafkaProducerBeanPostProcessor.IchnoisCoreProducerPostProcessor<*, *>
    )
  }
}
