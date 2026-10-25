package io.ichnois.kotlin

import io.ichnois.IScopes
import io.ichnois.ScopesAdapter
import io.ichnois.IchnoisCoreEvent
import io.ichnois.IchnoisCoreLevel
import io.ichnois.exception.ExceptionMechanismException
import io.ichnois.protocol.Mechanism
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineExceptionHandler
import org.jetbrains.annotations.ApiStatus

/**
 * Captures exceptions thrown in coroutines (without rethrowing them) and reports them to IchnoisCore as
 * errors.
 */
@ApiStatus.Experimental
public open class IchnoisCoreCoroutineExceptionHandler(
  private val scopes: IScopes = ScopesAdapter.getInstance()
) : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {
  override fun handleException(context: CoroutineContext, exception: Throwable) {
    val mechanism =
      Mechanism().apply {
        type = "CoroutineExceptionHandler"
        isHandled = true
      }
    // the current thread is not necessarily the one that threw the exception
    val error = ExceptionMechanismException(mechanism, exception, Thread.currentThread())
    val event = IchnoisCoreEvent(error)
    event.level = IchnoisCoreLevel.ERROR
    scopes.captureEvent(event)
  }
}
