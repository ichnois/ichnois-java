package io.ichnois.test

import io.ichnois.IScope
import io.ichnois.IIchnoisCoreClient
import io.ichnois.IIchnoisCoreExecutorService
import io.ichnois.Scope
import io.ichnois.Scopes
import io.ichnois.IchnoisCoreOptions
import io.ichnois.backpressure.IBackpressureMonitor
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ImmediateExecutorService : IIchnoisCoreExecutorService {
  override fun submit(runnable: Runnable): Future<*> {
    if (runnable !is IBackpressureMonitor) {
      runnable.run()
    }
    return mock()
  }

  override fun <T> submit(callable: Callable<T>): Future<T> = mock()

  override fun schedule(runnable: Runnable, delayMillis: Long): Future<*> {
    if (runnable !is IBackpressureMonitor) {
      runnable.run()
    }
    return mock<Future<Runnable>>()
  }

  override fun close(timeoutMillis: Long) {}

  override fun isClosed(): Boolean = false
}

class DeferredExecutorService : IIchnoisCoreExecutorService {
  private var runnables = ArrayList<Runnable>()
  private var scheduledRunnables = ArrayList<Runnable>()

  fun runAll() {
    // take a snapshot of the runnable list in case
    // executing the runnable itself schedules more runnables
    val currentRunnableList = runnables
    val currentScheduledRunnableList = scheduledRunnables

    synchronized(this) {
      runnables = ArrayList()
      scheduledRunnables = ArrayList()
    }

    currentRunnableList.forEach { it.run() }
    currentScheduledRunnableList.forEach { it.run() }
  }

  override fun submit(runnable: Runnable): Future<*> {
    synchronized(this) { runnables.add(runnable) }
    return FutureTask {}
  }

  override fun <T> submit(callable: Callable<T>): Future<T> = mock()

  override fun schedule(runnable: Runnable, delayMillis: Long): Future<*> {
    synchronized(this) { scheduledRunnables.add(runnable) }
    return FutureTask {}
  }

  override fun close(timeoutMillis: Long) {}

  override fun isClosed(): Boolean = false

  fun hasScheduledRunnables(): Boolean = scheduledRunnables.isNotEmpty()
}

class NonOverridableNoOpIchnoisCoreExecutorService : IIchnoisCoreExecutorService {
  // mirrors NoOpIchnoisCoreExecutorService: a task that is never run is reported as cancelled, so
  // callers can tell it apart from a queued one
  private fun <T> cancelledFuture(): FutureTask<T> = FutureTask<T> { null }.apply { cancel(false) }

  override fun submit(runnable: Runnable): Future<*> = cancelledFuture<Void>()

  override fun <T> submit(callable: Callable<T>): Future<T> = cancelledFuture()

  override fun schedule(runnable: Runnable, delayMillis: Long): Future<*> = cancelledFuture<Void>()

  override fun close(timeoutMillis: Long) {}

  override fun isClosed(): Boolean = false
}

fun createIchnoisCoreClientMock(enabled: Boolean = true) =
  mock<IIchnoisCoreClient>().also {
    val isEnabled = AtomicBoolean(enabled)
    whenever(it.isEnabled).then { isEnabled.get() }
    whenever(it.close()).then { isEnabled.set(false) }
    whenever(it.close(any())).then { isEnabled.set(false) }
  }

fun createTestScopes(
  options: IchnoisCoreOptions? = null,
  enabled: Boolean = true,
  scope: IScope? = null,
  isolationScope: IScope? = null,
  globalScope: IScope? = null,
): Scopes {
  val optionsToUse = options ?: IchnoisCoreOptions().also { it.dsn = "https://key@ichnois-h2jw.onrender.com/proj" }
  initForTest(optionsToUse)
  val scopeToUse = scope ?: Scope(optionsToUse)
  val isolationScopeToUse = isolationScope ?: Scope(optionsToUse)
  val globalScopeToUse = globalScope ?: Scope(optionsToUse)
  return Scopes(scopeToUse, isolationScopeToUse, globalScopeToUse, "test").also {
    if (enabled) {
      it.bindClient(createIchnoisCoreClientMock())
    }
  }
}
