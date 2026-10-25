package io.ichnois.spring;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.EventProcessor;
import io.ichnois.IScopes;
import io.ichnois.ITransportFactory;
import io.ichnois.Integration;
import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCore;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.IchnoisCoreOptions.TracesSamplerCallback;
import io.ichnois.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Initializes IchnoisCore after all beans are registered. Closes IchnoisCore on Spring application context
 * destroy.
 */
@Open
public class IchnoisCoreInitBeanPostProcessor
    implements BeanPostProcessor, ApplicationContextAware, DisposableBean {
  private @Nullable ApplicationContext applicationContext;

  private final @NotNull IScopes scopes;

  public IchnoisCoreInitBeanPostProcessor() {
    this(ScopesAdapter.getInstance());
  }

  IchnoisCoreInitBeanPostProcessor(final @NotNull IScopes scopes) {
    Objects.requireNonNull(scopes, "scopes are required");
    this.scopes = scopes;
  }

  @Override
  @SuppressWarnings({"unchecked", "deprecation"})
  public @NotNull Object postProcessAfterInitialization(
      final @NotNull Object bean, @NotNull final String beanName) throws BeansException {
    if (bean instanceof IchnoisCoreOptions) {
      final IchnoisCoreOptions options = (IchnoisCoreOptions) bean;

      if (applicationContext != null) {
        applicationContext
            .getBeanProvider(TracesSamplerCallback.class)
            .ifAvailable(options::setTracesSampler);
        applicationContext
            .getBeanProvider(ITransportFactory.class)
            .ifAvailable(options::setTransportFactory);
        applicationContext
            .getBeanProvider(IchnoisCoreOptions.BeforeSendCallback.class)
            .ifAvailable(options::setBeforeSend);
        applicationContext
            .getBeanProvider(IchnoisCoreOptions.BeforeSendTransactionCallback.class)
            .ifAvailable(options::setBeforeSendTransaction);
        applicationContext
            .getBeanProvider(IchnoisCoreOptions.BeforeBreadcrumbCallback.class)
            .ifAvailable(options::setBeforeBreadcrumb);
        applicationContext
            .getBeanProvider(IchnoisCoreOptions.OnDiscardCallback.class)
            .ifAvailable(options::setOnDiscard);
        applicationContext
            .getBeansOfType(EventProcessor.class)
            .values()
            .forEach(options::addEventProcessor);
        applicationContext
            .getBeansOfType(Integration.class)
            .values()
            .forEach(options::addIntegration);
        applicationContext
            .getBeanProvider(IchnoisCore.OptionsConfiguration.class)
            .ifAvailable(optionsConfiguration -> optionsConfiguration.configure(options));
      }
      IchnoisCore.init(options);
    }
    return bean;
  }

  @Override
  public void setApplicationContext(final @NotNull ApplicationContext applicationContext)
      throws BeansException {
    this.applicationContext = applicationContext;
  }

  @Override
  public void destroy() {
    scopes.close();
  }
}
