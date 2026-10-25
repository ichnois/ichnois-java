package io.ichnois.rrweb

import io.ichnois.ILogger
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreReplayOptions
import io.ichnois.IchnoisCoreReplayOptions.IchnoisCoreReplayQuality.LOW
import io.ichnois.protocol.SdkVersion
import io.ichnois.protocol.SerializationUtils
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class RRWebOptionsEventSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      RRWebOptionsEvent(
          IchnoisCoreOptions().apply {
            sdkVersion = SdkVersion("ichnois.java", "7.19.1")
            sessionReplay.sessionSampleRate = 0.5
            sessionReplay.onErrorSampleRate = 0.1
            sessionReplay.quality = LOW
            sessionReplay.unmaskViewClasses.add("com.example.MyClass")
            sessionReplay.maskViewClasses.clear()
          }
        )
        .apply { timestamp = 12345678901 }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = SerializationUtils.sanitizedFile("json/rrweb_options_event.json")
    val actual = SerializationUtils.serializeToString(fixture.getSut(), fixture.logger)
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = SerializationUtils.sanitizedFile("json/rrweb_options_event.json")
    val actual =
      SerializationUtils.deserializeJson(
        expectedJson,
        RRWebOptionsEvent.Deserializer(),
        fixture.logger,
      )
    val actualJson = SerializationUtils.serializeToString(actual, fixture.logger)
    assertEquals(expectedJson, actualJson)
  }

  @Test
  fun `network detail fields are not included when networkDetailAllowUrls is empty`() {
    val options =
      IchnoisCoreOptions().apply {
        sessionReplay.setNetworkDetailAllowUrls(emptyList())

        // Any config is ignored when no allowUrls are specified.
        sessionReplay.setNetworkDetailDenyUrls(listOf("https://internal.example.com/*"))
        sessionReplay.setNetworkRequestHeaders(listOf("Authorization", "X-Custom"))
        sessionReplay.setNetworkResponseHeaders(listOf("X-RateLimit", "Content-Type"))
      }
    val event = RRWebOptionsEvent(options)

    val payload = event.optionsPayload
    assertFalse(payload.containsKey("networkDetailAllowUrls"))
    assertFalse(payload.containsKey("networkDetailDenyUrls"))
    assertFalse(payload.containsKey("networkRequestHeaders"))
    assertFalse(payload.containsKey("networkResponseHeaders"))
    assertFalse(payload.containsKey("networkCaptureBodies"))
    assertEquals(false, payload["networkDetailHasUrls"])
  }

  @Test
  fun `networkDetailAllowUrls and headers are included when networkDetailAllowUrls is configured`() {
    val options =
      IchnoisCoreOptions().apply {
        sessionReplay.setNetworkDetailAllowUrls(listOf("https://api.example.com/*"))
        sessionReplay.setNetworkRequestHeaders(listOf("Authorization", "X-Custom"))
        sessionReplay.setNetworkResponseHeaders(listOf("X-RateLimit", "Content-Type"))
      }
    val event = RRWebOptionsEvent(options)

    val payload = event.optionsPayload
    assertTrue(payload.containsKey("networkDetailAllowUrls"))
    assertTrue(payload.containsKey("networkRequestHeaders"))
    assertTrue(payload.containsKey("networkResponseHeaders"))
    assertEquals(true, payload["networkDetailHasUrls"])
    assertEquals(
      listOf("https://api.example.com/*"),
      (payload["networkDetailAllowUrls"] as List<String>),
    )
    assertEquals(
      (IchnoisCoreReplayOptions.getNetworkDetailsDefaultHeaders() + listOf("Authorization", "X-Custom"))
        .toSet(),
      (payload["networkRequestHeaders"] as List<String>).toSet(),
    )
    assertEquals(
      (IchnoisCoreReplayOptions.getNetworkDetailsDefaultHeaders() + listOf("X-RateLimit")).toSet(),
      (payload["networkResponseHeaders"] as List<String>).toSet(),
    )
  }

  @Test
  fun `networkDetailDenyUrls are included when networkDetailAllowUrls is configured`() {
    val options =
      IchnoisCoreOptions().apply {
        sessionReplay.setNetworkDetailAllowUrls(listOf("https://api.example.com/*"))
        sessionReplay.setNetworkDetailDenyUrls(listOf("https://internal.example.com/*"))
      }
    val event = RRWebOptionsEvent(options)

    val payload = event.optionsPayload
    assertTrue(payload.containsKey("networkDetailAllowUrls"))
    assertTrue(payload.containsKey("networkDetailDenyUrls"))
    assertEquals(
      listOf("https://api.example.com/*"),
      (payload["networkDetailAllowUrls"] as List<String>),
    )
    assertEquals(
      listOf("https://internal.example.com/*"),
      (payload["networkDetailDenyUrls"] as List<String>),
    )
  }

  @Test
  fun `networkCaptureBodies is included when networkDetailAllowUrls is configured`() {
    val options =
      IchnoisCoreOptions().apply {
        sessionReplay.setNetworkDetailAllowUrls(listOf("https://api.example.com/*"))
        sessionReplay.setNetworkCaptureBodies(false)
      }
    val event = RRWebOptionsEvent(options)

    val payload = event.optionsPayload
    assertTrue(payload.containsKey("networkCaptureBodies"))
    assertEquals(false, payload["networkCaptureBodies"])
  }

  @Test
  fun `default networkCaptureBodies is included when networkDetailAllowUrls is configured`() {
    val options =
      IchnoisCoreOptions().apply {
        sessionReplay.setNetworkDetailAllowUrls(listOf("https://api.example.com/*"))
      }
    val event = RRWebOptionsEvent(options)

    val payload = event.optionsPayload
    assertTrue(payload.containsKey("networkCaptureBodies"))
    assertEquals(true, payload["networkCaptureBodies"])
  }

  @Test
  fun `default network request and response headers are included when networkDetailAllowUrls is configured but no custom headers set`() {
    val options =
      IchnoisCoreOptions().apply {
        sessionReplay.setNetworkDetailAllowUrls(listOf("https://api.example.com/*"))
        // No custom headers set, should use defaults only
      }
    val event = RRWebOptionsEvent(options)

    val payload = event.optionsPayload
    assertTrue(payload.containsKey("networkRequestHeaders"))
    assertTrue(payload.containsKey("networkResponseHeaders"))
    assertEquals(
      IchnoisCoreReplayOptions.getNetworkDetailsDefaultHeaders().toSet(),
      (payload["networkRequestHeaders"] as List<String>).toSet(),
    )
    assertEquals(
      IchnoisCoreReplayOptions.getNetworkDetailsDefaultHeaders().toSet(),
      (payload["networkResponseHeaders"] as List<String>).toSet(),
    )
  }
}
