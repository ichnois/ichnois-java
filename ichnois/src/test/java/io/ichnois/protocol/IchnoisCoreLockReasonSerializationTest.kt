package io.ichnois.protocol

import io.ichnois.ILogger
import io.ichnois.IchnoisCoreLockReason
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class IchnoisCoreLockReasonSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      IchnoisCoreLockReason().apply {
        address = "0x0d3a2f0a"
        type = IchnoisCoreLockReason.BLOCKED
        threadId = 11
        className = "Object"
        packageName = "java.lang"
      }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = SerializationUtils.sanitizedFile("json/ichnois_lock_reason.json")
    val actual = SerializationUtils.serializeToString(fixture.getSut(), fixture.logger)

    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = SerializationUtils.sanitizedFile("json/ichnois_lock_reason.json")
    val actual =
      SerializationUtils.deserializeJson<IchnoisCoreLockReason>(
        expectedJson,
        IchnoisCoreLockReason.Deserializer(),
        fixture.logger,
      )
    val actualJson = SerializationUtils.serializeToString(actual, fixture.logger)

    assertEquals(expectedJson, actualJson)
  }
}
