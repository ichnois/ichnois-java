package io.ichnois.asyncprofiler.init

import io.ichnois.ILogger
import io.ichnois.IIchnoisCoreExecutorService
import io.ichnois.NoOpContinuousProfiler
import io.ichnois.NoOpProfileConverter
import io.ichnois.IchnoisCoreOptions
import io.ichnois.asyncprofiler.profiling.JavaContinuousProfiler
import io.ichnois.asyncprofiler.provider.AsyncProfilerProfileConverterProvider
import io.ichnois.util.InitUtil
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import org.mockito.kotlin.mock

class AsyncProfilerInitUtilTest {

  @Test
  fun `initialize Profiler returns no-op profiler if profiling disabled`() {
    val options = IchnoisCoreOptions()
    val profiler = InitUtil.initializeProfiler(options)
    assert(profiler is NoOpContinuousProfiler)
  }

  @Test
  fun `initialize Converter returns no-op converter if profiling disabled`() {
    val options = IchnoisCoreOptions()
    val converter = InitUtil.initializeProfileConverter(options)
    assert(converter is NoOpProfileConverter)
  }

  @Test
  fun `initialize profiler returns the existing profiler from options if already initialized`() {
    val initialProfiler =
      JavaContinuousProfiler(mock<ILogger>(), "", 10, mock<IIchnoisCoreExecutorService>())
    val options =
      IchnoisCoreOptions().also {
        it.setProfileSessionSampleRate(1.0)
        it.setContinuousProfiler(initialProfiler)
      }

    val profiler = InitUtil.initializeProfiler(options)
    assertSame(initialProfiler, profiler)
  }

  @Test
  fun `initialize converter returns the existing converter from options if already initialized`() {
    val initialConverter = AsyncProfilerProfileConverterProvider.AsyncProfilerProfileConverter()
    val options =
      IchnoisCoreOptions().also {
        it.setProfileSessionSampleRate(1.0)
        it.profilerConverter = initialConverter
      }

    val converter = InitUtil.initializeProfileConverter(options)
    assertSame(initialConverter, converter)
  }

  @Test
  fun `initialize Profiler returns JavaContinuousProfiler if profiling enabled but profiler not yet initialized`() {
    val options = IchnoisCoreOptions().also { it.setProfileSessionSampleRate(1.0) }
    val profiler = InitUtil.initializeProfiler(options)
    assertSame(profiler, options.continuousProfiler)
    assert(profiler is JavaContinuousProfiler)
  }

  @Test
  fun `initialize Converter returns AsyncProfilerProfileConverterProvider if profiling enabled but profiler not yet initialized`() {
    val options = IchnoisCoreOptions().also { it.setProfileSessionSampleRate(1.0) }
    val converter = InitUtil.initializeProfileConverter(options)
    assertSame(converter, options.profilerConverter)
    assert(converter is AsyncProfilerProfileConverterProvider.AsyncProfilerProfileConverter)
  }

  @Test
  fun `initialize profiler uses existing profilingTracesDirPath when set`() {
    val customPath = "/custom/path/to/traces"
    val options =
      IchnoisCoreOptions().also {
        it.setProfileSessionSampleRate(1.0)
        it.profilingTracesDirPath = customPath
      }
    val profiler = InitUtil.initializeProfiler(options)
    assert(profiler is JavaContinuousProfiler)
    assertSame(customPath, options.profilingTracesDirPath)
  }

  @Test
  fun `initialize profiler creates and sets profilingTracesDirPath when null`() {
    val options = IchnoisCoreOptions().also { it.setProfileSessionSampleRate(1.0) }
    val profiler = InitUtil.initializeProfiler(options)
    assert(profiler is JavaContinuousProfiler)
    assertNotNull(options.profilingTracesDirPath)
    assert(options.profilingTracesDirPath!!.contains("ichnois_profiling_traces"))
  }
}
