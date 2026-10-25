package io.ichnois.protocol

import io.ichnois.FileFromResources
import io.ichnois.ILogger
import io.ichnois.JsonObjectReader
import io.ichnois.JsonObjectWriter
import io.ichnois.JsonSerializable
import io.ichnois.protocol.profiling.IchnoisCoreProfile
import io.ichnois.protocol.profiling.IchnoisCoreSample
import io.ichnois.protocol.profiling.IchnoisCoreThreadMetadata
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class IchnoisCoreProfileSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()

    fun getSut() =
      IchnoisCoreProfile().apply {
        samples =
          listOf(
            IchnoisCoreSample().apply {
              timestamp = 1753439655.387274
              threadId = "57"
              stackId = 0
            },
            IchnoisCoreSample().apply {
              timestamp = 1753439655.415672
              threadId = "57"
              stackId = 1
            },
          )
        stacks = listOf(listOf(0, 1, 2), listOf(3, 4))
        frames =
          listOf(
            IchnoisCoreStackFrame().apply {
              filename = "sun.nio.ch.Net"
              function = "accept"
              module = "sun.nio.ch.Net"
            },
            IchnoisCoreStackFrame().apply {
              filename = "org.apache.tomcat.util.net.NioEndpoint"
              function = "serverSocketAccept"
              module = "org.apache.tomcat.util.net.NioEndpoint"
              lineno = 519
            },
            IchnoisCoreStackFrame().apply {
              filename = "java.lang.Thread"
              function = "run"
              module = "java.lang.Thread"
              lineno = 840
            },
            IchnoisCoreStackFrame().apply {
              filename = "io.ichnois.samples.spring.boot.jakarta.quartz.SampleJob"
              function = "execute"
              module = "io.ichnois.samples.spring.boot.jakarta.quartz.SampleJob"
              lineno = 14
              isInApp = true
            },
            IchnoisCoreStackFrame().apply {
              filename = ""
              function = "Unsafe_Park"
              module = ""
              isInApp = false
            },
          )
        threadMetadata =
          mapOf(
            "57" to
              IchnoisCoreThreadMetadata().apply {
                name = "http-nio-8080-Acceptor"
                priority = 0
              }
          )
      }
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    val expected = sanitizedFile("json/ichnois_profile.json")
    val actual = serialize(fixture.getSut())
    assertEquals(expected, actual)
  }

  @Test
  fun deserialize() {
    val expectedJson = sanitizedFile("json/ichnois_profile.json")
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

  private fun deserialize(json: String): IchnoisCoreProfile {
    val reader = JsonObjectReader(StringReader(json))
    return IchnoisCoreProfile.Deserializer().deserialize(reader, fixture.logger)
  }
}
