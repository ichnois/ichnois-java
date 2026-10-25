package io.ichnois.sqlite

import io.ichnois.IScopes
import io.ichnois.ISpan
import io.ichnois.IchnoisCoreDateProvider
import io.ichnois.IchnoisCoreLongDate
import io.ichnois.IchnoisCoreNanotimeDate
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreTracer
import io.ichnois.SpanDataConvention
import io.ichnois.SpanStatus
import io.ichnois.TransactionContext
import io.ichnois.util.thread.IThreadChecker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DriverSpansTest {

  private class Fixture {

    val scopes = mock<IScopes>()
    lateinit var ichnoisTracer: IchnoisCoreTracer
    lateinit var options: IchnoisCoreOptions

    fun getSut(isTransactionActive: Boolean = true, fileName: String = ":memory:"): DriverSpans {
      options = IchnoisCoreOptions().apply { dsn = "https://key@ichnois-h2jw.onrender.com/proj" }
      whenever(scopes.options).thenReturn(options)
      ichnoisTracer = IchnoisCoreTracer(TransactionContext("name", "op"), scopes)
      if (isTransactionActive) {
        whenever(scopes.span).thenReturn(ichnoisTracer)
      }
      return DriverSpans.fromFileName(fileName, scopes)
    }
  }

  private val fixture = Fixture()

  @Test
  fun `startTimestamp is ns-precise and skips date provider when parent uses IchnoisCoreNanotimeDate`() {
    // Only the parent date is queued. If startTimestamp() were to call dateProvider.now(),
    // the queue would underflow and the test would fail loudly — this is what verifies the
    // optimization is in effect.
    val parentDate = IchnoisCoreNanotimeDate(1_000_000L, 100_000_000L)
    val sut = setUpWithNanotimeDates(parentDate)

    val start = sut.startTimestamp()

    val durationNanos = 42_000_000L
    sut.record("SELECT 1", start, durationNanos, SpanStatus.OK)

    val span = fixture.ichnoisTracer.children.first()

    // startTimestamp returns an already-ns-precise value, anchored to the parent's wall clock and
    // offset by elapsed System.nanoTime(). The exact ns-math is unit-tested in
    // ChildStartTimestampOrNullTest; here we verify the integration shape.
    assertIs<IchnoisCoreLongDate>(span.startDate)
    assertEquals(start, span.startDate.nanoTimestamp())
    assertEquals(start + durationNanos, span.finishDate!!.nanoTimestamp())
  }

  @Test
  fun `startTimestamp falls back to date provider when parent does not use IchnoisCoreNanotimeDate`() {
    val providerDate = IchnoisCoreNanotimeDate(2_000_000L, 200_000_000L)
    val parentSpan = mock<ISpan>()
    whenever(parentSpan.startDate).thenReturn(IchnoisCoreLongDate(1_000_000_000_000_000L))
    val options =
      IchnoisCoreOptions().apply {
        dsn = "https://key@ichnois-h2jw.onrender.com/proj"
        dateProvider = IchnoisCoreDateProvider { providerDate }
      }
    whenever(fixture.scopes.options).thenReturn(options)
    whenever(fixture.scopes.span).thenReturn(parentSpan)

    val sut = DriverSpans.fromFileName(":memory:", fixture.scopes)

    assertEquals(providerDate.nanoTimestamp(), sut.startTimestamp())
  }

  @Test
  fun `startTimestamp falls back to date provider when no transaction is active`() {
    val providerDate = IchnoisCoreNanotimeDate(2_000_000L, 200_000_000L)
    val options =
      IchnoisCoreOptions().apply {
        dsn = "https://key@ichnois-h2jw.onrender.com/proj"
        dateProvider = IchnoisCoreDateProvider { providerDate }
      }
    whenever(fixture.scopes.options).thenReturn(options)
    whenever(fixture.scopes.span).thenReturn(null)

    val sut = DriverSpans.fromFileName(":memory:", fixture.scopes)

    assertEquals(providerDate.nanoTimestamp(), sut.startTimestamp())
  }

  @Test
  fun `record method records a span if a transaction is active`() {
    val sut = fixture.getSut(isTransactionActive = true)
    sut.record("SELECT 1", sut.startTimestamp(), 1_000_000, SpanStatus.OK)
    assertEquals(1, fixture.ichnoisTracer.children.size)
  }

  @Test
  fun `record method does not record a span if no transaction is active`() {
    val sut = fixture.getSut(isTransactionActive = false)
    val start = sut.startTimestamp()
    sut.record("SELECT 1", start, 1_000_000, SpanStatus.OK)
    assertEquals(0, fixture.ichnoisTracer.children.size)
  }

  @Test
  fun `record method creates a span with correct properties`() {
    val sut = fixture.getSut()
    val start = sut.startTimestamp()
    sut.record("SELECT * FROM users", start, 1_000_000, SpanStatus.OK)

    val span = fixture.ichnoisTracer.children.firstOrNull()
    assertNotNull(span)
    assertEquals("db.sql.query", span.operation)
    assertEquals("SELECT * FROM users", span.description)
    assertEquals("auto.db.sqlite", span.spanContext.origin)
    assertEquals(SpanStatus.OK, span.status)
    assertTrue(span.isFinished)
  }

  @Test
  fun `record method sets finishDate equal to startDate + durationNanos`() {
    val sut = fixture.getSut()
    val start = sut.startTimestamp()
    val durationNanos = 42_000_000L

    sut.record("SELECT 1", start, durationNanos, SpanStatus.OK)

    val span = fixture.ichnoisTracer.children.first()
    assertEquals(span.startDate.nanoTimestamp() + durationNanos, span.finishDate!!.nanoTimestamp())
  }

  @Test
  fun `record method attaches throwable when provided`() {
    val sut = fixture.getSut()
    val start = sut.startTimestamp()
    val exception = RuntimeException("disk I/O error")

    sut.record("INSERT INTO t VALUES(1)", start, 500_000, SpanStatus.INTERNAL_ERROR, exception)

    val span = fixture.ichnoisTracer.children.first()
    assertEquals(SpanStatus.INTERNAL_ERROR, span.status)
    assertEquals(exception, span.throwable)
  }

  @Test
  fun `record method sets db system and db name when fileName is not the in-memory sentinel`() {
    val sut = fixture.getSut(fileName = "/data/data/com.example/databases/tracks.db")
    val start = sut.startTimestamp()
    sut.record("SELECT 1", start, 1_000_000, SpanStatus.OK)

    val span = fixture.ichnoisTracer.children.first()
    assertEquals("sqlite", span.data[SpanDataConvention.DB_SYSTEM_KEY])
    assertEquals("tracks.db", span.data[SpanDataConvention.DB_NAME_KEY])
  }

  @Test
  fun `record method sets db system only when fileName is the in-memory sentinel`() {
    val sut = fixture.getSut(fileName = ":memory:")
    val start = sut.startTimestamp()
    sut.record("SELECT 1", start, 1_000_000, SpanStatus.OK)

    val span = fixture.ichnoisTracer.children.first()
    assertEquals("in-memory", span.data[SpanDataConvention.DB_SYSTEM_KEY])
    assertNull(span.data[SpanDataConvention.DB_NAME_KEY])
  }

  @Test
  fun `record method sets blocked_main_thread to true and attaches call stack on main thread`() {
    val sut = fixture.getSut()
    fixture.options.threadChecker = mock<IThreadChecker>()
    whenever(fixture.options.threadChecker.isMainThread).thenReturn(true)
    whenever(fixture.options.threadChecker.currentThreadName).thenReturn("main")

    sut.record("SELECT 1", sut.startTimestamp(), 1_000_000, SpanStatus.OK)

    val span = fixture.ichnoisTracer.children.first()
    assertTrue(span.getData(SpanDataConvention.BLOCKED_MAIN_THREAD_KEY) as Boolean)
    assertNotNull(span.getData(SpanDataConvention.CALL_STACK_KEY))
  }

  @Test
  fun `record method sets blocked_main_thread to false and does not attach a call stack on background thread`() {
    val sut = fixture.getSut()
    fixture.options.threadChecker = mock<IThreadChecker>()
    whenever(fixture.options.threadChecker.isMainThread).thenReturn(false)
    whenever(fixture.options.threadChecker.currentThreadName).thenReturn("worker")

    sut.record("SELECT 1", sut.startTimestamp(), 1_000_000, SpanStatus.OK)

    val span = fixture.ichnoisTracer.children.first()
    assertFalse(span.getData(SpanDataConvention.BLOCKED_MAIN_THREAD_KEY) as Boolean)
    assertNull(span.getData(SpanDataConvention.CALL_STACK_KEY))
  }

  private fun setUpWithNanotimeDates(vararg dates: IchnoisCoreNanotimeDate): DriverSpans {
    val dateQueue = ArrayDeque(dates.toList())
    val options =
      IchnoisCoreOptions().apply {
        dsn = "https://key@ichnois-h2jw.onrender.com/proj"
        dateProvider = IchnoisCoreDateProvider { dateQueue.removeFirst() }
      }
    whenever(fixture.scopes.options).thenReturn(options)
    fixture.ichnoisTracer = IchnoisCoreTracer(TransactionContext("name", "op"), fixture.scopes)
    whenever(fixture.scopes.span).thenReturn(fixture.ichnoisTracer)
    return DriverSpans.fromFileName(":memory:", fixture.scopes)
  }
}
