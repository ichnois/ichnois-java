package io.ichnois.android.sqlite

import android.database.CrossProcessCursor
import android.database.SQLException
import io.ichnois.IScopes
import io.ichnois.ISpan
import io.ichnois.Instrumenter
import io.ichnois.ScopesAdapter
import io.ichnois.IchnoisCoreIntegrationPackageStorage
import io.ichnois.IchnoisCoreStackTraceFactory
import io.ichnois.SpanDataConvention
import io.ichnois.SpanStatus

private const val TRACE_ORIGIN = "auto.db.sqlite"

/** Span instrumentation for [IchnoisCoreSupportSQLiteOpenHelper]. */
internal class OpenHelperSpans(
  private val scopes: IScopes = ScopesAdapter.getInstance(),
  private val databaseName: String? = null,
) {
  private val stackTraceFactory = IchnoisCoreStackTraceFactory(scopes.options)

  init {
    IchnoisCoreIntegrationPackageStorage.getInstance().addIntegration("SQLite")
  }

  /**
   * Performs a sql operation, creates a span and handles exceptions in case of occurrence.
   *
   * @param sql The sql query
   * @param operation The sql operation to execute. In case of an error the surrounding span will
   *   have its status set to INTERNAL_ERROR
   */
  @Suppress("TooGenericExceptionCaught", "UNCHECKED_CAST")
  @Throws(SQLException::class)
  fun <T> performSql(sql: String, operation: () -> T): T {
    val startTimestamp = scopes.getOptions().dateProvider.now()
    var span: ISpan? = null
    return try {
      val result = operation()
      /*
       * SQLiteCursor - that extends CrossProcessCursor - executes the query lazily, when one of
       *  getCount() or onMove() is called. In this case we don't have to start the span here.
       * Otherwise we start the span with the timestamp taken before the operation started.
       */
      if (result is CrossProcessCursor) {
        return IchnoisCoreCrossProcessCursor(result, this, sql) as T
      }
      span = scopes.span?.startChild("db.sql.query", sql, startTimestamp, Instrumenter.ICHNOIS)
      span?.spanContext?.origin = TRACE_ORIGIN
      span?.status = SpanStatus.OK
      result
    } catch (e: Throwable) {
      span = scopes.span?.startChild("db.sql.query", sql, startTimestamp, Instrumenter.ICHNOIS)
      span?.spanContext?.origin = TRACE_ORIGIN
      span?.status = SpanStatus.INTERNAL_ERROR
      span?.throwable = e
      throw e
    } finally {
      span?.apply {
        val isMainThread: Boolean = scopes.options.threadChecker.isMainThread
        setData(SpanDataConvention.BLOCKED_MAIN_THREAD_KEY, isMainThread)
        if (isMainThread) {
          setData(SpanDataConvention.CALL_STACK_KEY, stackTraceFactory.inAppCallStack)
        }
        // if db name is null, then it's an in-memory database as per
        // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:sqlite/sqlite/src/main/java/androidx/sqlite/db/SupportSQLiteOpenHelper.kt;l=38-42
        if (databaseName != null) {
          setData(SpanDataConvention.DB_SYSTEM_KEY, "sqlite")
          setData(SpanDataConvention.DB_NAME_KEY, databaseName)
        } else {
          setData(SpanDataConvention.DB_SYSTEM_KEY, "in-memory")
        }

        finish()
      }
    }
  }
}
