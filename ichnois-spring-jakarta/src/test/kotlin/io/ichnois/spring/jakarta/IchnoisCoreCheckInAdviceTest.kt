package io.ichnois.spring.jakarta

import io.ichnois.CheckIn
import io.ichnois.CheckInStatus
import io.ichnois.IScopes
import io.ichnois.IIchnoisCoreLifecycleToken
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreOptions
import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.spring.jakarta.checkin.IchnoisCoreCheckIn
import io.ichnois.spring.jakarta.checkin.IchnoisCoreCheckInAdviceConfiguration
import io.ichnois.spring.jakarta.checkin.IchnoisCoreCheckInPointcutConfiguration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.assertThrows
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.util.StringValueResolver

@RunWith(SpringRunner::class)
@SpringJUnitConfig(IchnoisCoreCheckInAdviceTest.Config::class)
@TestPropertySource(properties = ["my.cron.slug = mypropertycronslug"])
class IchnoisCoreCheckInAdviceTest {

  @Autowired lateinit var sampleService: SampleService

  @Autowired lateinit var sampleServiceNoSlug: SampleServiceNoSlug

  @Autowired lateinit var sampleServiceHeartbeat: SampleServiceHeartbeat

  @Autowired lateinit var sampleServiceSpringProperties: SampleServiceSpringProperties

  @Autowired lateinit var scopes: IScopes

  val lifecycleToken = mock<IIchnoisCoreLifecycleToken>()

  @BeforeTest
  fun setup() {
    reset(scopes)
    whenever(scopes.options).thenReturn(IchnoisCoreOptions())
    whenever(scopes.forkedScopes(any())).thenReturn(scopes)
    whenever(scopes.makeCurrent()).thenReturn(lifecycleToken)
  }

