package io.ichnois.protocol

import io.ichnois.DateUtils
import io.ichnois.ILogger
import io.ichnois.IchnoisCoreIntegrationPackageStorage
import io.ichnois.IchnoisCoreReplayEvent
import io.ichnois.protocol.SerializationUtils.deserializeJson
import io.ichnois.protocol.SerializationUtils.sanitizedFile
import io.ichnois.protocol.SerializationUtils.serializeToString
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class IchnoisCoreReplayEventSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      IchnoisCoreReplayEvent().apply {
        replayId = IchnoisCoreId("f715e1d64ef64ea3ad7744b5230813c3")
        segmentId = 0
        timestamp = DateUtils.getDateTime("1942-07-09T12:55:34.000Z")
        replayStartTimestamp = DateUtils.getDateTime("1942-07-09T12:55:34.000Z")
        urls = listOf("ScreenOne")
        errorIds = listOf("ab3a347a4cc14fd4b4cf1dc56b670c5b")
        traceIds = listOf("340cfef948204549ac07c3b353c81c50")
        segmentNames = listOf("CheckoutActivity")
        IchnoisCoreBaseEventSerializationTest.Fixture().update(this)
        // irrelevant for replay
        serverName = null
        breadcrumbs = null
        extras = null
      }
  }

  private val fixture = Fixture()

  @Before
  fun setup() {
    IchnoisCoreIntegrationPackageStorage.getInstance().clearStorage()
  }

  @After
  fun teardown() {
    IchnoisCoreIntegrationPackageStorage.getInstance().clearStorage()
  }

  @Test
  fun serialize() {
    val expected = sanitizedFile("json/ichnois_replay_event.json")
    val actual = serializeToString(fixture.getSut(), fixture.logger)
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/ichnois_replay_event.json")
    val actual = deserializeJson(expectedJson, IchnoisCoreReplayEvent.Deserializer(), fixture.logger)
    val actualJson = serializeToString(actual, fixture.logger)
    assertEquals(expectedJson, actualJson)
  }
}
