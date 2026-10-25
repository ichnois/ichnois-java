package io.ichnois

import io.ichnois.test.getProperty
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.awaitility.kotlin.await
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IchnoisCoreExecutorServiceTest {
  @Test
  fun `IchnoisCoreExecutorService forwards submit call to ExecutorService`() {
    val executor = mock<ScheduledThreadPoolExecutor> { on { queue } doReturn LinkedBlockingQueue() }
    val ichnoisExecutor = IchnoisCoreExecutorService(executor, null)
    ichnoisExecutor.submit {}
    verify(executor).submit(any())
  }

  @Test
  fun `IchnoisCoreExecutorService forwards schedule call to ExecutorService`() {
    val executor = mock<ScheduledThreadPoolExecutor> { on { queue } doReturn LinkedBlockingQueue() }
    val ichnoisExecutor = IchnoisCoreExecutorService(executor, null)
    ichnoisExecutor.schedule({}, 0L)
    verify(executor).schedule(any(), any(), any())
  }

  @Test
  fun `IchnoisCoreExecutorService forwards close call to ExecutorService`() {
    val executor = mock<ScheduledThreadPoolExecutor>()
    val ichnoisExecutor = IchnoisCoreExecutorService(executor, null)
    whenever(executor.isShutdown).thenReturn(false)
    whenever(executor.awaitTermination(any(), any())).thenReturn(true)
    ichnoisExecutor.close(15000)
    verify(executor).shutdown()
  }

  @Test
  fun `IchnoisCoreExecutorService forwards close and call shutdownNow if not enough time`() {
    val executor = mock<ScheduledThreadPoolExecutor>()
    val ichnoisExecutor = IchnoisCoreExecutorService(executor, null)
    whenever(executor.isShutdown).thenReturn(false)
    whenever(executor.awaitTermination(any(), any())).thenReturn(false)
    ichnoisExecutor.close(15000)
    verify(executor).shutdownNow()
  }

  @Test
  fun `IchnoisCoreExecutorService forwards close and call shutdownNow if await throws`() {
    val executor = mock<ScheduledThreadPoolExecutor>()
    val ichnoisExecutor = IchnoisCoreExecutorService(executor, null)
    whenever(executor.isShutdown).thenReturn(false)
    whenever(executor.awaitTermination(any(), any())).thenThrow(InterruptedException())
    ichnoisExecutor.close(15000)
    verify(executor).shutdownNow()
  }

  @Test
  fun `IchnoisCoreExecutorService forwards close but do not shutdown if its already closed`() {
    val executor = mock<ScheduledThreadPoolExecutor>()
    val ichnoisExecutor = IchnoisCoreExecutorService(executor, null)
    whenever(executor.isShutdown).thenReturn(true)
    ichnoisExecutor.close(15000)
    verify(executor, never()).shutdown()
  }

  @Test
  fun `IchnoisCoreExecutorService forwards close call to ExecutorService and close it`() {
    val executor = ScheduledThreadPoolExecutor(1)
    val ichnoisExecutor = IchnoisCoreExecutorService(executor, null)
    ichnoisExecutor.close(15000)
    assertTrue(executor.isShutdown)
  }

  @Test
  fun `IchnoisCoreExecutorService executes runnable`() {
    val ichnoisExecutor = IchnoisCoreExecutorService()
    val atomicBoolean = AtomicBoolean(true)
    ichnoisExecutor.submit { atomicBoolean.set(false) }
    await.untilFalse(atomicBoolean)
    ichnoisExecutor.close(15000)
  }

  @Test
  fun `IchnoisCoreExecutorService enables removeOnCancelPolicy when requested`() {
    val ichnoisExecutor = IchnoisCoreExecutorService(null, true, 30, TimeUnit.SECONDS)
    val executor = ichnoisExecutor.getProperty<ScheduledThreadPoolExecutor>("executorService")
    assertTrue(executor.removeOnCancelPolicy)
    ichnoisExecutor.close(15000)
  }

  @Test
  fun `IchnoisCoreExecutorService does not enable removeOnCancelPolicy by default`() {
    val ichnoisExecutor = IchnoisCoreExecutorService(null)
    val executor = ichnoisExecutor.getProperty<ScheduledThreadPoolExecutor>("executorService")
    assertFalse(executor.removeOnCancelPolicy)
    ichnoisExecutor.close(15000)
  }

  @Test
  fun `IchnoisCoreExecutorService isClosed returns true if executor is shutdown`() {
    val executor = mock<ScheduledThreadPoolExecutor>()
    val ichnoisExecutor = IchnoisCoreExecutorService(executor, null)
    whenever(executor.isShutdown).thenReturn(true)
    assertTrue(ichnoisExecutor.isClosed)
  }

  @Test
  fun `IchnoisCoreExecutorService isClosed returns false if executor is not shutdown`() {
    val executor = mock<ScheduledThreadPoolExecutor>()
    val ichnoisExecutor = IchnoisCoreExecutorService(executor, null)
    whenever(executor.isShutdown).thenReturn(false)
    assertFalse(ichnoisExecutor.isClosed)
  }

  @Test
  fun `IchnoisCoreExecutorService submit runnable returns cancelled future when queue size exceeds limit`() {
    val queue = mock<BlockingQueue<Runnable>>()
    whenever(queue.size).thenReturn(272) // Above MAX_QUEUE_SIZE (271)

    val executor = mock<ScheduledThreadPoolExecutor> { on { getQueue() } doReturn queue }

    val options = mock<IchnoisCoreOptions>()
    val logger = mock<ILogger>()
    whenever(options.logger).thenReturn(logger)

    val ichnoisExecutor = IchnoisCoreExecutorService(executor, options)
    val future = ichnoisExecutor.submit {}

    assertTrue(future.isCancelled)
    assertTrue(future.isDone)
    assertFailsWith<CancellationException> { future.get() }
    verify(executor, never()).submit(any<Runnable>())
    verify(logger).log(any<IchnoisCoreLevel>(), any<String>())
  }

  @Test
  fun `IchnoisCoreExecutorService submit runnable accepts when queue size is within limit`() {
    val queue = mock<BlockingQueue<Runnable>>()
    whenever(queue.size).thenReturn(270) // Below MAX_QUEUE_SIZE (271)

    val executor = mock<ScheduledThreadPoolExecutor> { on { getQueue() } doReturn queue }

    val ichnoisExecutor = IchnoisCoreExecutorService(executor, null)
    ichnoisExecutor.submit {}

    verify(executor).submit(any<Runnable>())
  }

  @Test
  fun `IchnoisCoreExecutorService submit callable returns cancelled future when queue size exceeds limit`() {
    val queue = mock<BlockingQueue<Runnable>>()
    whenever(queue.size).thenReturn(272) // Above MAX_QUEUE_SIZE (271)

    val executor = mock<ScheduledThreadPoolExecutor> { on { getQueue() } doReturn queue }

    val options = mock<IchnoisCoreOptions>()
    val logger = mock<ILogger>()
    whenever(options.logger).thenReturn(logger)

    val ichnoisExecutor = IchnoisCoreExecutorService(executor, options)
    val future = ichnoisExecutor.submit(Callable { "result" })

    assertTrue(future.isCancelled)
    assertTrue(future.isDone)
    assertFailsWith<CancellationException> { future.get() }
    verify(executor, never()).submit(any<Callable<String>>())
    verify(logger).log(any<IchnoisCoreLevel>(), any<String>())
  }

  @Test
  fun `IchnoisCoreExecutorService submit callable accepts when queue size is within limit`() {
    val queue = mock<BlockingQueue<Runnable>>()
    whenever(queue.size).thenReturn(270) // Below MAX_QUEUE_SIZE (271)

    val executor = mock<ScheduledThreadPoolExecutor> { on { getQueue() } doReturn queue }

    val ichnoisExecutor = IchnoisCoreExecutorService(executor, null)
    ichnoisExecutor.submit(Callable { "result" })

    verify(executor).submit(any<Callable<String>>())
  }

  @Test
  fun `IchnoisCoreExecutorService schedule accepts when queue size is within limit`() {
    val queue = mock<BlockingQueue<Runnable>>()
    whenever(queue.size).thenReturn(270) // Below MAX_QUEUE_SIZE (271)

    val executor = mock<ScheduledThreadPoolExecutor> { on { getQueue() } doReturn queue }

    val ichnoisExecutor = IchnoisCoreExecutorService(executor, null)
    ichnoisExecutor.schedule({}, 1000L)

    verify(executor).schedule(any<Runnable>(), any(), any())
  }

  @Test
  fun `IchnoisCoreExecutorService schedules any number of job`() {
    val executor = ScheduledThreadPoolExecutor(1)
    val ichnoisExecutor = IchnoisCoreExecutorService(executor, null)
    // Post 1k jobs after 1 day, to test they are all accepted
    repeat(1000) { ichnoisExecutor.schedule({}, TimeUnit.DAYS.toMillis(1)) }
    assertEquals(1000, executor.queue.size)
  }

  @Test
  fun `IchnoisCoreExecutorService purges cancelled jobs when limit is reached`() {
    val executor = ScheduledThreadPoolExecutor(1)
    val ichnoisExecutor = IchnoisCoreExecutorService(executor, null)
    // Post 1k jobs after 1 day, to test they are all accepted
    repeat(1000) {
      val future = ichnoisExecutor.schedule({}, TimeUnit.DAYS.toMillis(1))
      future.cancel(true)
    }
    assertEquals(1000, executor.queue.size)

    // Submit should purge cancelled scheduled jobs
    ichnoisExecutor.submit {}
    // The queue size should be 1, but if the executor thread runs right before we check the size,
    // it returns 0
    assertTrue(executor.queue.size < 2)
  }
}
