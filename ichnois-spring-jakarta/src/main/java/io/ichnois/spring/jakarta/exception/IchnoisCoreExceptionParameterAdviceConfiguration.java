package io.ichnois.spring.jakarta.exception;

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

/** Creates advice infrastructure for {@link IchnoisCoreCaptureExceptionParameter}. */
@Configuration(proxyBeanMethods = false)
@Open
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class IchnoisCoreExceptionParameterAdviceConfiguration {

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public @NotNull Advice ichnoisCaptureExceptionParameterAdvice() {
    return new IchnoisCoreCaptureExceptionParameterAdvice();
  }

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public @NotNull Advisor ichnoisCaptureExceptionParameterAdvisor(
      final @NotNull @Qualifier("ichnoisCaptureExceptionParameterPointcut") Pointcut
              ichnoisCaptureExceptionParameterPointcut,
      final @NotNull @Qualifier("ichnoisCaptureExceptionParameterAdvice") Advice
              ichnoisCaptureExceptionParameterAdvice) {
    return new DefaultPointcutAdvisor(
        ichnoisCaptureExceptionParameterPointcut, ichnoisCaptureExceptionParameterAdvice);
  }
}
