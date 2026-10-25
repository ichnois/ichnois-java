package io.ichnois.samples.spring.jakarta;

import io.ichnois.IScopes;
import io.ichnois.spring.jakarta.IchnoisCoreUserFilter;
import io.ichnois.spring.jakarta.IchnoisCoreUserProvider;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(IchnoisCoreConfig.class)
public class AppConfig {

  @Bean
  IchnoisCoreUserFilter ichnoisUserFilter(
      final IScopes scopes, final List<IchnoisCoreUserProvider> ichnoisUserProviders) {
    return new IchnoisCoreUserFilter(scopes, ichnoisUserProviders);
  }
}
