package io.ichnois.spring.boot4;

import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.okhttp.IchnoisCoreOkHttpEventListener;
import io.ichnois.okhttp.IchnoisCoreOkHttpInterceptor;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

final class IchnoisCoreOkHttpClientBeanPostProcessor implements BeanPostProcessor, PriorityOrdered {

  @Override
  public @NotNull Object postProcessAfterInitialization(
      final @NotNull Object bean, final @NotNull String beanName) throws BeansException {
    if (!(bean instanceof OkHttpClient client)) {
      return bean;
    }

    if (client.getClass() != OkHttpClient.class) {
      ScopesAdapter.getInstance()
          .getOptions()
          .getLogger()
          .log(
              IchnoisCoreLevel.WARNING,
              "IchnoisCore OkHttp auto-instrumentation skipped for bean '%s' (%s) because replacing "
                  + "an OkHttpClient subclass would not preserve its type. Configure IchnoisCore "
                  + "instrumentation manually for this client.",
              beanName,
              client.getClass().getName());
      return client;
    }

    final boolean addInterceptor = !hasIchnoisCoreInterceptor(client);
    final boolean wrapEventListener =
        !(client.eventListenerFactory() instanceof IchnoisCoreEventListenerFactory);
    if (!addInterceptor && !wrapEventListener) {
      return client;
    }

    final @NotNull OkHttpClient.Builder builder = client.newBuilder();
    if (addInterceptor) {
      builder.addInterceptor(new IchnoisCoreOkHttpInterceptor());
    }
    if (wrapEventListener) {
      builder.eventListenerFactory(new IchnoisCoreEventListenerFactory(client.eventListenerFactory()));
    }
    return builder.build();
  }

  private static boolean hasIchnoisCoreInterceptor(final @NotNull OkHttpClient client) {
    for (final @NotNull Interceptor interceptor : client.interceptors()) {
      if (interceptor instanceof IchnoisCoreOkHttpInterceptor) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }

  private static final class IchnoisCoreEventListenerFactory implements EventListener.Factory {
    private final @NotNull EventListener.Factory delegate;

    private IchnoisCoreEventListenerFactory(final @NotNull EventListener.Factory delegate) {
      this.delegate = delegate;
    }

    @Override
    public @NotNull EventListener create(final @NotNull Call call) {
      final @NotNull EventListener original = delegate.create(call);
      if (original instanceof IchnoisCoreOkHttpEventListener) {
        return original;
      }
      return new IchnoisCoreOkHttpEventListener(ScopesAdapter.getInstance(), original);
    }
  }
}
