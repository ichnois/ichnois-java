package io.ichnois.android.timber

import android.util.Log
import io.ichnois.Breadcrumb
import io.ichnois.IScopes
import io.ichnois.IchnoisCoreAttribute
import io.ichnois.IchnoisCoreAttributes
import io.ichnois.IchnoisCoreEvent
import io.ichnois.IchnoisCoreLevel
import io.ichnois.IchnoisCoreLogLevel
import io.ichnois.logger.IchnoisCoreLogParameters
import io.ichnois.protocol.Message
import timber.log.Timber

/** IchnoisCore Timber tree which is responsible to capture events via Timber */
@Suppress("TooManyFunctions") // we have to override all methods to be able to tweak logging
public class IchnoisCoreTimberTree(
  private val scopes: IScopes,
  private val minEventLevel: IchnoisCoreLevel,
  private val minBreadcrumbLevel: IchnoisCoreLevel,
  private val minLogLevel: IchnoisCoreLogLevel = IchnoisCoreLogLevel.INFO,
) : Timber.Tree() {
  private val pendingTag = ThreadLocal<String?>()

  private fun retrieveTag(): String? {
    val tag = pendingTag.get()
    if (tag != null) {
      this.pendingTag.remove()
    }
    return tag
  }

  /** Log a verbose message with optional format args. */
  override fun v(message: String?, vararg args: Any?) {
    super.v(message, *args)
    logWithIchnoisCore(Log.VERBOSE, null, message, *args)
  }

  /** Log a verbose exception and a message with optional format args. */
  override fun v(t: Throwable?, message: String?, vararg args: Any?) {
    super.v(t, message, *args)
    logWithIchnoisCore(Log.VERBOSE, t, message, *args)
  }

  /** Log a verbose exception. */
  override fun v(t: Throwable?) {
    super.v(t)
    logWithIchnoisCore(Log.VERBOSE, t, null)
  }

  /** Log a debug message with optional format args. */
  override fun d(message: String?, vararg args: Any?) {
    super.d(message, *args)
    logWithIchnoisCore(Log.DEBUG, null, message, *args)
  }

  /** Log a debug exception and a message with optional format args. */
  override fun d(t: Throwable?, message: String?, vararg args: Any?) {
    super.d(t, message, *args)
    logWithIchnoisCore(Log.DEBUG, t, message, *args)
  }

  /** Log a debug exception. */
  override fun d(t: Throwable?) {
    super.d(t)
    logWithIchnoisCore(Log.DEBUG, t, null)
  }

  /** Log an info message with optional format args. */
  override fun i(message: String?, vararg args: Any?) {
    super.i(message, *args)
    logWithIchnoisCore(Log.INFO, null, message, *args)
  }

  /** Log an info exception and a message with optional format args. */
  override fun i(t: Throwable?, message: String?, vararg args: Any?) {
    super.i(t, message, *args)
    logWithIchnoisCore(Log.INFO, t, message, *args)
  }

  /** Log an info exception. */
  override fun i(t: Throwable?) {
    super.i(t)
    logWithIchnoisCore(Log.INFO, t, null)
  }

  /** Log a warning message with optional format args. */
  override fun w(message: String?, vararg args: Any?) {
    super.w(message, *args)
    logWithIchnoisCore(Log.WARN, null, message, *args)
  }

  /** Log a warning exception and a message with optional format args. */
  override fun w(t: Throwable?, message: String?, vararg args: Any?) {
    super.w(t, message, *args)
    logWithIchnoisCore(Log.WARN, t, message, *args)
  }

  /** Log a warning exception. */
  override fun w(t: Throwable?) {
    super.w(t)
    logWithIchnoisCore(Log.WARN, t, null)
  }

  /** Log an error message with optional format args. */
  override fun e(message: String?, vararg args: Any?) {
    super.e(message, *args)
    logWithIchnoisCore(Log.ERROR, null, message, *args)
  }

  /** Log an error exception and a message with optional format args. */
  override fun e(t: Throwable?, message: String?, vararg args: Any?) {
    super.e(t, message, *args)
    logWithIchnoisCore(Log.ERROR, t, message, *args)
  }

  /** Log an error exception. */
  override fun e(t: Throwable?) {
    super.e(t)
    logWithIchnoisCore(Log.ERROR, t, null)
  }

  /** Log an assert message with optional format args. */
  override fun wtf(message: String?, vararg args: Any?) {
    super.wtf(message, *args)
    logWithIchnoisCore(Log.ASSERT, null, message, *args)
  }

  /** Log an assert exception and a message with optional format args. */
  override fun wtf(t: Throwable?, message: String?, vararg args: Any?) {
    super.wtf(t, message, *args)
    logWithIchnoisCore(Log.ASSERT, t, message, *args)
  }

  /** Log an assert exception. */
  override fun wtf(t: Throwable?) {
    super.wtf(t)
    logWithIchnoisCore(Log.ASSERT, t, null)
  }

  /** Log at `priority` a message with optional format args. */
  override fun log(priority: Int, message: String?, vararg args: Any?) {
    super.log(priority, message, *args)
    logWithIchnoisCore(priority, null, message, *args)
  }

  /** Log at `priority` an exception and a message with optional format args. */
  override fun log(priority: Int, t: Throwable?, message: String?, vararg args: Any?) {
    super.log(priority, t, message, *args)
    logWithIchnoisCore(priority, t, message, *args)
  }

  /** Log at `priority` an exception. */
  override fun log(priority: Int, t: Throwable?) {
    super.log(priority, t)
    logWithIchnoisCore(priority, t, null)
  }

  override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
    pendingTag.set(tag)
  }

  private fun logWithIchnoisCore(
    priority: Int,
    throwable: Throwable?,
    message: String?,
    vararg args: Any?,
  ) {
    val tag = retrieveTag()

    if (message.isNullOrEmpty() && throwable == null) {
      return // Swallow message if it's null and there's no throwable
    }

    val level = getIchnoisCoreLevel(priority)
    val logLevel = getIchnoisCoreLogLevel(priority)
    val ichnoisMessage =
      Message().apply {
        this.message = message
        if (!message.isNullOrEmpty() && args.isNotEmpty()) {
          this.formatted = message.format(*args)
        }
        this.params = args.map { it.toString() }
      }

    captureEvent(level, tag, ichnoisMessage, throwable)
    addBreadcrumb(level, ichnoisMessage, throwable)
    addLog(logLevel, message, tag, throwable, *args)
  }

  /** do not log if it's lower than min. required level. */
  private fun isLoggable(level: IchnoisCoreLevel, minLevel: IchnoisCoreLevel): Boolean =
    level.ordinal >= minLevel.ordinal

  /** do not log if it's lower than min. required level. */
  private fun isLoggable(level: IchnoisCoreLogLevel, minLevel: IchnoisCoreLogLevel): Boolean =
    level.ordinal >= minLevel.ordinal

  /** Captures an event with the given attributes */
  private fun captureEvent(
    ichnoisLevel: IchnoisCoreLevel,
    tag: String?,
    msg: Message,
    throwable: Throwable?,
  ) {
    if (isLoggable(ichnoisLevel, minEventLevel)) {
      val ichnoisEvent =
        IchnoisCoreEvent().apply {
          level = ichnoisLevel
          throwable?.let { setThrowable(it) }
          tag?.let { setTag("TimberTag", it) }
          message = msg
          logger = "Timber"
        }

      scopes.captureEvent(ichnoisEvent)
    }
  }

  /** Adds a breadcrumb */
  private fun addBreadcrumb(ichnoisLevel: IchnoisCoreLevel, msg: Message, throwable: Throwable?) {
    // checks the breadcrumb level
    if (isLoggable(ichnoisLevel, minBreadcrumbLevel)) {
      val throwableMsg = throwable?.message
      val breadCrumb =
        when {
          msg.message != null ->
            Breadcrumb().apply {
              level = ichnoisLevel
              category = "Timber"
              message = msg.formatted ?: msg.message
            }
          throwableMsg != null -> Breadcrumb.error(throwableMsg).apply { category = "exception" }
          else -> null
        }

      breadCrumb?.let { scopes.addBreadcrumb(it) }
    }
  }

  /** Send a IchnoisCore Logs */
  private fun addLog(
    ichnoisLogLevel: IchnoisCoreLogLevel,
    msg: String?,
    tag: String?,
    throwable: Throwable?,
    vararg args: Any?,
  ) {
    if (!scopes.options.logs.isEnabled) {
      return
    }

    // checks the log level
    if (isLoggable(ichnoisLogLevel, minLogLevel)) {
      val attributes = tag?.let {
        IchnoisCoreAttributes.of(IchnoisCoreAttribute.stringAttribute("timber.tag", tag))
      }
      val params = IchnoisCoreLogParameters.create(attributes)
      params.origin = "auto.log.timber"

      val throwableMsg = throwable?.message
      when {
        msg != null && throwableMsg != null ->
          scopes.logger().log(ichnoisLogLevel, params, "$msg\n$throwableMsg", *args)
        msg != null -> scopes.logger().log(ichnoisLogLevel, params, msg, *args)
        throwableMsg != null -> scopes.logger().log(ichnoisLogLevel, params, throwableMsg, *args)
      }
    }
  }

  /** Converts from Timber priority to IchnoisCoreLevel. Fallback to IchnoisCoreLevel.DEBUG. */
  private fun getIchnoisCoreLevel(priority: Int): IchnoisCoreLevel =
    when (priority) {
      Log.ASSERT -> IchnoisCoreLevel.FATAL
      Log.ERROR -> IchnoisCoreLevel.ERROR
      Log.WARN -> IchnoisCoreLevel.WARNING
      Log.INFO -> IchnoisCoreLevel.INFO
      Log.DEBUG -> IchnoisCoreLevel.DEBUG
      Log.VERBOSE -> IchnoisCoreLevel.DEBUG
      else -> IchnoisCoreLevel.DEBUG
    }

  /** Converts from Timber priority to IchnoisCoreLogLevel. Fallback to IchnoisCoreLogLevel.DEBUG. */
  private fun getIchnoisCoreLogLevel(priority: Int): IchnoisCoreLogLevel {
    return when (priority) {
      Log.ASSERT -> IchnoisCoreLogLevel.FATAL
      Log.ERROR -> IchnoisCoreLogLevel.ERROR
      Log.WARN -> IchnoisCoreLogLevel.WARN
      Log.INFO -> IchnoisCoreLogLevel.INFO
      Log.DEBUG -> IchnoisCoreLogLevel.DEBUG
      Log.VERBOSE -> IchnoisCoreLogLevel.TRACE
      else -> IchnoisCoreLogLevel.DEBUG
    }
  }
}
