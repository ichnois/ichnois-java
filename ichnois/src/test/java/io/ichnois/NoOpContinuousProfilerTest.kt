package io.ichnois

import io.ichnois.protocol.IchnoisCoreId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.mockito.kotlin.mock

class NoOpContinuousProfilerTest {
  private var profiler = NoOpContinuousProfiler.getInstance()

  @Test fun `start does not throw`() = profiler.startProfiler(mock(), mock())

  @Test fun `stop does not throw`() = profiler.stopProfiler(mock())

  @Test
  fun `isRunning returns false`() {
    assertFalse(profiler.isRunning)
  }

  @Test fun `close does not throw`() = profiler.close(true)

  @Test
  fun `getProfilerId returns Empty IchnoisCoreId`() {
    assertEquals(profiler.profilerId, IchnoisCoreId.EMPTY_ID)
  }

  @Test
  fun `getChunkId returns Empty IchnoisCoreId`() {
    assertEquals(profiler.chunkId, IchnoisCoreId.EMPTY_ID)
  }

  @Test
  fun `reevaluateSampling does not throw`() {
    profiler.reevaluateSampling()
  }
}
