package io.ichnois.protocol

import io.ichnois.DateUtils
import io.ichnois.FileFromResources
import io.ichnois.ILogger
import io.ichnois.JsonObjectReader
import io.ichnois.JsonObjectWriter
import io.ichnois.JsonSerializable
import io.ichnois.IchnoisCoreEnvelopeHeader
import io.ichnois.IchnoisCoreIntegrationPackageStorage
import io.ichnois.TraceContextSerializationTest
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class IchnoisCoreEnvelopeHeaderSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      IchnoisCoreEnvelopeHeader(
          IchnoisCoreIdSerializationTest.Fixture().getSut(),
          SdkVersionSerializationTest.Fixture().getSut(),
          TraceContextSerializationTest.Fixture().getSut(),
        )
        .apply { sentAt = DateUtils.getDateTime("2020-02-07T14:16:00.000Z") }
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
    val expected = sanitizedFile("json/ichnois_envelope_header.json")
    val actual = serialize(fixture.getSut())
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/ichnois_envelope_header.json")
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

  private fun deserialize(json: String): IchnoisCoreEnvelopeHeader {
    val reader = JsonObjectReader(StringReader(json))
    return IchnoisCoreEnvelopeHeader.Deserializer().deserialize(reader, fixture.logger)
  }
}
