package io.ichnois.spring.jakarta;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.IchnoisCoreOptions;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;

/** Registers Spring Web specific IchnoisCore beans. */
@Configuration(proxyBeanMethods = false)
@Open
public class IchnoisCoreWebConfiguration {

  @Bean
  @Lazy
  @Order(0)
  public @NotNull HttpServletRequestIchnoisCoreUserProvider httpServletRequestIchnoisCoreUserProvider(
      final @NotNull IchnoisCoreOptions ichnoisOptions) {
    return new HttpServletRequestIchnoisCoreUserProvider(ichnoisOptions);
  }
}
