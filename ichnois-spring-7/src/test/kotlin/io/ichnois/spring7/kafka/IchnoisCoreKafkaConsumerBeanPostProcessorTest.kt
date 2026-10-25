package io.ichnois.spring7.kafka

import io.ichnois.IchnoisCore
import io.ichnois.test.initForTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.mockito.kotlin.mock
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.listener.RecordInterceptor

class IchnoisCoreKafkaConsumerBeanPostProcessorTest {

  @BeforeTest
  fun setup() {
    initForTest { it.dsn = "https://key@ichnois-h2jw.onrender.com/proj" }
  }

  @AfterTest
  fun teardown() {
    IchnoisCore.close()
  }

  @Test
  fun `wraps ConcurrentKafkaListenerContainerFactory with IchnoisCoreKafkaRecordInterceptor`() {
    val consumerFactory = mock<ConsumerFactory<String, String>>()
    val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
    factory.setConsumerFactory(consumerFactory)

    val processor = IchnoisCoreKafkaConsumerBeanPostProcessor()
    processor.postProcessAfterInitialization(factory, "kafkaListenerContainerFactory")

    // Verify via reflection that the interceptor was set
    val field = factory.javaClass.superclass.getDeclaredField("recordInterceptor")
    field.isAccessible = true
    val interceptor = field.get(factory)
    assertTrue(interceptor is IchnoisCoreKafkaRecordInterceptor<*, *>)
  }

  @Test
  fun `does not double-wrap when IchnoisCoreKafkaRecordInterceptor already set`() {
    val consumerFactory = mock<ConsumerFactory<String, String>>()
    val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
    factory.setConsumerFactory(consumerFactory)

    val processor = IchnoisCoreKafkaConsumerBeanPostProcessor()
    // First wrap
    processor.postProcessAfterInitialization(factory, "kafkaListenerContainerFactory")

    val field = factory.javaClass.superclass.getDeclaredField("recordInterceptor")
    field.isAccessible = true
    val firstInterceptor = field.get(factory)

    // Second wrap — should be idempotent
    processor.postProcessAfterInitialization(factory, "kafkaListenerContainerFactory")
    val secondInterceptor = field.get(factory)

    assertSame(firstInterceptor, secondInterceptor)
  }

  @Test
  fun `does not wrap non-factory beans`() {
    val someBean = "not a factory"
    val processor = IchnoisCoreKafkaConsumerBeanPostProcessor()

    val result = processor.postProcessAfterInitialization(someBean, "someBean")

    assertSame(someBean, result)
  }

  @Test
  fun `chains existing customer RecordInterceptor as delegate`() {
    val consumerFactory = mock<ConsumerFactory<String, String>>()
    val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
    factory.setConsumerFactory(consumerFactory)

    val customerInterceptor = RecordInterceptor<String, String> { record, _ -> record }
    factory.setRecordInterceptor(customerInterceptor)

    val processor = IchnoisCoreKafkaConsumerBeanPostProcessor()
    processor.postProcessAfterInitialization(factory, "kafkaListenerContainerFactory")

    val field = factory.javaClass.superclass.getDeclaredField("recordInterceptor")
    field.isAccessible = true
    val installed = field.get(factory)
    assertTrue(
      installed is IchnoisCoreKafkaRecordInterceptor<*, *>,
      "expected IchnoisCoreKafkaRecordInterceptor, got ${installed?.javaClass}",
    )

    val delegateField = IchnoisCoreKafkaRecordInterceptor::class.java.getDeclaredField("delegate")
    delegateField.isAccessible = true
    assertSame(
      customerInterceptor,
      delegateField.get(installed),
      "customer interceptor must be preserved as delegate",
    )
  }

  @Test
  fun `skips installation when reflection fails and preserves customer interceptor`() {
    val consumerFactory = mock<ConsumerFactory<String, String>>()
    val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
    factory.setConsumerFactory(consumerFactory)
    val customerInterceptor = RecordInterceptor<String, String> { record, _ -> record }
    factory.setRecordInterceptor(customerInterceptor)

    val field = factory.javaClass.superclass.getDeclaredField("recordInterceptor")
    field.isAccessible = true
    assertSame(customerInterceptor, field.get(factory))

    val processor = IchnoisCoreKafkaConsumerBeanPostProcessor("missingRecordInterceptor")
    processor.postProcessAfterInitialization(factory, "kafkaListenerContainerFactory")

    assertSame(
      customerInterceptor,
      field.get(factory),
      "customer interceptor must remain installed when IchnoisCore cannot read it",
    )
  }
}
