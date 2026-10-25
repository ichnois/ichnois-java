package io.ichnois.util

import com.google.common.truth.Truth.assertThat
import io.ichnois.CheckIn
import io.ichnois.CheckInStatus
import io.ichnois.FilterString
import io.ichnois.IScopes
import io.ichnois.IIchnoisCoreLifecycleToken
import io.ichnois.MonitorConfig
import io.ichnois.MonitorSchedule
import io.ichnois.MonitorScheduleUnit
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreOptions
import io.ichnois.time.MonotonicTicker
import io.ichnois.time.TestMonotonicTicker
import java.lang.AssertionError
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CheckInUtilsTest {
  @Test
  fun `ignores exact match`() {
    assertTrue(CheckInUtils.isIgnored(listOf(FilterString("slugA")), "slugA"))
  }

  @Test
  fun `ignores regex match`() {
    assertTrue(CheckInUtils.isIgnored(listOf(FilterString("slug-.*")), "slug-A"))
  }

  @Test
  fun `does not ignore if ignored list is null`() {
    assertFalse(CheckInUtils.isIgnored(null, "slugA"))
  }

  @Test
  fun `does not ignore if ignored list is empty`() {
    assertFalse(CheckInUtils.isIgnored(emptyList(), "slugA"))
  }

  @Test
  fun `does not ignore if slug is not in ignored list`() {
    assertFalse(CheckInUtils.isIgnored(listOf(FilterString("slugB")), "slugA"))
  }

  @Test
  fun `does not ignore if slug is does not match ignored list`() {
    assertFalse(CheckInUtils.isIgnored(listOf(FilterString("slug-.*")), "slugA"))
  }

  @Test
  fun `sends check-in for wrapped supplier`() {
    Mockito.mockStatic(IchnoisCore::class.java).use { ichnois ->
      val scopes = mock<IScopes>()
      val lifecycleToken = mock<IIchnoisCoreLifecycleToken>()
      ichnois.`when`<Any> { IchnoisCore.getCurrentScopes() }.thenReturn(scopes)
      ichnois.`when`<Any> { IchnoisCore.forkedScopes(any()) }.then { scopes.forkedScopes("test") }
      whenever(scopes.forkedScopes(any())).thenReturn(scopes)
      whenever(scopes.makeCurrent()).thenReturn(lifecycleToken)
      whenever(scopes.options).thenReturn(IchnoisCoreOptions())
      val returnValue =
        CheckInUtils.withCheckIn("monitor-1") {
          return@withCheckIn "test1"
        }

      assertEquals("test1", returnValue)
      inOrder(scopes, lifecycleToken) {
        verify(scopes).forkedScopes(any())
        verify(scopes).makeCurrent()
        verify(scopes).configureScope(any())
        verify(scopes)
          .captureCheckIn(
            check {
              assertEquals("monitor-1", it.monitorSlug)
              assertEquals(CheckInStatus.IN_PROGRESS.apiName(), it.status)
            }
          )
        verify(scopes)
          .captureCheckIn(
            check {
              assertEquals("monitor-1", it.monitorSlug)
              assertEquals(CheckInStatus.OK.apiName(), it.status)
            }
          )
        verify(lifecycleToken).close()
      }
    }
  }

  @Test
  fun `sends check-in for wrapped supplier with environment`() {
    Mockito.mockStatic(IchnoisCore::class.java).use { ichnois ->
      val scopes = mock<IScopes>()
      val lifecycleToken = mock<IIchnoisCoreLifecycleToken>()
      ichnois.`when`<Any> { IchnoisCore.getCurrentScopes() }.thenReturn(scopes)
      ichnois.`when`<Any> { IchnoisCore.forkedScopes(any()) }.then { scopes.forkedScopes("test") }
      whenever(scopes.forkedScopes(any())).thenReturn(scopes)
      whenever(scopes.makeCurrent()).thenReturn(lifecycleToken)
      whenever(scopes.options).thenReturn(IchnoisCoreOptions())
      val returnValue =
        CheckInUtils.withCheckIn("monitor-1", "environment-1") {
          return@withCheckIn "test1"
        }

      assertEquals("test1", returnValue)
      inOrder(scopes, lifecycleToken) {
        verify(scopes).forkedScopes(any())
        verify(scopes).makeCurrent()
        verify(scopes).configureScope(any())
        verify(scopes)
          .captureCheckIn(
            check {
              assertEquals("monitor-1", it.monitorSlug)
              assertEquals("environment-1", it.environment)
              assertEquals(CheckInStatus.IN_PROGRESS.apiName(), it.status)
            }
          )
        verify(scopes)
          .captureCheckIn(
            check {
              assertEquals("monitor-1", it.monitorSlug)
              assertEquals("environment-1", it.environment)
              assertEquals(CheckInStatus.OK.apiName(), it.status)
            }
          )
        verify(lifecycleToken).close()
      }
    }
  }

  @Test
  fun `sends check-in for wrapped supplier with exception`() {
    Mockito.mockStatic(IchnoisCore::class.java).use { ichnois ->
      val scopes = mock<IScopes>()
      val lifecycleToken = mock<IIchnoisCoreLifecycleToken>()
      ichnois.`when`<Any> { IchnoisCore.getCurrentScopes() }.thenReturn(scopes)
      ichnois.`when`<Any> { IchnoisCore.forkedScopes(any()) }.then { scopes.forkedScopes("test") }
      whenever(scopes.forkedScopes(any())).thenReturn(scopes)
      whenever(scopes.makeCurrent()).thenReturn(lifecycleToken)
      whenever(scopes.options).thenReturn(IchnoisCoreOptions())

      try {
        CheckInUtils.withCheckIn("monitor-1") { throw RuntimeException("thrown on purpose") }
        throw AssertionError("expected exception to be rethrown")
      } catch (e: Exception) {
        assertEquals("thrown on purpose", e.message)
      }

      inOrder(scopes, lifecycleToken) {
        verify(scopes).forkedScopes(any())
        verify(scopes).makeCurrent()
        verify(scopes).configureScope(any())
        verify(scopes)
          .captureCheckIn(
            check {
              assertEquals("monitor-1", it.monitorSlug)
              assertEquals(CheckInStatus.IN_PROGRESS.apiName(), it.status)
            }
          )
        verify(scopes)
          .captureCheckIn(
            check {
              assertEquals("monitor-1", it.monitorSlug)
              assertEquals(CheckInStatus.ERROR.apiName(), it.status)
            }
          )
        verify(lifecycleToken).close()
      }
    }
  }

  @Test
  fun `sends check-in for wrapped supplier with upsert`() {
    Mockito.mockStatic(IchnoisCore::class.java).use { ichnois ->
      val scopes = mock<IScopes>()
      val lifecycleToken = mock<IIchnoisCoreLifecycleToken>()
      ichnois.`when`<Any> { IchnoisCore.getCurrentScopes() }.thenReturn(scopes)
      ichnois.`when`<Any> { IchnoisCore.forkedScopes(any()) }.then { scopes.forkedScopes("test") }
      whenever(scopes.forkedScopes(any())).thenReturn(scopes)
      whenever(scopes.makeCurrent()).thenReturn(lifecycleToken)
      whenever(scopes.options).thenReturn(IchnoisCoreOptions())
      val monitorConfig = MonitorConfig(MonitorSchedule.interval(7, MonitorScheduleUnit.DAY))
      val returnValue = CheckInUtils.withCheckIn("monitor-1", monitorConfig) { "test1" }

      assertEquals("test1", returnValue)
      inOrder(scopes, lifecycleToken) {
        verify(scopes).forkedScopes(any())
        verify(scopes).makeCurrent()
        verify(scopes).configureScope(any())
        verify(scopes)
          .captureCheckIn(
            check {
              assertEquals("monitor-1", it.monitorSlug)
              assertSame(monitorConfig, it.monitorConfig)
              assertEquals(CheckInStatus.IN_PROGRESS.apiName(), it.status)
            }
          )
        verify(scopes)
          .captureCheckIn(
            check {
              assertEquals("monitor-1", it.monitorSlug)
              assertEquals(CheckInStatus.OK.apiName(), it.status)
            }
          )
        verify(lifecycleToken).close()
      }
    }
  }

  @Test
  fun `sends check-in for wrapped supplier with upsert and thresholds`() {
    Mockito.mockStatic(IchnoisCore::class.java).use { ichnois ->
      val scopes = mock<IScopes>()
      val lifecycleToken = mock<IIchnoisCoreLifecycleToken>()
      ichnois.`when`<Any> { IchnoisCore.getCurrentScopes() }.thenReturn(scopes)
      ichnois.`when`<Any> { IchnoisCore.forkedScopes(any()) }.then { scopes.forkedScopes("test") }
      whenever(scopes.forkedScopes(any())).thenReturn(scopes)
      whenever(scopes.makeCurrent()).thenReturn(lifecycleToken)
      whenever(scopes.options).thenReturn(IchnoisCoreOptions())
      val monitorConfig =
        MonitorConfig(MonitorSchedule.interval(7, MonitorScheduleUnit.DAY)).apply {
          failureIssueThreshold = 10
          recoveryThreshold = 20
        }
      val returnValue = CheckInUtils.withCheckIn("monitor-1", monitorConfig) { "test1" }

      assertEquals("test1", returnValue)
      inOrder(scopes, lifecycleToken) {
        verify(scopes).forkedScopes(any())
        verify(scopes).makeCurrent()
        verify(scopes).configureScope(any())
        verify(scopes)
          .captureCheckIn(
            check {
              assertEquals("monitor-1", it.monitorSlug)
              assertSame(monitorConfig, it.monitorConfig)
              assertEquals(CheckInStatus.IN_PROGRESS.apiName(), it.status)
            }
          )
        verify(scopes)
          .captureCheckIn(
            check {
              assertEquals("monitor-1", it.monitorSlug)
              assertEquals(CheckInStatus.OK.apiName(), it.status)
            }
          )
        verify(lifecycleToken).close()
      }
    }
  }

  @Test
  fun `sets defaults for MonitorConfig from IchnoisCoreOptions`() {
    Mockito.mockStatic(IchnoisCore::class.java).use { ichnois ->
      val scopes = mock<IScopes>()
      ichnois.`when`<Any> { IchnoisCore.getCurrentScopes() }.thenReturn(scopes)
      whenever(scopes.options)
        .thenReturn(
          IchnoisCoreOptions().apply {
            cron =
              IchnoisCoreOptions.Cron().apply {
                defaultCheckinMargin = 20
                defaultMaxRuntime = 30
                defaultTimezone = "America/New_York"
                defaultFailureIssueThreshold = 40
                defaultRecoveryThreshold = 50
              }
          }
        )

      val monitorConfig = MonitorConfig(MonitorSchedule.interval(7, MonitorScheduleUnit.DAY))

      assertEquals(20, monitorConfig.checkinMargin)
      assertEquals(30, monitorConfig.maxRuntime)
      assertEquals("America/New_York", monitorConfig.timezone)
      assertEquals(40, monitorConfig.failureIssueThreshold)
      assertEquals(50, monitorConfig.recoveryThreshold)
    }
  }

  @Test
  fun `defaults for MonitorConfig from IchnoisCoreOptions can be overridden`() {
    Mockito.mockStatic(IchnoisCore::class.java).use { ichnois ->
      val scopes = mock<IScopes>()
      ichnois.`when`<Any> { IchnoisCore.getCurrentScopes() }.thenReturn(scopes)
      whenever(scopes.options)
        .thenReturn(
          IchnoisCoreOptions().apply {
            cron =
              IchnoisCoreOptions.Cron().apply {
                defaultCheckinMargin = 20
                defaultMaxRuntime = 50
                defaultTimezone = "America/New_York"
              }
          }
        )

      val monitorConfig =
        MonitorConfig(MonitorSchedule.interval(7, MonitorScheduleUnit.DAY)).apply {
          checkinMargin = 10
          maxRuntime = 30
          timezone = "America/Los_Angeles"
        }

      assertEquals(10, monitorConfig.checkinMargin)
      assertEquals(30, monitorConfig.maxRuntime)
      assertEquals("America/Los_Angeles", monitorConfig.timezone)
    }
  }

  @Test
  fun `resolves a ticker that ticks on System nanoTime`() {
    // The clock the check-in path reads. Bracketing a tick between two System.nanoTime() readings
    // pins the source, not just the resolution: a ticker on any other monotonic source reports a
    // different epoch and would fall outside the bracket.
    val ticker = IchnoisCoreOptions().monotonicTicker

    val before = System.nanoTime()
    val tick = ticker.tickNanos()
    val after = System.nanoTime()

    assertThat(tick).isAtLeast(before)
    assertThat(tick).isAtMost(after)
  }

  @Test
  fun `reports the duration the ticker advanced by`() {
    val ticker = TestMonotonicTicker()

    val checkIns =
      captureCheckIns(ticker) {
        CheckInUtils.withCheckIn("monitor-1") { ticker.advance(1500, MILLISECONDS) }
      }

    assertThat(checkIns.map { it.status })
      .containsExactly(CheckInStatus.IN_PROGRESS.apiName(), CheckInStatus.OK.apiName())
      .inOrder()
    assertThat(checkIns.first().duration).isNull()
    assertThat(checkIns.last().duration).isEqualTo(1.5)
  }

  @Test
  fun `reports the duration when the callable throws`() {
    val ticker = TestMonotonicTicker()

    val checkIns =
      captureCheckIns(ticker) {
        assertFailsWith<RuntimeException> {
          CheckInUtils.withCheckIn("monitor-1") {
            ticker.advance(2, SECONDS)
            throw RuntimeException("thrown on purpose")
          }
        }
      }

    assertThat(checkIns.last().status).isEqualTo(CheckInStatus.ERROR.apiName())
    assertThat(checkIns.last().duration).isEqualTo(2.0)
  }

  /**
   * Runs [block] against scopes whose options tick on [ticker], and returns every check-in it
   * captured, in order.
   */
  private fun captureCheckIns(ticker: MonotonicTicker, block: () -> Unit): List<CheckIn> {
    Mockito.mockStatic(IchnoisCore::class.java).use { ichnois ->
      val scopes = mock<IScopes>()
      val options =
        object : IchnoisCoreOptions() {
          override fun getMonotonicTicker(): MonotonicTicker = ticker
        }
      ichnois.`when`<Any> { IchnoisCore.getCurrentScopes() }.thenReturn(scopes)
      ichnois.`when`<Any> { IchnoisCore.forkedScopes(any()) }.then { scopes.forkedScopes("test") }
      whenever(scopes.forkedScopes(any())).thenReturn(scopes)
      whenever(scopes.makeCurrent()).thenReturn(mock<IIchnoisCoreLifecycleToken>())
      whenever(scopes.options).thenReturn(options)

      block()

      val captor = argumentCaptor<CheckIn>()
      verify(scopes, times(2)).captureCheckIn(captor.capture())
      return captor.allValues
    }
  }
}
