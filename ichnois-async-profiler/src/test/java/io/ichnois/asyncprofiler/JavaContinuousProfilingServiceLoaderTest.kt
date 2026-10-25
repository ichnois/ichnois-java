package io.ichnois.asyncprofiler

import io.ichnois.ILogger
import io.ichnois.asyncprofiler.profiling.JavaContinuousProfiler
import io.ichnois.asyncprofiler.provider.AsyncProfilerProfileConverterProvider
import io.ichnois.profiling.ProfilingServiceLoader
import kotlin.test.Test
import org.mockito.kotlin.mock

class JavaContinuousProfilingServiceLoaderTest {
  @Test
  fun loadsAsyncProfilerProfileConverter() {
    val service = ProfilingServiceLoader.loadProfileConverter()
    assert(service is AsyncProfilerProfileConverterProvider.AsyncProfilerProfileConverter)
  }

  @Test
  fun loadsJavaAsyncProfiler() {
    val logger = mock<ILogger>()

    val service = ProfilingServiceLoader.loadContinuousProfiler(logger, "", 10, null)
    assert(service is JavaContinuousProfiler)
  }
}
