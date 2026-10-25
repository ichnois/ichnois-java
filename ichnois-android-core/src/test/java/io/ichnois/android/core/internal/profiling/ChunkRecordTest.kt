package io.ichnois.android.core.internal.profiling

import io.ichnois.IchnoisCoreLongDate
import io.ichnois.profiling.ProfileRecordingState
import io.ichnois.protocol.IchnoisCoreId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChunkRecordTest {
  private val chunkStart = IchnoisCoreLongDate(1000)
  private val chunkEnd = IchnoisCoreLongDate(2000)

  private fun getSut(withEnd: Boolean = true): ChunkRecord =
    ChunkRecord(IchnoisCoreId(), chunkStart).apply { if (withEnd) setEndTimestamp(chunkEnd) }

  @Test
  fun `a new chunk has an unknown state`() {
    assertEquals(ProfileRecordingState.UNKNOWN, getSut().recordingState)
  }

  @Test
  fun `not recorded is final`() {
    val chunk = getSut()

    chunk.recordingState = ProfileRecordingState.NOT_RECORDED
    chunk.recordingState = ProfileRecordingState.RECORDED

    assertEquals(ProfileRecordingState.NOT_RECORDED, chunk.recordingState)
  }

  @Test
  fun `a recorded chunk can still turn out to be not recorded`() {
    val chunk = getSut()

    chunk.recordingState = ProfileRecordingState.RECORDED
    chunk.recordingState = ProfileRecordingState.NOT_RECORDED

    assertEquals(ProfileRecordingState.NOT_RECORDED, chunk.recordingState)
  }

  @Test
  fun `a chunk has only ended once it has an end timestamp`() {
    assertFalse(getSut(withEnd = false).hasEnded())
    assertTrue(getSut().hasEnded())
  }

  @Test
  fun `a window inside the chunk overlaps`() {
    assertTrue(getSut().overlaps(IchnoisCoreLongDate(1200), IchnoisCoreLongDate(1800)))
  }

  @Test
  fun `a window around the chunk overlaps`() {
    assertTrue(getSut().overlaps(IchnoisCoreLongDate(500), IchnoisCoreLongDate(2500)))
  }

  @Test
  fun `a window ending exactly at the chunk start overlaps`() {
    assertTrue(getSut().overlaps(IchnoisCoreLongDate(500), chunkStart))
  }

  @Test
  fun `a window starting exactly at the chunk end overlaps`() {
    assertTrue(getSut().overlaps(chunkEnd, IchnoisCoreLongDate(2500)))
  }

  @Test
  fun `a window before the chunk does not overlap`() {
    assertFalse(getSut().overlaps(IchnoisCoreLongDate(500), IchnoisCoreLongDate(999)))
  }

  @Test
  fun `a window after the chunk does not overlap`() {
    assertFalse(getSut().overlaps(IchnoisCoreLongDate(2001), IchnoisCoreLongDate(2500)))
  }

  @Test
  fun `a running chunk covers everything from its start on`() {
    val chunk = getSut(withEnd = false)

    assertTrue(chunk.overlaps(IchnoisCoreLongDate(1200), IchnoisCoreLongDate(1200)))
    assertTrue(chunk.overlaps(IchnoisCoreLongDate(9000), IchnoisCoreLongDate(9000)))
    assertFalse(chunk.overlaps(IchnoisCoreLongDate(500), IchnoisCoreLongDate(999)))
  }
}
