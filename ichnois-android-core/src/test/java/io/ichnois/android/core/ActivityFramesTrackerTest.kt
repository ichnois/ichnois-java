package io.ichnois.android.core

import android.app.Activity
import android.util.SparseIntArray
import androidx.core.app.FrameMetricsAggregator
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ichnois.ILogger
import io.ichnois.IchnoisCoreOptions
import io.ichnois.protocol.MeasurementValue
import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.util.LazyEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ActivityFramesTrackerTest {
  private class Fixture {
    val aggregator = mock<FrameMetricsAggregator>()
    val activity = mock<Activity>()
    val ichnoisId = IchnoisCoreId()
    val loadClass = mock<LoadClass>()
    val handler = mock<MainLooperHandler>()
    val options = IchnoisCoreAndroidOptions()

    init {
      // ActivityFramesTracker is used only if performanceV2 is disabled
      options.isEnablePerformanceV2 = false
    }

    fun getSut(isAndroidxAvailable: Boolean = true): ActivityFramesTracker {
      whenever(loadClass.isClassAvailableLazy(any(), any<ILogger>()))
        .thenReturn(LazyEvaluator { isAndroidxAvailable })
      whenever(loadClass.isClassAvailableLazy(any(), any<IchnoisCoreOptions>()))
        .thenReturn(LazyEvaluator { isAndroidxAvailable })

      return ActivityFramesTracker(loadClass, options, handler, aggregator)
    }
  }

  private val fixture = Fixture()

  @Test
  fun `sets total frames`() {
    val sut = fixture.getSut()
    val array = getArray()

    whenever(fixture.aggregator.metrics).thenReturn(emptyArray(), array)

    sut.addActivity(fixture.activity)
    sut.setMetrics(fixture.activity, fixture.ichnoisId)

    val metrics = sut.takeMetrics(fixture.ichnoisId)
    val totalFrames = metrics!![MeasurementValue.KEY_FRAMES_TOTAL]

    assertEquals(totalFrames!!.value, 1)
    assertEquals(totalFrames.unit, "none")
  }

  @Test
  fun `sets frozen frames`() {
    val sut = fixture.getSut()
    val arrayAtStart = getArray(frameTime = 701, numFrames = 5)
    val arrayAtEnd = getArray(frameTime = 701, numFrames = 10)

    whenever(fixture.aggregator.metrics).thenReturn(arrayAtStart, arrayAtEnd)

    sut.addActivity(fixture.activity)
    sut.setMetrics(fixture.activity, fixture.ichnoisId)

    val metrics = sut.takeMetrics(fixture.ichnoisId)
    val frozenFrames = metrics!![MeasurementValue.KEY_FRAMES_FROZEN]

    assertEquals(frozenFrames!!.value, 5)
    assertEquals(frozenFrames.unit, "none")
  }

  @Test
  fun `sets slow frames`() {
    val sut = fixture.getSut()
    val arrayAtStart = getArray(frameTime = 20, numFrames = 5)
    val arrayAtEnd = getArray(frameTime = 20, numFrames = 10)

    whenever(fixture.aggregator.metrics).thenReturn(arrayAtStart, arrayAtEnd)

    sut.addActivity(fixture.activity)
    sut.setMetrics(fixture.activity, fixture.ichnoisId)

    val metrics = sut.takeMetrics(fixture.ichnoisId)
    val slowFrames = metrics!![MeasurementValue.KEY_FRAMES_SLOW]

    assertEquals(slowFrames!!.value, 5)
    assertEquals(slowFrames.unit, "none")
  }

  @Test
  fun `sets slow and frozen frames`() {
    val sut = fixture.getSut()
    val array =
      SparseIntArray()
        .also {
          it.put(16, 100)
          it.put(20, 5)
          it.put(701, 6)
        }
        .let { arrayOf(it) }

    whenever(fixture.aggregator.metrics).thenReturn(emptyArray(), array)

    sut.addActivity(fixture.activity)
    sut.setMetrics(fixture.activity, fixture.ichnoisId)

    val metrics = sut.takeMetrics(fixture.ichnoisId)

    val totalFrames = metrics!![MeasurementValue.KEY_FRAMES_TOTAL]
    assertEquals(totalFrames!!.value, 111)

    val frozenFrames = metrics[MeasurementValue.KEY_FRAMES_FROZEN]
    assertEquals(frozenFrames!!.value, 6)

    val slowFrames = metrics[MeasurementValue.KEY_FRAMES_SLOW]
    assertEquals(slowFrames!!.value, 5)
  }

  @Test
  fun `sets slow and frozen frames even if start was null`() {
    val sut = fixture.getSut()
    val array =
      SparseIntArray()
        .also {
          it.put(16, 100)
          it.put(20, 5)
          it.put(701, 6)
        }
        .let { arrayOf(it) }

    whenever(fixture.aggregator.metrics).thenReturn(null, array)

    sut.addActivity(fixture.activity)
    sut.setMetrics(fixture.activity, fixture.ichnoisId)

    val metrics = sut.takeMetrics(fixture.ichnoisId)

    val totalFrames = metrics!![MeasurementValue.KEY_FRAMES_TOTAL]
    assertEquals(totalFrames!!.value, 111)

    val frozenFrames = metrics[MeasurementValue.KEY_FRAMES_FROZEN]
    assertEquals(frozenFrames!!.value, 6)

    val slowFrames = metrics[MeasurementValue.KEY_FRAMES_SLOW]
    assertEquals(slowFrames!!.value, 5)
  }

  @Test
  fun `different activities have separate counts - even when called out of order`() {
    val sut = fixture.getSut()
    val activityAStartFrameCounts =
      SparseIntArray()
        .also {
          it.put(16, 100)
          it.put(17, 3)
          it.put(700, 2)
          it.put(701, 6)
        }
        .let { arrayOf(it) }
    val activityBStartFrameCounts =
      SparseIntArray()
        .also {
          it.put(16, 110)
          it.put(17, 3)
          it.put(700, 3)
          it.put(701, 7)
        }
        .let { arrayOf(it) }
    val activityAEndFrameCounts =
      SparseIntArray()
        .also {
          it.put(16, 115)
          it.put(17, 3)
          it.put(700, 5)
          it.put(701, 9)
        }
        .let { arrayOf(it) }
    val activityBEndFrameCounts =
      SparseIntArray()
        .also {
          it.put(16, 135)
          it.put(17, 3)
          it.put(700, 8)
          it.put(701, 12)
        }
        .let { arrayOf(it) }

    val activityA = fixture.activity
    val activityB = mock<Activity>()
    val ichnoisIdA = fixture.ichnoisId
    val ichnoisIdB = IchnoisCoreId()

    whenever(fixture.aggregator.metrics)
      .thenReturn(
        activityAStartFrameCounts,
        activityBStartFrameCounts,
        activityAEndFrameCounts,
        activityBEndFrameCounts,
      )

    sut.addActivity(activityA)
    sut.addActivity(activityB)
    sut.setMetrics(activityA, ichnoisIdA)
    sut.setMetrics(activityB, ichnoisIdB)

    val metricsA = sut.takeMetrics(ichnoisIdA)!!
    val metricsB = sut.takeMetrics(ichnoisIdB)!!

    val totalFramesA = metricsA!![MeasurementValue.KEY_FRAMES_TOTAL]
    assertEquals(totalFramesA!!.value, 21) // 15 + 3 + 3 (diff counts for activityA)

    val frozenFramesA = metricsA[MeasurementValue.KEY_FRAMES_FROZEN]
    assertEquals(frozenFramesA!!.value, 3)

    val slowFramesA = metricsA[MeasurementValue.KEY_FRAMES_SLOW]
    assertEquals(slowFramesA!!.value, 3)

    val totalFramesB = metricsB!![MeasurementValue.KEY_FRAMES_TOTAL]
    assertEquals(totalFramesB!!.value, 35) // 25 + 5 + 5 (diff counts for activityB)

    val frozenFramesB = metricsB[MeasurementValue.KEY_FRAMES_FROZEN]
    assertEquals(frozenFramesB!!.value, 5)

    val slowFramesB = metricsB[MeasurementValue.KEY_FRAMES_SLOW]
    assertEquals(slowFramesB!!.value, 5)
  }

  @Test
  fun `same activity can be used again later on`() {
    val sut = fixture.getSut()
    val firstLaunchStartFrameCounts =
      SparseIntArray()
        .also {
          it.put(16, 100)
          it.put(20, 5)
          it.put(701, 6)
        }
        .let { arrayOf(it) }
    val firstLaunchEndFrameCounts =
      SparseIntArray()
        .also {
          it.put(16, 110)
          it.put(20, 6)
          it.put(701, 7)
        }
        .let { arrayOf(it) }
    val secondLaunchStartFrameCounts =
      SparseIntArray()
        .also {
          it.put(16, 115)
          it.put(20, 8)
          it.put(701, 9)
        }
        .let { arrayOf(it) }
    val secondLaunchEndFrameCounts =
      SparseIntArray()
        .also {
          it.put(16, 135)
          it.put(20, 11)
          it.put(701, 12)
        }
        .let { arrayOf(it) }
    val secondIchnoisCoreId = IchnoisCoreId()

    whenever(fixture.aggregator.metrics)
      .thenReturn(
        firstLaunchStartFrameCounts,
        firstLaunchEndFrameCounts,
        secondLaunchStartFrameCounts,
        secondLaunchEndFrameCounts,
      )

    sut.addActivity(fixture.activity)
    sut.setMetrics(fixture.activity, fixture.ichnoisId)
    sut.addActivity(fixture.activity)
    sut.setMetrics(fixture.activity, secondIchnoisCoreId)

    val metrics = sut.takeMetrics(fixture.ichnoisId)
    val secondMetrics = sut.takeMetrics(secondIchnoisCoreId)

    val totalFrames = metrics!![MeasurementValue.KEY_FRAMES_TOTAL]
    assertEquals(totalFrames!!.value, 12) // 10 + 1 + 1 (diff counts for first invocation)

    val frozenFrames = metrics[MeasurementValue.KEY_FRAMES_FROZEN]
    assertEquals(frozenFrames!!.value, 1)

    val slowFrames = metrics[MeasurementValue.KEY_FRAMES_SLOW]
    assertEquals(slowFrames!!.value, 1)

    val totalFramesSecond = secondMetrics!![MeasurementValue.KEY_FRAMES_TOTAL]
    assertEquals(totalFramesSecond!!.value, 26) // 20 + 3 + 3 (diff counts for second invocation)

    val frozenFramesSecond = secondMetrics[MeasurementValue.KEY_FRAMES_FROZEN]
    assertEquals(frozenFramesSecond!!.value, 3)

    val slowFramesSecond = secondMetrics[MeasurementValue.KEY_FRAMES_SLOW]
    assertEquals(slowFramesSecond!!.value, 3)
  }

  @Test
  fun `do not set metrics if values are zeroes`() {
    val sut = fixture.getSut()
    val arrayAll = SparseIntArray()
    arrayAll.put(0, 0)
    val array = arrayOf(arrayAll)

    whenever(fixture.aggregator.metrics).thenReturn(array)

    sut.addActivity(fixture.activity)
    sut.setMetrics(fixture.activity, fixture.ichnoisId)

    val metrics = sut.takeMetrics(fixture.ichnoisId)

    assertNull(metrics)
  }

  @Test
  fun `do not set metrics if values are null`() {
    val sut = fixture.getSut()

    whenever(fixture.aggregator.metrics).thenReturn(null)

    sut.addActivity(fixture.activity)
    sut.setMetrics(fixture.activity, fixture.ichnoisId)

    val metrics = sut.takeMetrics(fixture.ichnoisId)

    assertNull(metrics)
  }

  @Test
  fun `addActivity does not throw if no AndroidX`() {
    val sut = fixture.getSut(false)

    sut.addActivity(fixture.activity)
  }

  @Test
  fun `setMetrics does not throw if no AndroidX`() {
    val sut = fixture.getSut(false)

    sut.setMetrics(fixture.activity, fixture.ichnoisId)
  }

  @Test
  fun `addActivity and setMetrics combined do not throw if no AndroidX`() {
    val sut = fixture.getSut(false)

    sut.addActivity(fixture.activity)
    sut.setMetrics(fixture.activity, fixture.ichnoisId)
  }

  @Test
  fun `setMetrics does not throw if Activity is not added`() {
    whenever(fixture.aggregator.metrics).thenThrow(IllegalArgumentException())
    val sut = fixture.getSut()

    sut.setMetrics(fixture.activity, fixture.ichnoisId)
  }

  @Test
  fun `stop does not throw if no AndroidX`() {
    val sut = fixture.getSut(false)

    sut.stop()
  }

  @Test
  fun `stop resets frameMetricsAggregator`() {
    val sut = fixture.getSut()

    sut.stop()

    verify(fixture.aggregator).reset()
  }

  @Test
  fun `takeMetrics returns null if no AndroidX`() {
    val sut = fixture.getSut(false)

    whenever(fixture.aggregator.metrics).thenReturn(emptyArray(), getArray())

    sut.addActivity(fixture.activity)
    sut.setMetrics(fixture.activity, fixture.ichnoisId)

    assertNull(sut.takeMetrics(fixture.ichnoisId))
  }

  @Test
  fun `addActivity call to FrameMetricsTracker is done on the main thread, even when being called from a background thread`() {
    val sut = fixture.getSut()

    val addThread = Thread { sut.addActivity(fixture.activity) }
    addThread.start()
    addThread.join(500)
    verify(fixture.handler).post(any())
  }

  @Test
  fun `setMetrics call to FrameMetricsTracker is done on the main thread, even when being called from a background thread`() {
    val sut = fixture.getSut()

    val setMetricsThread = Thread { sut.setMetrics(fixture.activity, fixture.ichnoisId) }
    setMetricsThread.start()
    setMetricsThread.join(500)
    verify(fixture.handler).post(any())
  }

  @Test
  fun `stop call to FrameMetricsTracker is done on the main thread, even when being called from a background thread`() {
    val sut = fixture.getSut()

    val stopThread = Thread { sut.stop() }
    stopThread.start()
    stopThread.join(500)
    verify(fixture.handler).post(any())
  }

  @Test
  fun `when perf-2 is enabled, activity frame metrics tracker is disabled`() {
    fixture.options.isEnablePerformanceV2 = true
    val sut = fixture.getSut()
    assertFalse(sut.isFrameMetricsAggregatorAvailable)
  }

  @Test
  fun `when perf-2 is disabled, activity frame metrics tracker is enabled`() {
    fixture.options.isEnablePerformanceV2 = false
    val sut = fixture.getSut()
    assertTrue(sut.isFrameMetricsAggregatorAvailable)
  }

  private fun getArray(frameTime: Int = 1, numFrames: Int = 1): Array<SparseIntArray?> {
    val totalArray = SparseIntArray()
    totalArray.put(frameTime, numFrames)
    return arrayOf(totalArray)
  }
}