  @Test
  fun `when method is annotated with @IchnoisCoreCheckIn, every method call creates two check-ins`() {
    val checkInId = IchnoisCoreId()
    val checkInCaptor = argumentCaptor<CheckIn>()
    whenever(scopes.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
    val result = sampleService.hello()
    assertEquals(1, result)
    assertEquals(2, checkInCaptor.allValues.size)

    val inProgressCheckIn = checkInCaptor.firstValue
    assertEquals("monitor_slug_1", inProgressCheckIn.monitorSlug)
    assertEquals(CheckInStatus.IN_PROGRESS.apiName(), inProgressCheckIn.status)

    val doneCheckIn = checkInCaptor.lastValue
    assertEquals("monitor_slug_1", doneCheckIn.monitorSlug)
    assertEquals(CheckInStatus.OK.apiName(), doneCheckIn.status)

    val order = inOrder(scopes, lifecycleToken)
    order.verify(scopes).forkedScopes(any())
    order.verify(scopes).makeCurrent()
    order.verify(scopes, times(2)).captureCheckIn(any())
    order.verify(lifecycleToken).close()
  }

  @Test
  fun `when method is annotated with @IchnoisCoreCheckIn, every method call creates two check-ins error`() {
    val checkInId = IchnoisCoreId()
    val checkInCaptor = argumentCaptor<CheckIn>()
    whenever(scopes.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
    assertThrows<RuntimeException> { sampleService.oops() }
    assertEquals(2, checkInCaptor.allValues.size)

    val inProgressCheckIn = checkInCaptor.firstValue
    assertEquals("monitor_slug_1e", inProgressCheckIn.monitorSlug)
    assertEquals(CheckInStatus.IN_PROGRESS.apiName(), inProgressCheckIn.status)

    val doneCheckIn = checkInCaptor.lastValue
    assertEquals("monitor_slug_1e", doneCheckIn.monitorSlug)
    assertEquals(CheckInStatus.ERROR.apiName(), doneCheckIn.status)

    val order = inOrder(scopes, lifecycleToken)
    order.verify(scopes).forkedScopes(any())
    order.verify(scopes).makeCurrent()
    order.verify(scopes, times(2)).captureCheckIn(any())
    order.verify(lifecycleToken).close()
  }

  @Test
  fun `when method is annotated with @IchnoisCoreCheckIn and heartbeat only, every method call creates only one check-in at the end`() {
    val checkInId = IchnoisCoreId()
    val checkInCaptor = argumentCaptor<CheckIn>()
    whenever(scopes.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
    val result = sampleServiceHeartbeat.hello()
    assertEquals(1, result)
    assertEquals(1, checkInCaptor.allValues.size)

    val doneCheckIn = checkInCaptor.lastValue
    assertEquals("monitor_slug_2", doneCheckIn.monitorSlug)
    assertEquals(CheckInStatus.OK.apiName(), doneCheckIn.status)
    assertNotNull(doneCheckIn.duration)

    val order = inOrder(scopes, lifecycleToken)
    order.verify(scopes).forkedScopes(any())
    order.verify(scopes).makeCurrent()
    order.verify(scopes).captureCheckIn(any())
    order.verify(lifecycleToken).close()
  }

  @Test
  fun `when method is annotated with @IchnoisCoreCheckIn and heartbeat only, every method call creates only one check-in at the end with error`() {
    val checkInId = IchnoisCoreId()
    val checkInCaptor = argumentCaptor<CheckIn>()
    whenever(scopes.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
    assertThrows<RuntimeException> { sampleServiceHeartbeat.oops() }
    assertEquals(1, checkInCaptor.allValues.size)

    val doneCheckIn = checkInCaptor.lastValue
    assertEquals("monitor_slug_2e", doneCheckIn.monitorSlug)
    assertEquals(CheckInStatus.ERROR.apiName(), doneCheckIn.status)
    assertNotNull(doneCheckIn.duration)

    val order = inOrder(scopes, lifecycleToken)
    order.verify(scopes).forkedScopes(any())
    order.verify(scopes).makeCurrent()
    order.verify(scopes).captureCheckIn(any())
    order.verify(lifecycleToken).close()
  }

  @Test
  fun `when method is annotated with @IchnoisCoreCheckIn but slug is missing, does not create check-in`() {
    val checkInId = IchnoisCoreId()
    val checkInCaptor = argumentCaptor<CheckIn>()
    whenever(scopes.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
    val result = sampleServiceNoSlug.hello()
    assertEquals(1, result)
    assertEquals(0, checkInCaptor.allValues.size)

    verify(scopes, never()).forkedScopes(any())
    verify(scopes, never()).makeCurrent()
    verify(scopes, never()).captureCheckIn(any())
    verify(lifecycleToken, never()).close()
  }

  @Test
  fun `when @IchnoisCoreCheckIn is passed a spring property it is resolved correctly`() {
    val checkInId = IchnoisCoreId()
    val checkInCaptor = argumentCaptor<CheckIn>()
    whenever(scopes.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
    val result = sampleServiceSpringProperties.hello()
    assertEquals(1, result)
    assertEquals(1, checkInCaptor.allValues.size)

    val doneCheckIn = checkInCaptor.lastValue
    assertEquals("mypropertycronslug", doneCheckIn.monitorSlug)
    assertEquals(CheckInStatus.OK.apiName(), doneCheckIn.status)
    assertNotNull(doneCheckIn.duration)

    val order = inOrder(scopes, lifecycleToken)
    order.verify(scopes).forkedScopes(any())
    order.verify(scopes).makeCurrent()
    order.verify(scopes).captureCheckIn(any())
    order.verify(lifecycleToken).close()
  }

  @Test
  fun `when @IchnoisCoreCheckIn is passed a spring property that does not exist, raw value is used`() {
    val checkInId = IchnoisCoreId()
    val checkInCaptor = argumentCaptor<CheckIn>()
    whenever(scopes.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
    val result = sampleServiceSpringProperties.helloUnresolvedProperty()
    assertEquals(1, result)
    assertEquals(1, checkInCaptor.allValues.size)

    val doneCheckIn = checkInCaptor.lastValue
    assertEquals("\${my.cron.unresolved.property}", doneCheckIn.monitorSlug)
    assertEquals(CheckInStatus.OK.apiName(), doneCheckIn.status)
    assertNotNull(doneCheckIn.duration)

    val order = inOrder(scopes, lifecycleToken)
    order.verify(scopes).forkedScopes(any())
    order.verify(scopes).makeCurrent()
    order.verify(scopes).captureCheckIn(any())
    order.verify(lifecycleToken).close()
  }

  @Test
  fun `when @IchnoisCoreCheckIn is passed a spring property that causes an exception, raw value is used`() {
    val checkInId = IchnoisCoreId()
    val checkInCaptor = argumentCaptor<CheckIn>()
    whenever(scopes.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
    val result = sampleServiceSpringProperties.helloExceptionProperty()
    assertEquals(1, result)
    assertEquals(1, checkInCaptor.allValues.size)

    val doneCheckIn = checkInCaptor.lastValue
    assertEquals("\${my.cron.exception.property}", doneCheckIn.monitorSlug)
    assertEquals(CheckInStatus.OK.apiName(), doneCheckIn.status)
    assertNotNull(doneCheckIn.duration)

    val order = inOrder(scopes, lifecycleToken)
    order.verify(scopes).forkedScopes(any())
    order.verify(scopes).makeCurrent()
    order.verify(scopes).captureCheckIn(any())
    order.verify(lifecycleToken).close()
  }

  @Configuration
  @EnableAspectJAutoProxy(proxyTargetClass = true)
  @Import(IchnoisCoreCheckInAdviceConfiguration::class, IchnoisCoreCheckInPointcutConfiguration::class)
  open class Config {

    @Bean open fun sampleService() = SampleService()

    @Bean open fun sampleServiceNoSlug() = SampleServiceNoSlug()

    @Bean open fun sampleServiceHeartbeat() = SampleServiceHeartbeat()

    @Bean open fun sampleServiceSpringProperties() = SampleServiceSpringProperties()

    @Bean
    open fun scopes(): IScopes {
      val scopes = mock<IScopes>()
      IchnoisCore.setCurrentScopes(scopes)
      return scopes
    }

    companion object {
      @Bean
      @JvmStatic
      fun propertySourcesPlaceholderConfigurer() = MyPropertyPlaceholderConfigurer()
    }
  }

  open class SampleService {

    @IchnoisCoreCheckIn("monitor_slug_1") open fun hello() = 1

    @IchnoisCoreCheckIn("monitor_slug_1e")
    open fun oops() {
      throw RuntimeException("thrown on purpose")
    }
  }

  open class SampleServiceNoSlug {

    @IchnoisCoreCheckIn open fun hello() = 1
  }

  open class SampleServiceHeartbeat {

    @IchnoisCoreCheckIn(monitorSlug = "monitor_slug_2", heartbeat = true) open fun hello() = 1

    @IchnoisCoreCheckIn(monitorSlug = "monitor_slug_2e", heartbeat = true)
    open fun oops() {
      throw RuntimeException("thrown on purpose")
    }
  }

  open class SampleServiceSpringProperties {

    @IchnoisCoreCheckIn("\${my.cron.slug}", heartbeat = true) open fun hello() = 1

    @IchnoisCoreCheckIn("\${my.cron.unresolved.property}", heartbeat = true)
    open fun helloUnresolvedProperty() = 1

    @IchnoisCoreCheckIn("\${my.cron.exception.property}", heartbeat = true)
    open fun helloExceptionProperty() = 1
  }

  class MyPropertyPlaceholderConfigurer : PropertySourcesPlaceholderConfigurer() {

    override fun doProcessProperties(
      beanFactoryToProcess: ConfigurableListableBeanFactory,
      valueResolver: StringValueResolver,
    ) {
      val wrappedResolver = StringValueResolver { strVal: String ->
        if ("\${my.cron.exception.property}".equals(strVal)) {
          throw IllegalArgumentException("Cannot resolve property: $strVal")
        } else {
          valueResolver.resolveStringValue(strVal)
        }
      }
      super.doProcessProperties(beanFactoryToProcess, wrappedResolver)
    }
  }
}
