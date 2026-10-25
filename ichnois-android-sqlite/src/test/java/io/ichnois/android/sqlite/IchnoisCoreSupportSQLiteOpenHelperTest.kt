package io.ichnois.android.sqlite

import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IchnoisCoreSupportSQLiteOpenHelperTest {
  class Fixture {
    val mockOpenHelper = mock<SupportSQLiteOpenHelper>()

    init {
      whenever(mockOpenHelper.writableDatabase).thenReturn(mock())
      whenever(mockOpenHelper.readableDatabase).thenReturn(mock())
    }

    fun getSut(): IchnoisCoreSupportSQLiteOpenHelper =
      IchnoisCoreSupportSQLiteOpenHelper.create(mockOpenHelper) as IchnoisCoreSupportSQLiteOpenHelper
  }

  private val fixture = Fixture()

  @Test
  fun `all calls are propagated to the delegate`() {
    val openHelper = fixture.getSut()

    openHelper.writableDatabase
    verify(fixture.mockOpenHelper).writableDatabase

    openHelper.readableDatabase
    verify(fixture.mockOpenHelper).readableDatabase

    openHelper.databaseName
    verify(fixture.mockOpenHelper, times(2)).databaseName

    openHelper.close()
    verify(fixture.mockOpenHelper).close()
  }

  @Test
  fun `writableDatabase returns a IchnoisCoreSupportSQLiteDatabase`() {
    val openHelper = fixture.getSut()
    assertIs<IchnoisCoreSupportSQLiteDatabase>(openHelper.writableDatabase)
  }

  @Test
  fun `create returns a IchnoisCoreSupportSQLiteOpenHelper wrapper`() {
    val openHelper: SupportSQLiteOpenHelper =
      IchnoisCoreSupportSQLiteOpenHelper.Companion.create(fixture.mockOpenHelper)
    assertIs<IchnoisCoreSupportSQLiteDatabase>(openHelper.writableDatabase)
    assertNotEquals(fixture.mockOpenHelper, openHelper)
  }

  @Test
  fun `create returns the passed openHelper if it is a IchnoisCoreSupportSQLiteOpenHelper`() {
    val ichnoisOpenHelper = mock<IchnoisCoreSupportSQLiteOpenHelper>()
    val openHelper: SupportSQLiteOpenHelper =
      IchnoisCoreSupportSQLiteOpenHelper.Companion.create(ichnoisOpenHelper)
    assertEquals(ichnoisOpenHelper, openHelper)
  }
}
