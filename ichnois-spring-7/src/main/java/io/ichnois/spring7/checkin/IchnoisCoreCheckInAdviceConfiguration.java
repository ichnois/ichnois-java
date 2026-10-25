package io.ichnois.spring7.checkin;

import com.jakewharton.nopen.annotation.Open;
import org.aopalliance.aop.Advice;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

@Configuration(proxyBeanMethods = false)
@Open
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class IchnoisCoreCheckInAdviceConfiguration {

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public @NotNull Advice ichnoisCheckInAdvice() {
    return new IchnoisCoreCheckInAdvice();
  }

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public @NotNull Advisor ichnoisCheckInAdvisor(
      final @NotNull @Qualifier("ichnoisCheckInPointcut") Pointcut ichnoisCheckInPointcut,
      final @NotNull @Qualifier("ichnoisCheckInAdvice") Advice ichnoisCheckInAdvice) {
    return new DefaultPointcutAdvisor(ichnoisCheckInPointcut, ichnoisCheckInAdvice);
  }
}
