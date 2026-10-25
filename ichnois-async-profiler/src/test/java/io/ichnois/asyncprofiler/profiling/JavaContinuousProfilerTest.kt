package io.ichnois.asyncprofiler.profiling

import io.ichnois.DataCategory
import io.ichnois.ILogger
import io.ichnois.IScopes
import io.ichnois.ProfileLifecycle
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreLevel
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreTracer
import io.ichnois.TracesSampler
import io.ichnois.TransactionContext
import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.test.DeferredExecutorService
import io.ichnois.transport.RateLimiter
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.mockito.ArgumentMatchers.startsWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class JavaContinuousProfilerTest {

  private val fixture = Fixture()

  private class Fixture {
    private val mockDsn = "http://key@localhost/proj"
    val executor = DeferredExecutorService()
    val mockedIchnoisCore = Mockito.mockStatic(IchnoisCore::class.java)
    val mockLogger = mock<ILogger>()
    val mockTracesSampler = mock<TracesSampler>()

    val scopes: IScopes = mock()

    lateinit var transaction1: IchnoisCoreTracer
    lateinit var transaction2: IchnoisCoreTracer
    lateinit var transaction3: IchnoisCoreTracer

    val options =
      spy(IchnoisCoreOptions()).apply {
        dsn = mockDsn
        profilesSampleRate = 1.0
        isDebug = true
        setLogger(mockLogger)
      }

    init {
      whenever(mockTracesSampler.sampleSessionProfile(any())).thenReturn(true)
    }

    fun getSut(optionConfig: ((options: IchnoisCoreOptions) -> Unit) = {}): JavaContinuousProfiler {
      options.executorService = executor
      optionConfig(options)
      whenever(scopes.options).thenReturn(options)
      transaction1 = IchnoisCoreTracer(TransactionContext("", ""), scopes)
      transaction2 = IchnoisCoreTracer(TransactionContext("", ""), scopes)
      transaction3 = IchnoisCoreTracer(TransactionContext("", ""), scopes)
      return JavaContinuousProfiler(
        options.logger,
        options.profilingTracesDirPath,
        options.profilingTracesHz,
        options.executorService,
      )
    }
  }

  @BeforeTest
  fun `set up`() {
    // Profiler doesn't start if the folder doesn't exists.
    // Usually it's generated when calling IchnoisCore.init, but for tests we can create it manually.

    fixture.options.cacheDirPath = "tmp"
    File(fixture.options.profilingTracesDirPath!!).mkdirs()

    IchnoisCore.setCurrentScopes(fixture.scopes)

    fixture.mockedIchnoisCore.`when`<Any> { IchnoisCore.getCurrentScopes() }.thenReturn(fixture.scopes)
    fixture.mockedIchnoisCore.`when`<Any> { IchnoisCore.close() }.then { fixture.executor.runAll() }
  }

  @AfterTest
  fun clear() {
    fixture.options.profilingTracesDirPath?.let { File(it).deleteRecursively() }
    fixture.options.cacheDirPath?.let { File(it).deleteRecursively() }

    IchnoisCore.stopProfiler()
    IchnoisCore.close()
    fixture.mockedIchnoisCore.close()
  }

  @Test
  fun `isRunning reflects profiler status`() {
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertTrue(profiler.isRunning)
    profiler.stopProfiler(ProfileLifecycle.MANUAL)
    fixture.executor.runAll()
    assertFalse(profiler.isRunning)
  }

  @Test
  fun `stopProfiler stops the profiler after chunk is finished`() {
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertTrue(profiler.isRunning)
    // We are scheduling the profiler to stop at the end of the chunk, so it should still be running
    profiler.stopProfiler(ProfileLifecycle.MANUAL)
    assertFalse(profiler.isRunning)
  }

  @Test
  fun `profiler multiple starts are ignored in manual mode`() {
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertTrue(profiler.isRunning)
    verify(fixture.mockLogger, never())
      .log(eq(IchnoisCoreLevel.DEBUG), eq("Profiler is already running."))
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    verify(fixture.mockLogger).log(eq(IchnoisCoreLevel.DEBUG), eq("Profiler is already running."))
    assertTrue(profiler.isRunning)
    assertEquals(0, profiler.rootSpanCounter)
  }

  @Test
  fun `profiler multiple starts are accepted in trace mode`() {
    val profiler = fixture.getSut()

    // rootSpanCounter is incremented when the profiler starts in trace mode
    assertEquals(0, profiler.rootSpanCounter)
    profiler.startProfiler(ProfileLifecycle.TRACE, fixture.mockTracesSampler)
    assertEquals(1, profiler.rootSpanCounter)
    assertTrue(profiler.isRunning)
    profiler.startProfiler(ProfileLifecycle.TRACE, fixture.mockTracesSampler)
    verify(fixture.mockLogger, never())
      .log(eq(IchnoisCoreLevel.DEBUG), eq("Profiler is already running."))
    assertTrue(profiler.isRunning)
    assertEquals(2, profiler.rootSpanCounter)

    // rootSpanCounter is decremented when the profiler stops in trace mode, and keeps running until
    // rootSpanCounter is 0
    profiler.stopProfiler(ProfileLifecycle.TRACE)
    fixture.executor.runAll()
    assertEquals(1, profiler.rootSpanCounter)
    assertTrue(profiler.isRunning)

    // only when rootSpanCounter is 0 the profiler stops
    profiler.stopProfiler(ProfileLifecycle.TRACE)
    fixture.executor.runAll()
    assertEquals(0, profiler.rootSpanCounter)
    assertFalse(profiler.isRunning)
  }

  @Test
  fun `profiler logs a warning on start if not sampled`() {
    val profiler = fixture.getSut()
    whenever(fixture.mockTracesSampler.sampleSessionProfile(any())).thenReturn(false)
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertFalse(profiler.isRunning)
    verify(fixture.mockLogger)
      .log(eq(IchnoisCoreLevel.DEBUG), eq("Profiler was not started due to sampling decision."))
  }

  @Test
  fun `profiler evaluates sessionSampleRate only the first time`() {
    val profiler = fixture.getSut()
    verify(fixture.mockTracesSampler, never()).sampleSessionProfile(any())
    // The first time the profiler is started, the sessionSampleRate is evaluated
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    verify(fixture.mockTracesSampler, times(1)).sampleSessionProfile(any())
    // Then, the sessionSampleRate is not evaluated again
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    verify(fixture.mockTracesSampler, times(1)).sampleSessionProfile(any())
  }

  @Test
  fun `when reevaluateSampling, profiler evaluates sessionSampleRate on next start`() {
    val profiler = fixture.getSut()
    verify(fixture.mockTracesSampler, never()).sampleSessionProfile(any())
    // The first time the profiler is started, the sessionSampleRate is evaluated
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    verify(fixture.mockTracesSampler, times(1)).sampleSessionProfile(any())
    // When reevaluateSampling is called, the sessionSampleRate is not evaluated immediately
    profiler.reevaluateSampling()
    verify(fixture.mockTracesSampler, times(1)).sampleSessionProfile(any())
    // Then, when the profiler starts again, the sessionSampleRate is reevaluated
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    verify(fixture.mockTracesSampler, times(2)).sampleSessionProfile(any())
  }

  @Test
  fun `profiler ignores profilesSampleRate`() {
    val profiler = fixture.getSut { it.profilesSampleRate = 0.0 }
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertTrue(profiler.isRunning)
    profiler.close(true)
  }

  @Test
  fun `profiler evaluates profilingTracesDirPath options only on first start`() {
    // We create the profiler, and nothing goes wrong
    val profiler = fixture.getSut { it.cacheDirPath = null }
    verify(fixture.mockLogger, never())
      .log(
        IchnoisCoreLevel.WARNING,
        "Disabling profiling because no profiling traces dir path is defined in options.",
      )

    // Regardless of how many times the profiler is started, the option is evaluated and logged only
    // once
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    verify(fixture.mockLogger, times(1))
      .log(
        IchnoisCoreLevel.WARNING,
        "Disabling profiling because no profiling traces dir path is defined in options.",
      )
  }

  @Test
  fun `profiler evaluates profilingTracesHz options only on first start`() {
    // We create the profiler, and nothing goes wrong
    val profiler = fixture.getSut { it.profilingTracesHz = 0 }
    verify(fixture.mockLogger, never())
      .log(IchnoisCoreLevel.WARNING, "Disabling profiling because trace rate is set to %d", 0)

    // Regardless of how many times the profiler is started, the option is evaluated and logged only
    // once
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    verify(fixture.mockLogger, times(1))
      .log(IchnoisCoreLevel.WARNING, "Disabling profiling because trace rate is set to %d", 0)
  }

  @Test
  fun `profiler on tracesDirPath null`() {
    val profiler = fixture.getSut { it.cacheDirPath = null }
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertFalse(profiler.isRunning)
  }

  @Test
  fun `profiler on tracesDirPath empty`() {
    val profiler = fixture.getSut { it.cacheDirPath = "" }
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertFalse(profiler.isRunning)
  }

  @Test
  fun `profiler on profilingTracesHz 0`() {
    val profiler = fixture.getSut { it.profilingTracesHz = 0 }
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertFalse(profiler.isRunning)
  }

  @Test
  fun `profiler does not throw if traces cannot be written to disk`() {
    val profiler = fixture.getSut { File(it.profilingTracesDirPath!!).setWritable(false) }
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    profiler.stopProfiler(ProfileLifecycle.MANUAL)
    fixture.executor.runAll()
    // We assert that no trace files are written
    assertTrue(File(fixture.options.profilingTracesDirPath!!).list()!!.isEmpty())
    val expectedPath = fixture.options.profilingTracesDirPath
    verify(fixture.mockLogger)
      .log(
        eq(IchnoisCoreLevel.WARNING),
        eq(
          "Disabling profiling because traces directory is not writable or does not exist: %s (writable=%b, exists=%b)"
        ),
        eq(expectedPath),
        eq(false),
        eq(true),
      )
  }

  @Test
  fun `profiler stops and restart for each chunk`() {
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertTrue(profiler.isRunning)

    fixture.executor.runAll()
    verify(fixture.mockLogger)
      .log(eq(IchnoisCoreLevel.DEBUG), eq("Profile chunk finished. Starting a new one."))
    assertTrue(profiler.isRunning)

    fixture.executor.runAll()
    verify(fixture.mockLogger, times(2))
      .log(eq(IchnoisCoreLevel.DEBUG), eq("Profile chunk finished. Starting a new one."))
    assertTrue(profiler.isRunning)
  }

  @Test
  fun `profiler sends chunk on each restart`() {
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertTrue(profiler.isRunning)
    // We run the executor service to trigger the profiler restart (chunk finish)
    fixture.executor.runAll()
    verify(fixture.scopes, never()).captureProfileChunk(any())
    // Now the executor is used to send the chunk
    fixture.executor.runAll()
    verify(fixture.scopes).captureProfileChunk(any())
  }

  @Test
  fun `profiler sends another chunk on stop`() {
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertTrue(profiler.isRunning)
    // We run the executor service to trigger the profiler restart (chunk finish)
    fixture.executor.runAll()
    // At this point the chunk has been submitted to the executor, but yet to be sent
    verify(fixture.scopes, never()).captureProfileChunk(any())
    profiler.stopProfiler(ProfileLifecycle.MANUAL)
    // We stop the profiler, which should send both the first and last chunk
    fixture.executor.runAll()
    verify(fixture.scopes, times(2)).captureProfileChunk(any())
  }

  @Test
  fun `close without terminating stops all profiles after chunk is finished`() {
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.TRACE, fixture.mockTracesSampler)
    assertTrue(profiler.isRunning)
    // We are closing the profiler, which should stop all profiles after the chunk is finished
    profiler.close(false)
    assertFalse(profiler.isRunning)
    // However, close() already resets the rootSpanCounter
    assertEquals(0, profiler.rootSpanCounter)
  }

  @Test
  fun `profiler can be stopped and restarted`() {
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertTrue(profiler.isRunning)

    profiler.stopProfiler(ProfileLifecycle.MANUAL)
    fixture.executor.runAll()
    assertFalse(profiler.isRunning)

    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    fixture.executor.runAll()

    assertTrue(profiler.isRunning)
    verify(fixture.mockLogger, never())
      .log(eq(IchnoisCoreLevel.WARNING), startsWith("JFR file is invalid or empty"), any(), any(), any())
  }

  @Test
  fun `profiler does not send chunks after close`() {
    val profiler = fixture.getSut()
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertTrue(profiler.isRunning)

    // We close the profiler, which should prevent sending additional chunks
    profiler.close(true)

    // The executor used to send the chunk doesn't do anything
    fixture.executor.runAll()
    verify(fixture.scopes, never()).captureProfileChunk(any())
  }

  @Test
  fun `profiler stops when rate limited`() {
    val profiler = fixture.getSut()
    val rateLimiter = mock<RateLimiter>()
    whenever(rateLimiter.isActiveForCategory(DataCategory.ProfileChunk)).thenReturn(true)

    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertTrue(profiler.isRunning)

    // If the SDK is rate limited, the profiler should stop
    profiler.onRateLimitChanged(rateLimiter)
    assertFalse(profiler.isRunning)
    assertEquals(IchnoisCoreId.EMPTY_ID, profiler.profilerId)
    verify(fixture.mockLogger)
      .log(eq(IchnoisCoreLevel.WARNING), eq("SDK is rate limited. Stopping profiler."))
  }

  @Test
  fun `profiler does not start when rate limited`() {
    val profiler = fixture.getSut()
    val rateLimiter = mock<RateLimiter>()
    whenever(rateLimiter.isActiveForCategory(DataCategory.ProfileChunk)).thenReturn(true)
    whenever(fixture.scopes.rateLimiter).thenReturn(rateLimiter)

    // If the SDK is rate limited, the profiler should never start
    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertFalse(profiler.isRunning)
    assertEquals(IchnoisCoreId.EMPTY_ID, profiler.profilerId)
    verify(fixture.mockLogger)
      .log(eq(IchnoisCoreLevel.WARNING), eq("SDK is rate limited. Stopping profiler."))
  }

  @Test
  fun `profiler does not start when filename contains invalid characters`() {
    val profiler = fixture.getSut { it.profilingTracesDirPath = "," }

    profiler.startProfiler(ProfileLifecycle.MANUAL, fixture.mockTracesSampler)
    assertFalse(profiler.isRunning)
    assertEquals(IchnoisCoreId.EMPTY_ID, profiler.profilerId)
    verify(fixture.mockLogger)
      .log(
        eq(IchnoisCoreLevel.WARNING),
        eq("Disabling profiling because traces directory path contains invalid character: %s"),
        eq(","),
      )
  }

  fun withMockScopes(closure: () -> Unit) =
    Mockito.mockStatic(IchnoisCore::class.java).use {
      it.`when`<Any> { IchnoisCore.getCurrentScopes() }.thenReturn(fixture.scopes)
      closure.invoke()
    }
}
