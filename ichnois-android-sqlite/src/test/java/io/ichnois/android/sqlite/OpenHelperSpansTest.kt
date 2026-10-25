package io.ichnois.android.sqlite

import android.database.CrossProcessCursor
import android.database.SQLException
import io.ichnois.IScopes
import io.ichnois.IchnoisCoreIntegrationPackageStorage
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
import org.junit.Before
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OpenHelperSpansTest {
  private class Fixture {
    private val scopes = mock<IScopes>()
    lateinit var ichnoisTracer: IchnoisCoreTracer
    lateinit var options: IchnoisCoreOptions

    fun getSut(isSpanActive: Boolean = true, databaseName: String? = null): OpenHelperSpans {
      options = IchnoisCoreOptions().apply { dsn = "https://key@ichnois-h2jw.onrender.com/proj" }
      whenever(scopes.options).thenReturn(options)
      ichnoisTracer = IchnoisCoreTracer(TransactionContext("name", "op"), scopes)

      if (isSpanActive) {
        whenever(scopes.span).thenReturn(ichnoisTracer)
      }
      return OpenHelperSpans(scopes, databaseName)
    }
  }

  private val fixture = Fixture()

  @Before
  fun setup() {
    IchnoisCoreIntegrationPackageStorage.getInstance().clearStorage()
  }

  @Test
  fun `add SQLite to the list of integrations`() {
    assertFalse(IchnoisCoreIntegrationPackageStorage.getInstance().integrations.contains("SQLite"))
    fixture.getSut()
    assertTrue(IchnoisCoreIntegrationPackageStorage.getInstance().integrations.contains("SQLite"))
  }

  @Test
  fun `performSql creates a span if a span is running`() {
    val sut = fixture.getSut()
    sut.performSql("sql") {}
    val span = fixture.ichnoisTracer.children.firstOrNull()
    assertNotNull(span)
    assertEquals("db.sql.query", span.operation)
    assertEquals("auto.db.sqlite", span.spanContext.origin)
    assertEquals("sql", span.description)
    assertEquals(SpanStatus.OK, span.status)
    assertTrue(span.isFinished)
  }

  @Test
  fun `performSql does not create a span if no span is running`() {
    val sut = fixture.getSut(isSpanActive = false)
    sut.performSql("sql") {}
    assertEquals(0, fixture.ichnoisTracer.children.size)
  }

  @Test
  fun `performSql creates a span with error status if the operation throws`() {
    val sut = fixture.getSut()
    val e = SQLException()
    try {
      sut.performSql("error sql") { throw e }
    } catch (_: Throwable) {}
    val span = fixture.ichnoisTracer.children.firstOrNull()
    assertNotNull(span)
    assertEquals("db.sql.query", span.operation)
    assertEquals("error sql", span.description)
    assertEquals(SpanStatus.INTERNAL_ERROR, span.status)
    assertEquals(e, span.throwable)
    assertTrue(span.isFinished)
  }

  @Test
  fun `when performSql runs in background blocked_main_thread is false and no stack trace is attached`() {
    val sut = fixture.getSut()

    fixture.options.threadChecker = mock<IThreadChecker>()
    whenever(fixture.options.threadChecker.isMainThread).thenReturn(false)
    whenever(fixture.options.threadChecker.currentThreadName).thenReturn("test")

    sut.performSql("sql") {}
    val span = fixture.ichnoisTracer.children.first()

    assertFalse(span.getData(SpanDataConvention.BLOCKED_MAIN_THREAD_KEY) as Boolean)
    assertNull(span.getData(SpanDataConvention.CALL_STACK_KEY))
  }

  @Test
  fun `when performSql runs in foreground blocked_main_thread is true and a stack trace is attached`() {
    val sut = fixture.getSut()

    fixture.options.threadChecker = mock<IThreadChecker>()
    whenever(fixture.options.threadChecker.isMainThread).thenReturn(true)
    whenever(fixture.options.threadChecker.currentThreadName).thenReturn("test")

    sut.performSql("sql") {}
    val span = fixture.ichnoisTracer.children.first()

    assertTrue(span.getData(SpanDataConvention.BLOCKED_MAIN_THREAD_KEY) as Boolean)
    assertNotNull(span.getData(SpanDataConvention.CALL_STACK_KEY))
  }

  @Test
  fun `when databaseName is provided, sets system and name as span data`() {
    val sut = fixture.getSut(databaseName = "tracks.db")

    sut.performSql("sql") {}
    val span = fixture.ichnoisTracer.children.first()

    assertEquals(span.data[SpanDataConvention.DB_SYSTEM_KEY], "sqlite")
    assertEquals(span.data[SpanDataConvention.DB_NAME_KEY], "tracks.db")
  }

  @Test
  fun `when databaseName is null, sets system to in-memory`() {
    val sut = fixture.getSut()

    sut.performSql("sql") {}
    val span = fixture.ichnoisTracer.children.first()

    assertEquals(span.data[SpanDataConvention.DB_SYSTEM_KEY], "in-memory")
  }

  @Test
  fun `when performSql returns a CrossProcessCursor, does not start a span and returns a IchnoisCoreCrossProcessCursor`() {
    val sut = fixture.getSut()

    // When performSql returns a CrossProcessCursor
    val result = sut.performSql("sql") { mock<CrossProcessCursor>() }

    // Returns a IchnoisCoreCrossProcessCursor
    assertIs<IchnoisCoreCrossProcessCursor>(result)
    // And no span is started
    assertNull(fixture.ichnoisTracer.children.firstOrNull())
  }
}
