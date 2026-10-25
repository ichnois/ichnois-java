package io.ichnois.spring.exception

import io.ichnois.Hint
import io.ichnois.IScopes
import io.ichnois.IchnoisCore
import io.ichnois.exception.ExceptionMechanismException
import io.ichnois.test.initForTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringJUnitConfig(IchnoisCoreCaptureExceptionParameterAdviceTest.Config::class)
class IchnoisCoreCaptureExceptionParameterAdviceTest {
  @Autowired lateinit var sampleService: SampleService

  @Autowired lateinit var scopes: IScopes

  @BeforeTest
  fun setup() {
    reset(scopes)
    initForTest { it.dsn = "https://key@ichnois-h2jw.onrender.com/proj" }
    IchnoisCore.setCurrentScopes(scopes)
  }

  @AfterTest
  fun teardown() {
    IchnoisCore.close()
  }

  @Test
  fun `captures exception passed to method annotated with @IchnoisCoreCaptureException`() {
    val exception = RuntimeException("test exception")
    sampleService.methodTakingAnException(exception)
    verify(scopes)
      .captureException(
        check {
          assertTrue(it is ExceptionMechanismException)
          assertEquals(exception, it.throwable)
          assertEquals("IchnoisCoreSpring5CaptureExceptionParameterAdvice", it.exceptionMechanism.type)
        },
        any<Hint>(),
      )
  }

  @Configuration
  @EnableAspectJAutoProxy(proxyTargetClass = true)
  @Import(IchnoisCoreCaptureExceptionParameterConfiguration::class)
  open class Config {
    @Bean open fun sampleService() = SampleService()

    @Bean
    open fun scopes(): IScopes {
      val scopes = mock<IScopes>()
      IchnoisCore.setCurrentScopes(scopes)
      return scopes
    }
  }

  open class SampleService {
    @IchnoisCoreCaptureExceptionParameter open fun methodTakingAnException(e: Exception) = Unit
  }
}
