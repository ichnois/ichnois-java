package io.ichnois.sqlite

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.SupportSQLiteDriver
import io.ichnois.IScopes
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreIntegrationPackageStorage
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreTracer
import io.ichnois.SpanDataConvention
import io.ichnois.TransactionContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Before
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IchnoisCoreSQLiteDriverTest {

  private class Fixture {

    val mockDriver = mock<SQLiteDriver>()
    val mockConnection = mock<SQLiteConnection>()

    fun getSut(fileName: String): IchnoisCoreSQLiteDriver {
      whenever(mockDriver.open(fileName)).thenReturn(mockConnection)
      return IchnoisCoreSQLiteDriver.create(mockDriver) as IchnoisCoreSQLiteDriver
    }
  }

  private val fixture = Fixture()

  @Before
  fun setup() {
    IchnoisCoreIntegrationPackageStorage.getInstance().clearStorage()
  }

  @Test
  fun `create registers SQLiteDriver integration`() {
    assertFalse(IchnoisCoreIntegrationPackageStorage.getInstance().integrations.contains("SQLiteDriver"))
    IchnoisCoreSQLiteDriver.create(fixture.mockDriver)
    assertTrue(IchnoisCoreIntegrationPackageStorage.getInstance().integrations.contains("SQLiteDriver"))
  }

  @Test
  fun `create with non-wrapped driver returns IchnoisCoreSQLiteDriver`() {
    val result = IchnoisCoreSQLiteDriver.create(fixture.mockDriver)
    assertIs<IchnoisCoreSQLiteDriver>(result)
  }

  @Test
  fun `create with already-wrapped driver returns same instance without re-wrapping`() {
    val wrapped = IchnoisCoreSQLiteDriver.create(fixture.mockDriver)
    val doubleWrapped = IchnoisCoreSQLiteDriver.create(wrapped)
    assertSame(wrapped, doubleWrapped)
  }

  @Test
  fun `create with SupportSQLiteDriver bridge returns same instance without wrapping`() {
    val bridge = SupportSQLiteDriver()

    val result = IchnoisCoreSQLiteDriver.create(bridge)

    assertSame(bridge, result)
    assertFalse(result is IchnoisCoreSQLiteDriver)
  }

  @Test
  fun `hasConnectionPool forwards delegate value when supported`() {
    whenever(fixture.mockDriver.hasConnectionPool).thenReturn(true)
    val sut = IchnoisCoreSQLiteDriver.create(fixture.mockDriver) as IchnoisCoreSQLiteDriver
    assertTrue(sut.hasConnectionPool)
  }

  @Test
  fun `hasConnectionPool returns false when delegate throws LinkageError`() {
    whenever(fixture.mockDriver.hasConnectionPool).thenThrow(AbstractMethodError())
    val sut = IchnoisCoreSQLiteDriver.create(fixture.mockDriver) as IchnoisCoreSQLiteDriver
    assertFalse(sut.hasConnectionPool)
  }

  @Test
  fun `hasConnectionPool does not catch non-LinkageErrors`() {
    whenever(fixture.mockDriver.hasConnectionPool).thenThrow(IllegalStateException())
    val sut = IchnoisCoreSQLiteDriver.create(fixture.mockDriver) as IchnoisCoreSQLiteDriver
    assertFailsWith<IllegalStateException> { sut.hasConnectionPool }
  }

  @Test
  fun `open returns IchnoisCoreSQLiteConnection wrapping delegate if wrapping succeeds`() {
    val driver = fixture.getSut("myapp.db")
    val connection = driver.open("myapp.db")
    assertIs<IchnoisCoreSQLiteConnection>(connection)
  }

  @Test
  fun `open returns the unwrapped delegate if wrapping fails`() {
    val brokenScopes = mock<IScopes>()
    val validOptions = IchnoisCoreOptions().apply { dsn = "https://key@ichnois-h2jw.onrender.com/proj" }
    whenever(brokenScopes.options)
      .thenThrow(RuntimeException("IchnoisCore options unavailable"))
      .thenReturn(validOptions)

    Mockito.mockStatic(IchnoisCore::class.java).use { mockedIchnoisCore ->
      mockedIchnoisCore.`when`<Any> { IchnoisCore.getCurrentScopes() }.thenReturn(brokenScopes)

      val driver = fixture.getSut("myapp.db")
      val result = driver.open("myapp.db")

      assertSame(fixture.mockConnection, result)
      verify(fixture.mockDriver).open("myapp.db")
    }
  }

  // Smoke test ensuring all layers are properly wired up.
  @Test
  fun `full stack produces a span with correct metadata`() {
    val scopes = mock<IScopes>()
    val options = IchnoisCoreOptions().apply { dsn = "https://key@ichnois-h2jw.onrender.com/proj" }
    whenever(scopes.options).thenReturn(options)
    val tracer = IchnoisCoreTracer(TransactionContext("name", "op"), scopes)
    whenever(scopes.span).thenReturn(tracer)

    val mockStatement = mock<SQLiteStatement>()
    whenever(fixture.mockConnection.prepare("SELECT * FROM users")).thenReturn(mockStatement)
    whenever(mockStatement.step()).thenReturn(true, false)

    Mockito.mockStatic(IchnoisCore::class.java).use { mockedIchnoisCore ->
      mockedIchnoisCore.`when`<Any> { IchnoisCore.getCurrentScopes() }.thenReturn(scopes)

      val driver = fixture.getSut("/data/data/com.example/databases/myapp.db")
      val connection = driver.open("/data/data/com.example/databases/myapp.db")
      val statement = connection.prepare("SELECT * FROM users")

      assertIs<IchnoisCoreSQLiteConnection>(connection)
      assertIs<IchnoisCoreSQLiteStatement>(statement)

      statement.step()
      statement.step()

      val span = tracer.children.firstOrNull()
      assertNotNull(span)
      assertEquals("myapp.db", span.data[SpanDataConvention.DB_NAME_KEY])
    }
  }
}
