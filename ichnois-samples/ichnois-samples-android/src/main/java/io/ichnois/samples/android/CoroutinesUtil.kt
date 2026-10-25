package io.ichnois.samples.android

import io.ichnois.kotlin.IchnoisCoreContext
import io.ichnois.kotlin.IchnoisCoreCoroutineExceptionHandler
import java.lang.RuntimeException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object CoroutinesUtil {
  fun throwInCoroutine() {
    GlobalScope.launch(IchnoisCoreContext() + IchnoisCoreCoroutineExceptionHandler()) {
      throw RuntimeException("Exception in coroutine")
    }
  }
}
