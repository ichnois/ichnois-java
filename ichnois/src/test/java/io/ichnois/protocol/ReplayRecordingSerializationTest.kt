package io.ichnois.protocol

import io.ichnois.FileFromResources
import io.ichnois.ILogger
import io.ichnois.ReplayRecording
import io.ichnois.protocol.SerializationUtils.deserializeJson
import io.ichnois.protocol.SerializationUtils.serializeToString
import io.ichnois.rrweb.RRWebBreadcrumbEventSerializationTest
import io.ichnois.rrweb.RRWebInteractionEventSerializationTest
import io.ichnois.rrweb.RRWebInteractionMoveEventSerializationTest
import io.ichnois.rrweb.RRWebMetaEventSerializationTest
import io.ichnois.rrweb.RRWebSpanEventSerializationTest
import io.ichnois.rrweb.RRWebVideoEventSerializationTest
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class ReplayRecordingSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      ReplayRecording().apply {
        segmentId = 0
        payload =
          listOf(
            RRWebMetaEventSerializationTest.Fixture().getSut(),
            RRWebVideoEventSerializationTest.Fixture().getSut(),
            RRWebBreadcrumbEventSerializationTest.Fixture().getSut(),
            RRWebSpanEventSerializationTest.Fixture().getSut(),
            RRWebInteractionEventSerializationTest.Fixture().getSut(),
            RRWebInteractionMoveEventSerializationTest.Fixture().getSut(),
          )
      }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = FileFromResources.invoke("json/replay_recording.json").substringBeforeLast("\n")
    val actual = serializeToString(fixture.getSut(), fixture.logger)
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson =
      FileFromResources.invoke("json/replay_recording.json").substringBeforeLast("\n")
    val actual = deserializeJson(expectedJson, ReplayRecording.Deserializer(), fixture.logger)
    val actualJson = serializeToString(actual, fixture.logger)
    assertEquals(expectedJson, actualJson)
  }
}
