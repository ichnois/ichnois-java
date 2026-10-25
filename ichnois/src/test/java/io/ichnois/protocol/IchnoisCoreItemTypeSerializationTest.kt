package io.ichnois.protocol

import io.ichnois.ILogger
import io.ichnois.JsonObjectReader
import io.ichnois.JsonObjectWriter
import io.ichnois.IchnoisCoreItemType
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class IchnoisCoreItemTypeSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    assertEquals(serialize(IchnoisCoreItemType.Session), json("session"))
    assertEquals(serialize(IchnoisCoreItemType.Event), json("event"))
    assertEquals(serialize(IchnoisCoreItemType.UserFeedback), json("user_report"))
    assertEquals(serialize(IchnoisCoreItemType.Attachment), json("attachment"))
    assertEquals(serialize(IchnoisCoreItemType.Transaction), json("transaction"))
    assertEquals(serialize(IchnoisCoreItemType.Profile), json("profile"))
    assertEquals(serialize(IchnoisCoreItemType.ProfileChunk), json("profile_chunk"))
    assertEquals(serialize(IchnoisCoreItemType.ClientReport), json("client_report"))
    assertEquals(serialize(IchnoisCoreItemType.ReplayEvent), json("replay_event"))
    assertEquals(serialize(IchnoisCoreItemType.ReplayRecording), json("replay_recording"))
    assertEquals(serialize(IchnoisCoreItemType.ReplayVideo), json("replay_video"))
    assertEquals(serialize(IchnoisCoreItemType.CheckIn), json("check_in"))
    assertEquals(serialize(IchnoisCoreItemType.Feedback), json("feedback"))
    assertEquals(serialize(IchnoisCoreItemType.Span), json("span"))
    assertEquals(serialize(IchnoisCoreItemType.TraceMetric), json("trace_metric"))
  }

  @Test
  fun deserialize() {
    assertEquals(deserialize(json("session")), IchnoisCoreItemType.Session)
    assertEquals(deserialize(json("event")), IchnoisCoreItemType.Event)
    assertEquals(deserialize(json("user_report")), IchnoisCoreItemType.UserFeedback)
    assertEquals(deserialize(json("attachment")), IchnoisCoreItemType.Attachment)
    assertEquals(deserialize(json("transaction")), IchnoisCoreItemType.Transaction)
    assertEquals(deserialize(json("profile")), IchnoisCoreItemType.Profile)
    assertEquals(deserialize(json("profile_chunk")), IchnoisCoreItemType.ProfileChunk)
    assertEquals(deserialize(json("client_report")), IchnoisCoreItemType.ClientReport)
    assertEquals(deserialize(json("replay_event")), IchnoisCoreItemType.ReplayEvent)
    assertEquals(deserialize(json("replay_recording")), IchnoisCoreItemType.ReplayRecording)
    assertEquals(deserialize(json("replay_video")), IchnoisCoreItemType.ReplayVideo)
    assertEquals(deserialize(json("check_in")), IchnoisCoreItemType.CheckIn)
    assertEquals(deserialize(json("feedback")), IchnoisCoreItemType.Feedback)
    assertEquals(deserialize(json("span")), IchnoisCoreItemType.Span)
    assertEquals(deserialize(json("trace_metric")), IchnoisCoreItemType.TraceMetric)
  }

  private fun json(type: String): String = "{\"type\":\"${type}\"}"

  private fun serialize(src: IchnoisCoreItemType): String {
    val wrt = StringWriter()
    val jsonWrt = JsonObjectWriter(wrt, 100)
    jsonWrt.beginObject()
    jsonWrt.name("type")
    src.serialize(jsonWrt, fixture.logger)
    jsonWrt.endObject()
    return wrt.toString()
  }

  private fun deserialize(json: String): IchnoisCoreItemType {
    val reader = JsonObjectReader(StringReader(json))
    reader.beginObject()
    reader.nextName()
    return IchnoisCoreItemType.Deserializer().deserialize(reader, fixture.logger)
  }
}
