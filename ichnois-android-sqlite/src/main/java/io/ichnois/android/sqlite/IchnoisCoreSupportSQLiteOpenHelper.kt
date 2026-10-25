package io.ichnois.android.sqlite

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper

/**
 * The IchnoisCore's [IchnoisCoreSupportSQLiteOpenHelper], it will automatically add a span out of the active
 * span bound to the scope for each database query. It's a wrapper around an instance of
 * [SupportSQLiteOpenHelper].
 *
 * You can wrap your custom [SupportSQLiteOpenHelper] instance with
 * `IchnoisCoreSupportSQLiteOpenHelper(myHelper)`. If you're using the IchnoisCore Android Gradle plugin, this
 * will be applied automatically.
 *
 * Usage - wrap your custom [SupportSQLiteOpenHelper] instance in [IchnoisCoreSupportSQLiteOpenHelper]
 *
 * ```
 * val openHelper = IchnoisCoreSupportSQLiteOpenHelper.create(myOpenHelper)
 * ```
 *
 * If you use Room you can wrap the default [FrameworkSQLiteOpenHelperFactory]:
 * ```
 * val database = Room.databaseBuilder(context, MyDatabase::class.java, "dbName")
 *     .openHelperFactory { configuration ->
 *         IchnoisCoreSupportSQLiteOpenHelper.create(FrameworkSQLiteOpenHelperFactory().create(configuration))
 *     }
 *     ...
 *     .build()
 * ```
 *
 * @param delegate The [SupportSQLiteOpenHelper] instance to delegate calls to.
 */
public class IchnoisCoreSupportSQLiteOpenHelper
private constructor(private val delegate: SupportSQLiteOpenHelper) :
  SupportSQLiteOpenHelper by delegate {
  private val spans = OpenHelperSpans(databaseName = delegate.databaseName)

  private val ichnoisWritableDatabase: SupportSQLiteDatabase by lazy {
    IchnoisCoreSupportSQLiteDatabase(delegate.writableDatabase, spans)
  }

  private val ichnoisReadableDatabase: SupportSQLiteDatabase by lazy {
    IchnoisCoreSupportSQLiteDatabase(delegate.readableDatabase, spans)
  }

  override val writableDatabase: SupportSQLiteDatabase
    get() = ichnoisWritableDatabase

  override val readableDatabase: SupportSQLiteDatabase
    get() = ichnoisReadableDatabase

  public companion object {
    // @JvmStatic is needed to let this method be accessed by our gradle plugin
    @JvmStatic
    public fun create(delegate: SupportSQLiteOpenHelper): SupportSQLiteOpenHelper =
      if (delegate is IchnoisCoreSupportSQLiteOpenHelper) {
        delegate
      } else {
        IchnoisCoreSupportSQLiteOpenHelper(delegate)
      }
  }
}
