package io.ichnois.spring.boot4;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.log4j2.IchnoisCoreAppender;
import org.apache.logging.log4j.core.LoggerContext;
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
@ConditionalOnProperty(
    name = "ichnois.logging.enabled",
    havingValue = "true",
    matchIfMissing = false)
@ConditionalOnBean(IchnoisCoreProperties.class)
public class IchnoisCoreLog4j2AppenderAutoConfiguration {

  @Bean
  public @NotNull IchnoisCoreLog4j2Initializer ichnoisLog4j2Initializer(
      final @NotNull IchnoisCoreProperties ichnoisProperties) {
    return new IchnoisCoreLog4j2Initializer(ichnoisProperties);
  }
}
