package io.ichnois.spring7

import io.ichnois.IScopes
import kotlin.test.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class IchnoisCoreInitBeanPostProcessorTest {
  @Test
  fun closesIchnoisCoreOnApplicationContextDestroy() {
    val ctx = AnnotationConfigApplicationContext(TestConfig::class.java)
    val scopes = ctx.getBean(IScopes::class.java)
    ctx.close()
    verify(scopes).close()
  }

  @Configuration
  open class TestConfig {
    @Bean(destroyMethod = "") open fun scopes() = mock<IScopes>()

    @Bean open fun ichnoisInitBeanPostProcessor() = IchnoisCoreInitBeanPostProcessor(scopes())
  }
}
