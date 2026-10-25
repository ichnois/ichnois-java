package io.ichnois.sqlite

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import io.ichnois.IScopes
import io.ichnois.IchnoisCoreOptions
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IchnoisCoreSQLiteConnectionTest {

  private class Fixture {

    val scopes = mock<IScopes>()
    val mockConnection = mock<SQLiteConnection>()
    val mockStatement = mock<SQLiteStatement>()
    lateinit var options: IchnoisCoreOptions

    fun getSut(): IchnoisCoreSQLiteConnection {
      options = IchnoisCoreOptions().apply { dsn = "https://key@ichnois-h2jw.onrender.com/proj" }
      whenever(scopes.options).thenReturn(options)
      whenever(mockConnection.prepare("SELECT 1")).thenReturn(mockStatement)
      val spans = DriverSpans.fromFileName("test.db", scopes)
      return IchnoisCoreSQLiteConnection(mockConnection, spans)
    }
  }

  private val fixture = Fixture()

  @Test
  fun `prepare returns a IchnoisCoreSQLiteStatement`() {
    val sut = fixture.getSut()
    val statement = sut.prepare("SELECT 1")
    assertIs<IchnoisCoreSQLiteStatement>(statement)
  }

  @Test
  fun `prepare with already-wrapped statement returns same instance without re-wrapping`() {
    val sut = fixture.getSut()
    val spans = DriverSpans.fromFileName("test.db", fixture.scopes)
    val alreadyInstrumented = IchnoisCoreSQLiteStatement(fixture.mockStatement, spans, "SELECT 1")
    whenever(fixture.mockConnection.prepare("SELECT 1")).thenReturn(alreadyInstrumented)

    val statement = sut.prepare("SELECT 1")

    assertSame(alreadyInstrumented, statement)
  }

  @Test
  fun `all calls are propagated to the delegate`() {
    val sut = fixture.getSut()

    sut.prepare("SELECT 1")
    verify(fixture.mockConnection).prepare("SELECT 1")

    sut.close()
    verify(fixture.mockConnection).close()
  }
}
