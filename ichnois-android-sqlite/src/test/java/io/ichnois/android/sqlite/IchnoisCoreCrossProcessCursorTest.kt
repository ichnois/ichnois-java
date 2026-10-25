package io.ichnois.android.sqlite

import android.database.CrossProcessCursor
import android.database.CursorWrapper
import io.ichnois.IScopes
import io.ichnois.ISpan
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreTracer
import io.ichnois.SpanStatus
import io.ichnois.TransactionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IchnoisCoreCrossProcessCursorTest {
  private class Fixture {
    private val scopes = mock<IScopes>()
    private val spans = OpenHelperSpans(scopes)
    val mockCursor = mock<CrossProcessCursor>()
    lateinit var options: IchnoisCoreOptions
    lateinit var ichnoisTracer: IchnoisCoreTracer

    fun getSut(sql: String, isSpanActive: Boolean = true): IchnoisCoreCrossProcessCursor {
      options = IchnoisCoreOptions().apply { dsn = "https://key@ichnois-h2jw.onrender.com/proj" }
      whenever(scopes.options).thenReturn(options)
      ichnoisTracer = IchnoisCoreTracer(TransactionContext("name", "op"), scopes)

      if (isSpanActive) {
        whenever(scopes.span).thenReturn(ichnoisTracer)
      }
      return IchnoisCoreCrossProcessCursor(mockCursor, spans, sql)
    }
  }

  private val fixture = Fixture()

  @Test
  fun `all calls are propagated to the delegate`() {
    val sql = "sql"
    val cursor = fixture.getSut(sql)

    cursor.onMove(0, 1)
    verify(fixture.mockCursor).onMove(eq(0), eq(1))

    cursor.count
    verify(fixture.mockCursor).count

    cursor.fillWindow(0, mock())
    verify(fixture.mockCursor).fillWindow(eq(0), any())
  }

  @Test
  fun `ordinary cursor methods are delegated by Android CursorWrapper`() {
    val getStringMethod =
      IchnoisCoreCrossProcessCursor::class.java.getMethod("getString", Int::class.javaPrimitiveType!!)

    assertEquals(CursorWrapper::class.java, getStringMethod.declaringClass)
  }

  @Test
  fun `getCount creates a span if a span is running`() {
    val sql = "execute"
    val sut = fixture.getSut(sql)
    assertEquals(0, fixture.ichnoisTracer.children.size)
    sut.count
    val span = fixture.ichnoisTracer.children.firstOrNull()
    assertSqlSpanCreated(sql, span)
  }

  @Test
  fun `getCount does not create a span if no span is running`() {
    val sut = fixture.getSut("execute", isSpanActive = false)
    sut.count
    assertEquals(0, fixture.ichnoisTracer.children.size)
  }

  @Test
  fun `onMove creates a span if a span is running`() {
    val sql = "execute"
    val sut = fixture.getSut(sql)
    assertEquals(0, fixture.ichnoisTracer.children.size)
    sut.onMove(0, 5)
    val span = fixture.ichnoisTracer.children.firstOrNull()
    assertSqlSpanCreated(sql, span)
  }

  @Test
  fun `onMove does not create a span if no span is running`() {
    val sut = fixture.getSut("execute", isSpanActive = false)
    sut.onMove(0, 5)
    assertEquals(0, fixture.ichnoisTracer.children.size)
  }

  @Test
  fun `fillWindow creates a span if a span is running`() {
    val sql = "execute"
    val sut = fixture.getSut(sql)
    assertEquals(0, fixture.ichnoisTracer.children.size)
    sut.fillWindow(0, mock())
    val span = fixture.ichnoisTracer.children.firstOrNull()
    assertSqlSpanCreated(sql, span)
  }

  @Test
  fun `fillWindow does not create a span if no span is running`() {
    val sut = fixture.getSut("execute", isSpanActive = false)
    sut.fillWindow(0, mock())
    assertEquals(0, fixture.ichnoisTracer.children.size)
  }

  private fun assertSqlSpanCreated(sql: String, span: ISpan?) {
    assertNotNull(span)
    assertEquals("db.sql.query", span.operation)
    assertEquals(sql, span.description)
    assertEquals(SpanStatus.OK, span.status)
    assertTrue(span.isFinished)
  }
}
