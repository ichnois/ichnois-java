package io.ichnois.spring7.tracing;

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

/** Creates advice infrastructure for {@link IchnoisCoreSpan} and {@link IchnoisCoreTransaction}. */
@Configuration(proxyBeanMethods = false)
@Open
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class IchnoisCoreAdviceConfiguration {

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public @NotNull Advice ichnoisTransactionAdvice() {
    return new IchnoisCoreTransactionAdvice();
  }

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public @NotNull Advisor ichnoisTransactionAdvisor(
      final @NotNull @Qualifier("ichnoisTransactionPointcut") Pointcut ichnoisTransactionPointcut,
      final @NotNull @Qualifier("ichnoisTransactionAdvice") Advice ichnoisTransactionAdvice) {
    return new DefaultPointcutAdvisor(ichnoisTransactionPointcut, ichnoisTransactionAdvice);
  }

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public @NotNull Advice ichnoisSpanAdvice() {
    return new IchnoisCoreSpanAdvice();
  }

  @Bean
  @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
  public @NotNull Advisor ichnoisSpanAdvisor(
      final @NotNull @Qualifier("ichnoisSpanPointcut") Pointcut ichnoisSpanPointcut,
      final @NotNull @Qualifier("ichnoisSpanAdvice") Advice ichnoisSpanAdvice) {
    return new DefaultPointcutAdvisor(ichnoisSpanPointcut, ichnoisSpanAdvice);
  }
}
