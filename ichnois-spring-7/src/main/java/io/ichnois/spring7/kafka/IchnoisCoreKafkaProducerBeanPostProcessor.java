package io.ichnois.spring7.kafka;

import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.kafka.IchnoisCoreKafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.core.ProducerPostProcessor;

/**
 * Installs a {@link ProducerPostProcessor} on every {@link ProducerFactory} bean so that each
 * {@link Producer} created by Spring Kafka is wrapped via {@link IchnoisCoreKafkaProducer#wrap
 * IchnoisCoreKafkaProducer.wrap(Producer)}.
 *
 * <p>The wrapper records a {@code queue.publish} span around each {@code send(...)} that finishes
 * when the broker ack callback fires, giving a real producer-send lifecycle span. {@code
 * KafkaTemplate} beans are left untouched, so all customer-configured listeners, interceptors and
 * observation settings are preserved.
 *
 * <p>Note: {@link ProducerFactory#addPostProcessor(ProducerPostProcessor)} is a default method on
 * the interface that is a no-op unless overridden. Custom factories that do not extend {@code
 * DefaultKafkaProducerFactory} will not receive IchnoisCore producer instrumentation; a warning is
 * logged at startup in that case.
 */
@ApiStatus.Internal
public final class IchnoisCoreKafkaProducerBeanPostProcessor
    implements BeanPostProcessor, PriorityOrdered {

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  public @NotNull Object postProcessAfterInitialization(
      final @NotNull Object bean, final @NotNull String beanName) throws BeansException {
    if (bean instanceof ProducerFactory<?, ?> factory) {

      final @NotNull IchnoisCoreProducerPostProcessor pp = new IchnoisCoreProducerPostProcessor<>();
      factory.addPostProcessor(pp);
      if (!factory.getPostProcessors().contains(pp)) {
        ScopesAdapter.getInstance()
            .getOptions()
            .getLogger()
            .log(
                IchnoisCoreLevel.WARNING,
                "IchnoisCore Kafka producer tracing not active for ProducerFactory '%s' (%s). "
                    + "addPostProcessor() was not honored — the factory may not extend "
                    + "DefaultKafkaProducerFactory. Wrap producers manually with "
                    + "IchnoisCoreKafkaProducer.wrap(producer).",
                beanName,
                factory.getClass().getName());
      }
    }
    return bean;
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }

  /**
   * Marker {@link ProducerPostProcessor} that wraps the freshly created Kafka {@link Producer} via
   * {@link IchnoisCoreKafkaProducer#wrap}.
   */
  static final class IchnoisCoreProducerPostProcessor<K, V> implements ProducerPostProcessor<K, V> {
    @Override
    public @NotNull Producer<K, V> apply(final @NotNull Producer<K, V> producer) {
      return IchnoisCoreKafkaProducer.wrap(
          producer, ScopesAdapter.getInstance(), "auto.queue.spring7.kafka.producer");
    }
  }
}
