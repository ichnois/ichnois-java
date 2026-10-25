package io.ichnois.protocol

import io.ichnois.FileFromResources
import io.ichnois.ILogger
import io.ichnois.JsonObjectReader
import io.ichnois.JsonObjectWriter
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class IchnoisCoreIdSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() = IchnoisCoreId("afcb46b1140ade5187c4bbb5daa804df")
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expectedJson = sanitizedFile("json/ichnois_id.json")
    val actualJson = serialize(fixture.getSut())
    assertEquals(expectedJson, actualJson)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/ichnois_id.json")
    val actual = deserialize(expectedJson)
    assertEquals(fixture.getSut(), actual)
  }

  // Helper

  private fun sanitizedFile(path: String): String =
    FileFromResources.invoke(path).replace(Regex("[\n\r]"), "").replace(" ", "")

  private fun serialize(src: IchnoisCoreId): String {
    val wrt = StringWriter()
    val jsonWrt = JsonObjectWriter(wrt, 100)
    jsonWrt.beginObject()
    jsonWrt.name("ichnois_id")
    src.serialize(jsonWrt, fixture.logger)
    jsonWrt.endObject()
    return wrt.toString()
  }

  private fun deserialize(json: String): IchnoisCoreId {
    val reader = JsonObjectReader(StringReader(json))
    reader.beginObject()
    reader.nextName()
    return IchnoisCoreId.Deserializer().deserialize(reader, fixture.logger)
  }
}
