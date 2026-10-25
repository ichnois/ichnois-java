package io.ichnois.rrweb

import io.ichnois.ILogger
import io.ichnois.protocol.SerializationUtils.deserializeJson
import io.ichnois.protocol.SerializationUtils.sanitizedFile
import io.ichnois.protocol.SerializationUtils.serializeToString
import io.ichnois.rrweb.RRWebEventType.Meta
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class RRWebMetaEventSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      RRWebMetaEvent().apply {
        href = "https://example.com"
        height = 1920
        width = 1080
        type = Meta
        timestamp = 1234567890
      }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = sanitizedFile("json/rrweb_meta_event.json")
    val actual = serializeToString(fixture.getSut(), fixture.logger)
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/rrweb_meta_event.json")
    val actual = deserializeJson(expectedJson, RRWebMetaEvent.Deserializer(), fixture.logger)
    val actualJson = serializeToString(actual, fixture.logger)
    assertEquals(expectedJson, actualJson)
  }
}
