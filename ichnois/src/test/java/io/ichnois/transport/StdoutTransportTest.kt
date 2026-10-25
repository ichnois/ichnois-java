package io.ichnois.transport

import io.ichnois.ISerializer
import io.ichnois.IchnoisCoreEnvelope
import io.ichnois.IchnoisCoreEvent
import kotlin.test.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class StdoutTransportTest {
  private class Fixture {
    val serializer = mock<ISerializer>()

    fun getSUT(): ITransport = StdoutTransport(serializer)
  }

  private val fixture = Fixture()

  @Test
  fun `test serializes envelope`() {
    val transport = fixture.getSUT()
    val event = IchnoisCoreEvent()
    val envelope = IchnoisCoreEnvelope.from(fixture.serializer, event, null)

    transport.send(envelope)

    verify(fixture.serializer).serialize(eq(envelope), any())
  }
}
