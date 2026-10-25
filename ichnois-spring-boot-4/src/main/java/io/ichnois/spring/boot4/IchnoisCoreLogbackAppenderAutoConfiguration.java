package io.ichnois.spring.boot4;

import ch.qos.logback.classic.LoggerContext;
import com.jakewharton.nopen.annotation.Open;
import io.ichnois.logback.IchnoisCoreAppender;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Auto-configures {@link IchnoisCoreAppender}. */
@Configuration(proxyBeanMethods = false)
@Open
@ConditionalOnClass({LoggerContext.class, IchnoisCoreAppender.class})
@ConditionalOnProperty(name = "ichnois.logging.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(IchnoisCoreProperties.class)
public class IchnoisCoreLogbackAppenderAutoConfiguration {

  @Bean
  public @NotNull IchnoisCoreLogbackInitializer ichnoisLogbackInitializer(
      final @NotNull IchnoisCoreProperties ichnoisProperties) {
    return new IchnoisCoreLogbackInitializer(ichnoisProperties);
  }
}
