package io.ichnois.spring7.kafka;

import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCoreLevel;
import java.lang.reflect.Field;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.kafka.config.AbstractKafkaListenerContainerFactory;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * Registers {@link IchnoisCoreKafkaRecordInterceptor} on {@link AbstractKafkaListenerContainerFactory}
 * beans. If an existing {@link RecordInterceptor} is already set, it is composed as a delegate.
 */
@ApiStatus.Internal
public final class IchnoisCoreKafkaConsumerBeanPostProcessor
    implements BeanPostProcessor, PriorityOrdered {

  private static final @NotNull String RECORD_INTERCEPTOR_FIELD_NAME = "recordInterceptor";

  private final @NotNull String recordInterceptorFieldName;

  public IchnoisCoreKafkaConsumerBeanPostProcessor() {
    this(RECORD_INTERCEPTOR_FIELD_NAME);
  }

  IchnoisCoreKafkaConsumerBeanPostProcessor(final @NotNull String recordInterceptorFieldName) {
    this.recordInterceptorFieldName = recordInterceptorFieldName;
  }

  private static final class InterceptorReadFailedException extends Exception {
    private static final long serialVersionUID = 1L;

    InterceptorReadFailedException(final @NotNull Throwable cause) {
      super(cause);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public @NotNull Object postProcessAfterInitialization(
      final @NotNull Object bean, final @NotNull String beanName) throws BeansException {
    if (bean instanceof AbstractKafkaListenerContainerFactory) {
      final @NotNull AbstractKafkaListenerContainerFactory<?, ?, ?> factory =
          (AbstractKafkaListenerContainerFactory<?, ?, ?>) bean;

      final @Nullable RecordInterceptor<?, ?> existing;
      try {
        existing = getExistingInterceptor(factory);
      } catch (InterceptorReadFailedException e) {
        ScopesAdapter.getInstance()
            .getOptions()
            .getLogger()
            .log(
                IchnoisCoreLevel.ERROR,
                e,
                "IchnoisCore Kafka consumer tracing disabled for factory '%s' \u2014 could not read "
                    + "existing recordInterceptor via reflection. Refusing to install IchnoisCore's "
                    + "interceptor to avoid overwriting a customer-configured RecordInterceptor.",
                beanName);
        return bean;
      }

      if (existing instanceof IchnoisCoreKafkaRecordInterceptor) {
        return bean;
      }

      @SuppressWarnings("rawtypes")
      final RecordInterceptor ichnoisInterceptor =
          new IchnoisCoreKafkaRecordInterceptor<>(ScopesAdapter.getInstance(), existing);
      factory.setRecordInterceptor(ichnoisInterceptor);
    }
    return bean;
  }

  private @Nullable RecordInterceptor<?, ?> getExistingInterceptor(
      final @NotNull AbstractKafkaListenerContainerFactory<?, ?, ?> factory)
      throws InterceptorReadFailedException {
    try {
      final @NotNull Field field =
          AbstractKafkaListenerContainerFactory.class.getDeclaredField(recordInterceptorFieldName);
      field.setAccessible(true);
      return (RecordInterceptor<?, ?>) field.get(factory);
    } catch (NoSuchFieldException | IllegalAccessException | RuntimeException e) {
      throw new InterceptorReadFailedException(e);
    }
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
