package io.ichnois.spring.jakarta.tracing

import io.ichnois.IScopes
import io.ichnois.IIchnoisCoreLifecycleToken
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreTracer
import io.ichnois.SpanStatus
import io.ichnois.TraceContext
import io.ichnois.TransactionContext
import io.ichnois.TransactionOptions
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringJUnitConfig(IchnoisCoreTransactionAdviceTest.Config::class)
class IchnoisCoreTransactionAdviceTest {
  @Autowired lateinit var sampleService: SampleService

  @Autowired lateinit var classAnnotatedSampleService: ClassAnnotatedSampleService

  @Autowired
  lateinit var classAnnotatedWithOperationSampleService: ClassAnnotatedWithOperationSampleService

  @Autowired lateinit var scopes: IScopes

  val lifecycleToken = mock<IIchnoisCoreLifecycleToken>()

  @BeforeTest
  fun setup() {
    reset(scopes)
    whenever(
        scopes.startTransaction(
          any(),
          check<TransactionOptions> {
            assertTrue(it.isBindToScope)
            assertThat(it.origin).isEqualTo("auto.function.spring_jakarta.advice")
          },
        )
      )
      .thenAnswer { IchnoisCoreTracer(it.arguments[0] as TransactionContext, scopes) }
    whenever(scopes.options)
      .thenReturn(IchnoisCoreOptions().apply { dsn = "https://key@ichnois-h2jw.onrender.com/proj" })
    whenever(scopes.forkedScopes(any())).thenReturn(scopes)
    whenever(scopes.makeCurrent()).thenReturn(lifecycleToken)
  }

  @Test
  fun `creates transaction around method annotated with @IchnoisCoreTransaction`() {
    sampleService.methodWithTransactionNameSet()
    verify(scopes)
      .captureTransaction(
        check {
          assertThat(it.transaction).isEqualTo("customName")
          assertThat(it.contexts.trace!!.operation).isEqualTo("bean")
          assertThat(it.status).isEqualTo(SpanStatus.OK)
        },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `when method annotated with @IchnoisCoreTransaction throws exception, sets error status on transaction`() {
    assertThrows<RuntimeException> { sampleService.methodThrowingException() }
    verify(scopes)
      .captureTransaction(
        check { assertThat(it.status).isEqualTo(SpanStatus.INTERNAL_ERROR) },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `when @IchnoisCoreTransaction has no name set, sets transaction name as className dot methodName`() {
    sampleService.methodWithoutTransactionNameSet()
    verify(scopes)
      .captureTransaction(
        check {
          assertThat(it.transaction).isEqualTo("SampleService.methodWithoutTransactionNameSet")
          assertThat(it.contexts.trace!!.operation).isEqualTo("op")
        },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `when transaction is already active, does not start new transaction`() {
    whenever(scopes.options).thenReturn(IchnoisCoreOptions())
    whenever(scopes.span).then { IchnoisCoreTracer(TransactionContext("aTransaction", "op"), scopes) }

    sampleService.methodWithTransactionNameSet()

    verify(scopes, times(0)).captureTransaction(any(), any<TraceContext>())
  }

  @Test
  fun `creates transaction around method in class annotated with @IchnoisCoreTransaction`() {
    classAnnotatedSampleService.hello()
    verify(scopes)
      .captureTransaction(
        check {
          assertThat(it.transaction).isEqualTo("ClassAnnotatedSampleService.hello")
          assertThat(it.contexts.trace!!.operation).isEqualTo("op")
        },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `creates transaction with operation set around method in class annotated with @IchnoisCoreTransaction`() {
    classAnnotatedWithOperationSampleService.hello()
    verify(scopes)
      .captureTransaction(
        check {
          assertThat(it.transaction).isEqualTo("ClassAnnotatedWithOperationSampleService.hello")
          assertThat(it.contexts.trace!!.operation).isEqualTo("my-op")
        },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `pushes the scope when advice starts`() {
    classAnnotatedSampleService.hello()
    verify(scopes).forkedScopes(any())
    verify(scopes).makeCurrent()
  }

  @Test
  fun `pops the scope when advice finishes`() {
    classAnnotatedSampleService.hello()
    verify(lifecycleToken).close()
  }

  @Configuration
  @EnableAspectJAutoProxy(proxyTargetClass = true)
  @Import(IchnoisCoreTracingConfiguration::class)
  open class Config {
    @Bean open fun sampleService() = SampleService()

    @Bean open fun classAnnotatedSampleService() = ClassAnnotatedSampleService()

    @Bean
    open fun classAnnotatedWithOperationSampleService() = ClassAnnotatedWithOperationSampleService()

    @Bean
    open fun scopes(): IScopes {
      val scopes = mock<IScopes>()
      IchnoisCore.setCurrentScopes(scopes)
      return scopes
    }
  }

  open class SampleService {
    @IchnoisCoreTransaction(name = "customName", operation = "bean")
    open fun methodWithTransactionNameSet() = Unit

    @IchnoisCoreTransaction(operation = "op") open fun methodWithoutTransactionNameSet() = Unit

    @IchnoisCoreTransaction(operation = "op")
    open fun methodThrowingException(): Nothing = throw RuntimeException()
  }

  @IchnoisCoreTransaction(operation = "op")
  open class ClassAnnotatedSampleService {
    open fun hello() = Unit
  }

  @IchnoisCoreTransaction(operation = "my-op")
  open class ClassAnnotatedWithOperationSampleService {
    open fun hello() = Unit
  }
}
