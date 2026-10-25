package io.ichnois.android.core.performance

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ichnois.IScopes
import io.ichnois.ISpan
import io.ichnois.IchnoisCoreNanotimeDate
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreTracer
import io.ichnois.Span
import io.ichnois.SpanDataConvention
import io.ichnois.SpanOptions
import io.ichnois.TracesSamplingDecision
import io.ichnois.TransactionContext
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class ActivityLifecycleSpanHelperTest {
  private class Fixture {
    val appStartSpan: ISpan
    val scopes = mock<IScopes>()
    val options = IchnoisCoreOptions()
    val date = IchnoisCoreNanotimeDate(1, 1000000)
    val endDate = IchnoisCoreNanotimeDate(3, 3000000)

    init {
      whenever(scopes.options).thenReturn(options)
      appStartSpan =
        Span(
          TransactionContext("name", "op", TracesSamplingDecision(true)),
          IchnoisCoreTracer(TransactionContext("name", "op", TracesSamplingDecision(true)), scopes),
          scopes,
          SpanOptions(),
        )
    }

    fun getSut(activityName: String = "ActivityName"): ActivityLifecycleSpanHelper =
      ActivityLifecycleSpanHelper(activityName)
  }

  private val fixture = Fixture()

  @BeforeTest
  fun setup() {
    AppStartMetrics.getInstance().clear()
  }

  @Test
  fun `createAndStopOnCreateSpan creates and finishes onCreate span`() {
    val helper = fixture.getSut()
    val date = IchnoisCoreNanotimeDate(1, 1)
    helper.setOnCreateStartTimestamp(date)
    helper.createAndStopOnCreateSpan(fixture.appStartSpan)

    val onCreateSpan = helper.onCreateSpan
    assertNotNull(onCreateSpan)
    assertTrue(onCreateSpan.isFinished)

    assertEquals("activity.load", onCreateSpan.operation)
    assertEquals("ActivityName.onCreate", onCreateSpan.description)
    assertEquals(date.nanoTimestamp(), onCreateSpan.startDate.nanoTimestamp())
    assertEquals(date.nanoTimestamp(), onCreateSpan.startDate.nanoTimestamp())

    assertEquals(
      Looper.getMainLooper().thread.id,
      onCreateSpan.getData(SpanDataConvention.THREAD_ID),
    )
    assertEquals("main", onCreateSpan.getData(SpanDataConvention.THREAD_NAME))
    assertEquals(true, onCreateSpan.getData(SpanDataConvention.CONTRIBUTES_TTID))
    assertEquals(true, onCreateSpan.getData(SpanDataConvention.CONTRIBUTES_TTFD))
  }

  @Test
  fun `createAndStopOnCreateSpan does nothing if no onCreate start timestamp is available`() {
    val helper = fixture.getSut()
    helper.createAndStopOnCreateSpan(fixture.appStartSpan)
    assertNull(helper.onCreateSpan)
  }

  @Test
  fun `createAndStopOnCreateSpan does nothing if passed appStartSpan is null`() {
    val helper = fixture.getSut()
    helper.setOnCreateStartTimestamp(IchnoisCoreNanotimeDate())
    helper.createAndStopOnCreateSpan(null)
    assertNull(helper.onCreateSpan)
  }

  @Test
  fun `createAndStopOnStartSpan creates and finishes onStart span`() {
    val helper = fixture.getSut()
    val date = IchnoisCoreNanotimeDate(1, 1)
    helper.setOnStartStartTimestamp(date)
    helper.createAndStopOnStartSpan(fixture.appStartSpan)

    val onStartSpan = helper.onStartSpan
    assertNotNull(onStartSpan)
    assertTrue(onStartSpan.isFinished)

    assertEquals("activity.load", onStartSpan.operation)
    assertEquals("ActivityName.onStart", onStartSpan.description)
    assertEquals(date.nanoTimestamp(), onStartSpan.startDate.nanoTimestamp())
    assertEquals(date.nanoTimestamp(), onStartSpan.startDate.nanoTimestamp())

    assertEquals(
      Looper.getMainLooper().thread.id,
      onStartSpan.getData(SpanDataConvention.THREAD_ID),
    )
    assertEquals("main", onStartSpan.getData(SpanDataConvention.THREAD_NAME))
    assertEquals(true, onStartSpan.getData(SpanDataConvention.CONTRIBUTES_TTID))
    assertEquals(true, onStartSpan.getData(SpanDataConvention.CONTRIBUTES_TTFD))
  }

  @Test
  fun `createAndStopOnStartSpan does nothing if no onStart start timestamp is available`() {
    val helper = fixture.getSut()
    helper.createAndStopOnStartSpan(fixture.appStartSpan)
    assertNull(helper.onStartSpan)
  }

  @Test
  fun `createAndStopOnStartSpan does nothing if passed appStartSpan is null`() {
    val helper = fixture.getSut()
    helper.setOnStartStartTimestamp(IchnoisCoreNanotimeDate())
    helper.createAndStopOnStartSpan(null)
    assertNull(helper.onStartSpan)
  }

  @Test
  fun `saveSpanToAppStartMetrics does nothing if onCreate span is null`() {
    val helper = fixture.getSut()
    helper.setOnCreateStartTimestamp(fixture.date)
    helper.setOnStartStartTimestamp(fixture.date)
    helper.createAndStopOnStartSpan(fixture.appStartSpan)
    assertNull(helper.onCreateSpan)
    assertNotNull(helper.onStartSpan)
  }

  @Test
  fun `saveSpanToAppStartMetrics does nothing if onStart span is null`() {
    val helper = fixture.getSut()
    helper.setOnCreateStartTimestamp(fixture.date)
    helper.createAndStopOnCreateSpan(fixture.appStartSpan)
    helper.setOnStartStartTimestamp(fixture.date)
    assertNotNull(helper.onCreateSpan)
    assertNull(helper.onStartSpan)
  }

  @Test
  fun `saveSpanToAppStartMetrics saves spans to AppStartMetrics`() {
    val helper = fixture.getSut()
    helper.setOnCreateStartTimestamp(fixture.date)
    helper.createAndStopOnCreateSpan(fixture.appStartSpan)
    helper.onCreateSpan!!.updateEndDate(fixture.endDate)
    helper.setOnStartStartTimestamp(fixture.date)
    helper.createAndStopOnStartSpan(fixture.appStartSpan)
    helper.onStartSpan!!.updateEndDate(fixture.endDate)
    assertNotNull(helper.onCreateSpan)
    assertNotNull(helper.onStartSpan)

    val appStartMetrics = AppStartMetrics.getInstance()
    assertTrue(appStartMetrics.activityLifecycleTimeSpans.isEmpty())

    // Save spans to AppStartMetrics
    helper.saveSpanToAppStartMetrics()
    assertFalse(appStartMetrics.activityLifecycleTimeSpans.isEmpty())
    val onCreate = appStartMetrics.activityLifecycleTimeSpans.first().onCreate
    val onStart = appStartMetrics.activityLifecycleTimeSpans.first().onStart

    // Check onCreate TimeSpan has same values as helper.onCreateSpan
    assertNotNull(onCreate)
    assertEquals(
      helper.onCreateSpan!!.startDate.nanoTimestamp(),
      onCreate.startTimestamp!!.nanoTimestamp(),
    )
    val spanOnCreateDurationNanos =
      helper.onCreateSpan!!.finishDate!!.diff(helper.onCreateSpan!!.startDate)
    assertEquals(onCreate.durationMs, TimeUnit.NANOSECONDS.toMillis(spanOnCreateDurationNanos))
    assertEquals(onCreate.description, helper.onCreateSpan!!.description)

    // Check onStart TimeSpan has same values as helper.onStartSpan
    assertNotNull(onStart)
    assertEquals(
      helper.onStartSpan!!.startDate.nanoTimestamp(),
      onStart.startTimestamp!!.nanoTimestamp(),
    )
    val spanOnStartDurationNanos =
      helper.onStartSpan!!.finishDate!!.diff(helper.onStartSpan!!.startDate)
    assertEquals(onStart.durationMs, TimeUnit.NANOSECONDS.toMillis(spanOnStartDurationNanos))
    assertEquals(onStart.description, helper.onStartSpan!!.description)
  }
}
