package io.ichnois.asyncprofiler.convert

import io.ichnois.DateUtils
import io.ichnois.ILogger
import io.ichnois.IProfileConverter
import io.ichnois.IScope
import io.ichnois.IScopes
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreStackTraceFactory
import io.ichnois.TracesSampler
import io.ichnois.asyncprofiler.provider.AsyncProfilerProfileConverterProvider
import io.ichnois.protocol.profiling.IchnoisCoreProfile
import io.ichnois.test.DeferredExecutorService
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.absoluteValue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

class JfrAsyncProfilerToIchnoisCoreProfileConverterTest {

  private val fixture = Fixture()

  private class Fixture {
    private val mockDsn = "http://key@localhost/proj"
    val executor = DeferredExecutorService()
    val mockedIchnoisCore = Mockito.mockStatic(IchnoisCore::class.java)
    val mockLogger = mock<ILogger>()
    val mockTracesSampler = mock<TracesSampler>()
    val mockStackTraceFactory = mock<IchnoisCoreStackTraceFactory>()

    val scopes: IScopes = mock()
    val scope: IScope = mock()

    val options =
      spy(IchnoisCoreOptions()).apply {
        dsn = mockDsn
        profilesSampleRate = 1.0
        isDebug = true
        setLogger(mockLogger)
        // Set in-app packages for testing
        addInAppInclude("io.ichnois")
        addInAppInclude("com.example")
      }

    init {
      whenever(mockTracesSampler.sampleSessionProfile(any())).thenReturn(true)
      // Setup default in-app behavior for stack trace factory
      whenever(mockStackTraceFactory.isInApp(any())).thenAnswer { invocation ->
        val className = invocation.getArgument<String>(0)
        className.startsWith("io.ichnois") || className.startsWith("com.example")
      }
    }

    fun getSut(optionConfig: ((options: IchnoisCoreOptions) -> Unit) = {}): IProfileConverter? {
      options.executorService = executor
      optionConfig(options)
      whenever(scopes.options).thenReturn(options)
      whenever(scope.options).thenReturn(options)
      return AsyncProfilerProfileConverterProvider().profileConverter
    }
  }

  @BeforeTest
  fun `set up`() {
    IchnoisCore.setCurrentScopes(fixture.scopes)

    fixture.mockedIchnoisCore.`when`<IScopes> { IchnoisCore.getCurrentScopes() }.thenReturn(fixture.scopes)
    fixture.mockedIchnoisCore.`when`<IScope> { IchnoisCore.getGlobalScope() }.thenReturn(fixture.scope)

    // Ensure the global scope returns proper options for the static converter method
    whenever(fixture.scope.options).thenReturn(fixture.options)
  }

  @AfterTest
  fun clear() {
    IchnoisCore.close()
    fixture.mockedIchnoisCore.close()
  }

  @Test
  fun `check number of samples for specific frame`() {
    val file = loadFile("async_profiler_test_sample.jfr")

    val ichnoisProfile = fixture.getSut()!!.convertFromFile(file)
    val tracingFilterFrame =
      ichnoisProfile.frames.filter {
        it.function == "slowFunction" && it.module == "io.ichnois.samples.console.Main"
      }

    val tracingFilterFrameIndexes = tracingFilterFrame.map { ichnoisProfile.frames.indexOf(it) }
    val tracingFilterStacks =
      ichnoisProfile.stacks.filter { it.any { inner -> tracingFilterFrameIndexes.contains(inner) } }
    val tracingFilterStackIds = tracingFilterStacks.map { ichnoisProfile.stacks.indexOf(it) }
    val tracingFilterSamples =
      ichnoisProfile.samples.filter { tracingFilterStackIds.contains(it.stackId) }

    // Sample size base on 101 samples/sec and 5 sec of profiling
    // So expected around 500 samples (with some margin)
    assertTrue(
      tracingFilterSamples.count() >= 500 && tracingFilterSamples.count() <= 600,
      "Expected sample count between 500 and 600, but was ${tracingFilterSamples.count()}",
    )
  }

  @Test
  fun `check number of samples for specific thread`() {
    val file = loadFile("async_profiler_test_sample.jfr")

    val ichnoisProfile = fixture.getSut()!!.convertFromFile(file)
    val mainThread =
      ichnoisProfile.threadMetadata.entries.firstOrNull { it.value.name == "main" }?.key

    val samples = ichnoisProfile.samples.filter { it.threadId == mainThread }

    // Sample size base on 101 samples/sec and 5 sec of profiling
    // So expected around 500 samples (with some margin)
    assertTrue(
      samples.count() >= 500 && samples.count() <= 600,
      "Expected sample count between 500 and 600, but was ${samples.count()}",
    )
  }

  @Test
  fun `check no duplicate frames`() {
    val file = loadFile("async_profiler_test_sample.jfr")
    val ichnoisProfile = fixture.getSut()!!.convertFromFile(file)

    val frameSet = ichnoisProfile.frames.toSet()

    assertEquals(frameSet.size, ichnoisProfile.frames.size)
  }

  @Test
  fun `convertFromFile with valid JFR returns populated IchnoisCoreProfile`() {
    val file = loadFile("async_profiler_test_sample.jfr")

    val ichnoisProfile = fixture.getSut()!!.convertFromFile(file)

    assertNotNull(ichnoisProfile)
    assertValidIchnoisCoreProfile(ichnoisProfile)
  }

