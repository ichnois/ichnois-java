package io.ichnois.rrweb

import io.ichnois.ILogger
import io.ichnois.JsonDeserializer
import io.ichnois.JsonSerializable
import io.ichnois.ObjectReader
import io.ichnois.ObjectWriter
import io.ichnois.protocol.SerializationUtils.deserializeJson
import io.ichnois.protocol.SerializationUtils.sanitizedFile
import io.ichnois.protocol.SerializationUtils.serializeToString
import io.ichnois.rrweb.RRWebEventType.Custom
import io.ichnois.vendor.gson.stream.JsonToken
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class RRWebEventSerializationTest {
  /** Make subclass, as `RRWebEvent` initializers are protected. */
  class Sut : RRWebEvent(), JsonSerializable {
    override fun serialize(writer: ObjectWriter, logger: ILogger) {
      writer.beginObject()
      Serializer().serialize(this, writer, logger)
      writer.endObject()
    }

    class Deserializer : JsonDeserializer<Sut> {
      override fun deserialize(reader: ObjectReader, logger: ILogger): Sut {
        val sut = Sut()
        reader.beginObject()

        val baseEventDeserializer = RRWebEvent.Deserializer()
        do {
          val nextName = reader.nextName()
          baseEventDeserializer.deserializeValue(sut, nextName, reader, logger)
        } while (reader.hasNext() && reader.peek() == JsonToken.NAME)
        reader.endObject()
        return sut
      }
    }
  }

  class Fixture {
    val logger = mock<ILogger>()

    fun update(rrWebEvent: RRWebEvent) {
      rrWebEvent.apply {
        type = Custom
        timestamp = 9999999
      }
    }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = sanitizedFile("json/rrweb_event.json")
    val sut = Sut().apply { fixture.update(this) }
    val actual = serializeToString(sut, fixture.logger)

    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/rrweb_event.json")
    val actual = deserializeJson(expectedJson, Sut.Deserializer(), fixture.logger)
    val actualJson = serializeToString(actual, fixture.logger)

    assertEquals(expectedJson, actualJson)
  }
}
