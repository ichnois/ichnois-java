package io.ichnois.protocol

import io.ichnois.DateUtils
import io.ichnois.FileFromResources
import io.ichnois.ILogger
import io.ichnois.JsonObjectReader
import io.ichnois.JsonObjectWriter
import io.ichnois.JsonSerializable
import io.ichnois.IchnoisCoreAttributeType
import io.ichnois.IchnoisCoreLogEvent
import io.ichnois.IchnoisCoreLogEventAttributeValue
import io.ichnois.IchnoisCoreLogEvents
import io.ichnois.IchnoisCoreLogLevel
import io.ichnois.SpanId
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class IchnoisCoreLogsSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      IchnoisCoreLogEvents(
        listOf(
          IchnoisCoreLogEvent(
              IchnoisCoreId("5c1f73d39486827b9e60ceb1fc23277a"),
              DateUtils.dateToSeconds(DateUtils.getDateTime("2004-04-10T18:24:03.000Z")),
              "42e6bd2a-c45e-414d-8066-ed5196fbc686",
              IchnoisCoreLogLevel.INFO,
            )
            .also {
              it.spanId = SpanId("0a53026963414893")
              it.attributes =
                mutableMapOf(
                  "ichnois.sdk.name" to
                    IchnoisCoreLogEventAttributeValue("string", "ichnois.java.spring-boot.jakarta"),
                  "ichnois.environment" to IchnoisCoreLogEventAttributeValue("string", "production"),
                  "custom.array" to IchnoisCoreLogEventAttributeValue("array", listOf("a", "b")),
                  "ichnois.sdk.version" to IchnoisCoreLogEventAttributeValue("string", "8.11.1"),
                  "ichnois.trace.parent_span_id" to
                    IchnoisCoreLogEventAttributeValue("string", "f28b86350e534671"),
                  "custom.boolean" to IchnoisCoreLogEventAttributeValue("boolean", true),
                  "custom.point2" to
                    IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, Point(21, 31)),
                  "custom.double" to IchnoisCoreLogEventAttributeValue("double", 11.12.toDouble()),
                  "custom.point" to IchnoisCoreLogEventAttributeValue("string", Point(20, 30)),
                  "custom.integer" to IchnoisCoreLogEventAttributeValue("integer", 10),
                )
              it.severityNumber = 10
            }
        )
      )
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = sanitizedFile("json/ichnois_logs.json")
    val actual = serialize(fixture.getSut())
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/ichnois_logs.json")
    val actual = deserialize(expectedJson)
    val actualJson = serialize(actual)
    assertEquals(expectedJson, actualJson)
  }

  // Helper

  private fun sanitizedFile(path: String): String =
    FileFromResources.invoke(path).replace(Regex("[\n\r]"), "").replace(" ", "")

  private fun serialize(jsonSerializable: JsonSerializable): String {
    val wrt = StringWriter()
    val jsonWrt = JsonObjectWriter(wrt, 100)
    jsonSerializable.serialize(jsonWrt, fixture.logger)
    return wrt.toString()
  }

  private fun deserialize(json: String): IchnoisCoreLogEvents {
    val reader = JsonObjectReader(StringReader(json))
    return IchnoisCoreLogEvents.Deserializer().deserialize(reader, fixture.logger)
  }

  companion object {
    data class Point(val x: Int, val y: Int) {
      override fun toString(): String = "Point{x:$x,y:$y}-Hello"
    }
  }
}
