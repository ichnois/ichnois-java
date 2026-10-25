package io.ichnois.asyncprofiler.provider

import io.ichnois.ILogger
import io.ichnois.IIchnoisCoreExecutorService
import io.ichnois.NoOpContinuousProfiler
import io.ichnois.asyncprofiler.profiling.JavaContinuousProfiler
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import one.profiler.AsyncProfiler
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic

class AsyncProfilerContinuousProfilerProviderTest {

  @Test
  fun `provider returns JavaAsyncProfiler if AsyncProfiler can be loaded`() {
    val profiler =
      AsyncProfilerContinuousProfilerProvider()
        .getContinuousProfiler(
          mock(ILogger::class.java),
          "",
          10,
          mock(IIchnoisCoreExecutorService::class.java),
        )

    assertTrue(profiler is JavaContinuousProfiler)
  }

  @Test
  fun `provider return NoopProfiler if AsyncProfiler cannot be loaded`() {
    mockStatic(AsyncProfiler::class.java).use {
      it.`when`<Any> { AsyncProfiler.getInstance() }.thenReturn(null)

      val profiler =
        AsyncProfilerContinuousProfilerProvider()
          .getContinuousProfiler(
            mock(ILogger::class.java),
            "",
            10,
            mock(IIchnoisCoreExecutorService::class.java),
          )

      assertSame(NoOpContinuousProfiler.getInstance(), profiler)
    }
  }
}
