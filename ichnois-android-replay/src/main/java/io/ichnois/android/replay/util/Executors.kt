package io.ichnois.android.replay.util

import io.ichnois.IIchnoisCoreExecutorService
import io.ichnois.IchnoisCoreLevel.ERROR
import io.ichnois.IchnoisCoreOptions
import java.util.concurrent.Future

internal fun IIchnoisCoreExecutorService.submitSafely(
  options: IchnoisCoreOptions,
  taskName: String,
  task: Runnable,
): Future<*>? =
  try {
    submit {
      try {
        task.run()
      } catch (e: Throwable) {
        options.logger.log(ERROR, "Failed to execute task $taskName", e)
      }
    }
  } catch (e: Throwable) {
    options.logger.log(ERROR, "Failed to submit task $taskName to executor", e)
    null
  }
