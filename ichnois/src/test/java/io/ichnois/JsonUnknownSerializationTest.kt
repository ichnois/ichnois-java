package io.ichnois

import io.ichnois.protocol.App
import io.ichnois.protocol.Browser
import io.ichnois.protocol.DebugImage
import io.ichnois.protocol.DebugMeta
import io.ichnois.protocol.Device
import io.ichnois.protocol.Geo
import io.ichnois.protocol.Gpu
import io.ichnois.protocol.Mechanism
import io.ichnois.protocol.Message
import io.ichnois.protocol.OperatingSystem
import io.ichnois.protocol.Request
import io.ichnois.protocol.SdkInfo
import io.ichnois.protocol.SdkVersion
import io.ichnois.protocol.IchnoisCoreEnvelopeHeaderSerializationTest
import io.ichnois.protocol.IchnoisCoreEnvelopeItemHeaderSerializationTest
import io.ichnois.protocol.IchnoisCoreException
import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.protocol.IchnoisCorePackage
import io.ichnois.protocol.IchnoisCoreRuntime
import io.ichnois.protocol.IchnoisCoreSpan
import io.ichnois.protocol.IchnoisCoreSpanSerializationTest
import io.ichnois.protocol.IchnoisCoreStackFrame
import io.ichnois.protocol.IchnoisCoreStackTrace
import io.ichnois.protocol.IchnoisCoreThread
import io.ichnois.protocol.IchnoisCoreTransaction
import io.ichnois.protocol.IchnoisCoreTransactionSerializationTest
import io.ichnois.protocol.SessionSerializationTest
import io.ichnois.protocol.User
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Test serialization/deserialization for all classes implementing JsonUnknown */
@RunWith(Parameterized::class)
class JsonUnknownSerializationTest(
  private val jsonUnknown: JsonUnknown,
  private val jsonSerializable: JsonSerializable,
  private val deserializer: (JsonObjectReader, ILogger) -> JsonUnknown,
) {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): Collection<Array<Any>> {
      val app = givenJsonUnknown(App())
      val breadcrumb = givenJsonUnknown(Breadcrumb())
      val browser = givenJsonUnknown(Browser())
      val debugImage = givenJsonUnknown(DebugImage())
      val debugMeta = givenJsonUnknown(DebugMeta())
      val device = givenJsonUnknown(Device())
      val gpu = givenJsonUnknown(Gpu())
      val mechanism = givenJsonUnknown(Mechanism())
      val message = givenJsonUnknown(Message())
      val operatingSystem = givenJsonUnknown(OperatingSystem())
      val request = givenJsonUnknown(Request())
      val sdkInfo = givenJsonUnknown(SdkInfo())
      val ichnoisEnvelopeHeader =
        givenJsonUnknown(IchnoisCoreEnvelopeHeaderSerializationTest.Fixture().getSut())
      val ichnoisEnvelopeItemHeader =
        givenJsonUnknown(IchnoisCoreEnvelopeItemHeaderSerializationTest.Fixture().getSut())
      val ichnoisEvent = givenJsonUnknown(IchnoisCoreEvent())
      val ichnoisException = givenJsonUnknown(IchnoisCoreException())
      val ichnoisPackage =
        givenJsonUnknown(
          IchnoisCorePackage(
            "b59a1949-9950-4203-b394-ddd8d02c9633",
            "3d7790f3-7f32-43f7-b82f-9f5bc85205a8",
          )
        )
      val ichnoisRuntime = givenJsonUnknown(IchnoisCoreRuntime())
      val ichnoisSpan = givenJsonUnknown(IchnoisCoreSpanSerializationTest.Fixture().getSut())
      val ichnoisStackFrame = givenJsonUnknown(IchnoisCoreStackFrame())
      val ichnoisStackTrace = givenJsonUnknown(IchnoisCoreStackTrace())
      val ichnoisThread = givenJsonUnknown(IchnoisCoreThread())
      val ichnoisLockReason = givenJsonUnknown(IchnoisCoreLockReason())
      val ichnoisTransaction =
        givenJsonUnknown(IchnoisCoreTransactionSerializationTest.Fixture().getSut())
      val session = givenJsonUnknown(SessionSerializationTest.Fixture().getSut())
      val skdVersion =
        givenJsonUnknown(
          SdkVersion("3e934135-3f2b-49bc-8756-9f025b55143e", "3e31738e-4106-42d0-8be2-4a3a1bc648d3")
        )
      val spanContext = givenJsonUnknown(SpanContext("c2fb8fee2e2b49758bcb67cda0f713c7"))
      val traceContext = givenJsonUnknown(TraceContextSerializationTest.Fixture().getSut())
      val user = givenJsonUnknown(User())
      val userFeedback =
        givenJsonUnknown(UserFeedback(IchnoisCoreId("c2fb8fee2e2b49758bcb67cda0f713c7")))
      val geo = givenJsonUnknown(Geo())

      // Same instance for first and second param, so we cann access both JsonUnknown and
      // JsonSerializable in the test method. Third param is the method reference, so we
      // don't have to deal with generics.
      return listOf(
        arrayOf(app, app, App.Deserializer()::deserialize),
        arrayOf(breadcrumb, breadcrumb, Breadcrumb.Deserializer()::deserialize),
        arrayOf(browser, browser, Browser.Deserializer()::deserialize),
        arrayOf(debugImage, debugImage, DebugImage.Deserializer()::deserialize),
        arrayOf(debugMeta, debugMeta, DebugMeta.Deserializer()::deserialize),
        arrayOf(device, device, Device.Deserializer()::deserialize),
        arrayOf(gpu, gpu, Gpu.Deserializer()::deserialize),
        arrayOf(mechanism, mechanism, Mechanism.Deserializer()::deserialize),
        arrayOf(message, message, Message.Deserializer()::deserialize),
        arrayOf(operatingSystem, operatingSystem, OperatingSystem.Deserializer()::deserialize),
        arrayOf(request, user, Request.Deserializer()::deserialize),
        arrayOf(sdkInfo, sdkInfo, SdkInfo.Deserializer()::deserialize),
        arrayOf(
          ichnoisEnvelopeHeader,
          ichnoisEnvelopeHeader,
          IchnoisCoreEnvelopeHeader.Deserializer()::deserialize,
        ),
        arrayOf(
          ichnoisEnvelopeItemHeader,
          ichnoisEnvelopeItemHeader,
          IchnoisCoreEnvelopeItemHeader.Deserializer()::deserialize,
        ),
        arrayOf(ichnoisEvent, ichnoisEvent, IchnoisCoreEvent.Deserializer()::deserialize),
        arrayOf(ichnoisException, ichnoisException, IchnoisCoreException.Deserializer()::deserialize),
        arrayOf(ichnoisPackage, ichnoisPackage, IchnoisCorePackage.Deserializer()::deserialize),
        arrayOf(ichnoisRuntime, ichnoisRuntime, IchnoisCoreRuntime.Deserializer()::deserialize),
        arrayOf(ichnoisSpan, ichnoisSpan, IchnoisCoreSpan.Deserializer()::deserialize),
        arrayOf(ichnoisStackFrame, ichnoisStackFrame, IchnoisCoreStackFrame.Deserializer()::deserialize),
        arrayOf(ichnoisStackTrace, ichnoisStackTrace, IchnoisCoreStackTrace.Deserializer()::deserialize),
        arrayOf(ichnoisThread, ichnoisThread, IchnoisCoreThread.Deserializer()::deserialize),
        arrayOf(ichnoisLockReason, ichnoisLockReason, IchnoisCoreLockReason.Deserializer()::deserialize),
        arrayOf(
          ichnoisTransaction,
          ichnoisTransaction,
          IchnoisCoreTransaction.Deserializer()::deserialize,
        ),
        arrayOf(session, session, Session.Deserializer()::deserialize),
        arrayOf(skdVersion, skdVersion, SdkVersion.Deserializer()::deserialize),
        arrayOf(spanContext, spanContext, SpanContext.Deserializer()::deserialize),
        arrayOf(traceContext, traceContext, TraceContext.Deserializer()::deserialize),
        arrayOf(user, user, User.Deserializer()::deserialize),
        arrayOf(userFeedback, userFeedback, UserFeedback.Deserializer()::deserialize),
        arrayOf(geo, geo, Geo.Deserializer()::deserialize),
      )
    }

    private fun <T : JsonUnknown> givenJsonUnknown(jsonUnknown: T): T = jsonUnknown.apply {
      unknown = mapOf("fixture-key" to "fixture-value")
    }
  }

  @Test
  fun `serializing and deserialize app`() {
    val sut = jsonSerializable

    val serialized = serialize(sut)
    val reader = JsonObjectReader(StringReader(serialized))
    val logger = mock<ILogger>()
    val deserialized = deserializer(reader, logger)

    assertEquals(jsonUnknown.unknown, deserialized.unknown)
  }

  @Test
  fun `serializing unknown calls json object writer for app`() {
    val writer: JsonObjectWriter = mock()
    whenever(writer.name(any())).thenReturn(writer)

    val logger: ILogger = mock()
    val sut = jsonSerializable

    sut.serialize(writer, logger)

    verify(writer).name("fixture-key")
    verify(writer).value(logger, "fixture-value")
  }

  // Helper

  private fun serialize(jsonSerializable: JsonSerializable): String {
    val wrt = StringWriter()
    val jsonWrt = JsonObjectWriter(wrt, 100)
    val logger = mock<ILogger>()
    jsonSerializable.serialize(jsonWrt, logger)
    return wrt.toString()
  }
}