  @Test
  fun `convertFromFile parses timestamps correctly`() {
    val file = loadFile("async_profiler_test_sample.jfr")

    val ichnoisProfile = fixture.getSut()!!.convertFromFile(file)

    val samples = ichnoisProfile.samples
    assertTrue(samples.isNotEmpty())

    val minTimestamp = samples.minOf { it.timestamp }
    val maxTimestamp = samples.maxOf { it.timestamp }
    val sampleTimeStamp =
      DateUtils.nanosToDate((maxTimestamp * 1000 * 1000 * 1000).toLong()).toInstant()

    // The sample was recorded around "2025-09-05T08:14:50" in UTC timezone
    val referenceTimestamp = LocalDateTime.parse("2025-09-05T08:14:50").toInstant(ZoneOffset.UTC)
    val between = ChronoUnit.MILLIS.between(sampleTimeStamp, referenceTimestamp).absoluteValue

    assertTrue(between < 5000, "Sample timestamp should be within 5s of reference timestamp")
    assertTrue(maxTimestamp >= minTimestamp, "Max timestamp should be >= min timestamp")
    assertTrue(
      maxTimestamp - minTimestamp <= 10,
      "There should be a max difference of <10s between min and max timestamp",
    )
  }

  @Test
  fun `convertFromFile extracts thread metadata correctly`() {
    val file = loadFile("async_profiler_test_sample.jfr")

    val ichnoisProfile = fixture.getSut()!!.convertFromFile(file)

    val threadMetadata = ichnoisProfile.threadMetadata
    val samples = ichnoisProfile.samples

    assertTrue(threadMetadata.isNotEmpty())

    // Verify thread IDs in samples match thread metadata keys
    val threadIdsFromSamples = samples.map { it.threadId }.toSet()
    threadIdsFromSamples.forEach { threadId ->
      assertTrue(
        threadMetadata.containsKey(threadId),
        "Thread metadata should contain thread ID: $threadId",
      )
    }

    // Verify thread metadata has proper values
    threadMetadata.forEach { (_, metadata) ->
      assertNotNull(metadata.name, "Thread name should not be null")
      assertEquals(0, metadata.priority, "Thread priority should be default (0)")
    }
  }

  @Test
  fun `converter processes frames with complete information`() {
    val file = loadFile("async_profiler_test_sample.jfr")

    val ichnoisProfile = fixture.getSut()!!.convertFromFile(file)

    val frames = ichnoisProfile.frames
    assertTrue(frames.isNotEmpty())

    // Find frames with complete information
    val completeFrames = frames.filter { frame ->
      frame.function != null &&
        frame.module != null &&
        frame.lineno != null &&
        frame.filename != null
    }

    assertTrue(completeFrames.isNotEmpty(), "Should have frames with complete information")
  }

  @Test
  fun `converter marks in-app frames correctly`() {
    val file = loadFile("async_profiler_test_sample.jfr")

    val ichnoisProfile = fixture.getSut()!!.convertFromFile(file)

    val frames = ichnoisProfile.frames

    // Verify system packages are marked as not in-app
    val systemFrames = frames.filter { frame ->
      frame.module?.let {
        it.startsWith("java.") || it.startsWith("sun.") || it.startsWith("jdk.")
      } ?: false
    }

    val inappIchnoisCoreFrames = frames.filter { frame ->
      frame.module?.startsWith("io.ichnois.") ?: false
    }

    val emptyModuleFrames = frames.filter { it.module.isNullOrEmpty() }

    // Verify system classes are not marked as in-app
    systemFrames.forEach { frame ->
      assertFalse(frame.isInApp ?: false, "System classes should not be marked as in app")
    }

    // Verify ichnois classes are marked as in-app
    inappIchnoisCoreFrames.forEach { frame ->
      assertTrue(frame.isInApp ?: false, "IchnoisCore classes should be marked as in app")
    }

    // Verify empty class names are marked as not in-app
    emptyModuleFrames.forEach { frame ->
      assertFalse(frame.isInApp ?: true, "Empty module frame should not be in-app")
    }
  }

  @Test
  fun `converter filters native methods`() {
    val file = loadFile("async_profiler_test_sample.jfr")

    val ichnoisProfile = fixture.getSut()!!.convertFromFile(file)

    // Native methods should be filtered out during frame creation
    // Verify no frames have characteristics of native methods
    ichnoisProfile.frames.forEach { frame ->
      // Native methods would have been skipped, so no frame should indicate native
      assertTrue(
        frame.filename?.isNotEmpty() == true || frame?.module?.isNotEmpty() == true,
        "Frame should have some non-native information",
      )
    }
  }

  @Test(expected = IOException::class)
  fun `convertFromFile with non-existent file throws IOException`() {
    val nonExistentFile = "/non/existent/file.jfr"

    JfrAsyncProfilerToIchnoisCoreProfileConverter.convertFromFileStatic(nonExistentFile)
  }

  private fun loadFile(path: String): String = javaClass.classLoader!!.getResource(path)!!.file

  private fun assertValidIchnoisCoreProfile(profile: IchnoisCoreProfile) {
    assertNotNull(profile.samples, "Samples should not be null")
    assertNotNull(profile.frames, "Frames should not be null")
    assertNotNull(profile.stacks, "Stacks should not be null")
    assertNotNull(profile.threadMetadata, "Thread metadata should not be null")
  }
}
