package io.ichnois.samples.android

import io.ichnois.IchnoisCoreOptions
import io.ichnois.protocol.IchnoisCoreTransaction
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Owns the single global beforeSendTransaction hook used by sample screens that inspect finished
 * transactions.
 *
 * Multiple sample activities can overlap briefly during configuration changes or relaunches. If
 * each screen installs and restores its own callback, a later uninstall can resurrect a stale
 * wrapper callback that still retains dead state and drops the original callback chain.
 *
 * Keep one stable callback installed and let sample screens register lightweight listeners against
 * it instead.
 */
internal object SampleBeforeSendTransactionHook {

  private val listeners = CopyOnWriteArrayList<(IchnoisCoreTransaction, String?) -> Unit>()

  @Volatile private var installedCallback: IchnoisCoreOptions.BeforeSendTransactionCallback? = null
  @Volatile private var previousCallback: IchnoisCoreOptions.BeforeSendTransactionCallback? = null

  fun installIfNeeded(options: IchnoisCoreOptions) {
    if (installedCallback != null) {
      return
    }

    synchronized(this) {
      if (installedCallback != null) {
        return
      }

      previousCallback = options.beforeSendTransaction
      val callback = IchnoisCoreOptions.BeforeSendTransactionCallback { transaction, hint ->
        val previous = previousCallback

        val processedTransaction =
          if (previous == null) {
            transaction
          } else {
            previous.execute(transaction, hint)
          }

        processedTransaction?.let { processed ->
          listeners.forEach { listener ->
            try {
              listener(processed, options.dsn)
            } catch (e: RuntimeException) {
              options.logger.log(
                io.ichnois.IchnoisCoreLevel.ERROR,
                "Sample transaction listener failed.",
                e,
              )
            }
          }
        }

        processedTransaction
      }

      installedCallback = callback
      options.beforeSendTransaction = callback
    }
  }

  fun addListener(listener: (IchnoisCoreTransaction, String?) -> Unit) {
    listeners.addIfAbsent(listener)
  }

  fun removeListener(listener: (IchnoisCoreTransaction, String?) -> Unit) {
    listeners.remove(listener)
  }
}
