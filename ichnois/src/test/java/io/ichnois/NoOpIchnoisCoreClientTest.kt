package io.ichnois

import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.test.callMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.mockito.kotlin.mock

class NoOpIchnoisCoreClientTest {
  private var sut: NoOpIchnoisCoreClient = NoOpIchnoisCoreClient.getInstance()

  @Test fun `client is always disabled`() = assertFalse(sut.isEnabled)

  @Test
  fun `captureEvent returns empty IchnoisCoreId`() =
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.callMethod("captureEvent", IchnoisCoreEvent::class.java, null))

  @Test
  fun `captureException returns empty IchnoisCoreId`() =
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.callMethod("captureException", Throwable::class.java, null))

  @Test
  fun `captureMessage returns empty IchnoisCoreId`() =
    assertEquals(
      IchnoisCoreId.EMPTY_ID,
      sut.callMethod(
        "captureMessage",
        parameterTypes = arrayOf(String::class.java, IchnoisCoreLevel::class.java),
        null,
        null,
      ),
    )

  @Test
  fun `captureEnvelope returns empty IchnoisCoreId`() =
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.captureEnvelope(mock()))

  @Test
  fun `captureFeedback returns empty IchnoisCoreId`() =
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.captureFeedback(mock(), mock(), mock()))

  @Test
  fun `close does not affect captureEvent`() {
    sut.close()
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.callMethod("captureEvent", IchnoisCoreEvent::class.java, null))
  }

  @Test
  fun `close with isRestarting true does not affect captureEvent`() {
    sut.close(true)
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.callMethod("captureEvent", IchnoisCoreEvent::class.java, null))
  }

  @Test
  fun `close with isRestarting false does not affect captureEvent`() {
    sut.close(false)
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.callMethod("captureEvent", IchnoisCoreEvent::class.java, null))
  }

  @Test
  fun `close does not affect captureException`() {
    sut.close()
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.callMethod("captureException", Throwable::class.java, null))
  }

  @Test
  fun `close does not affect captureMessage`() {
    sut.close()
    assertEquals(
      IchnoisCoreId.EMPTY_ID,
      sut.callMethod(
        "captureMessage",
        parameterTypes = arrayOf(String::class.java, IchnoisCoreLevel::class.java),
        null,
        null,
      ),
    )
  }

  @Test
  fun `captureTransaction returns empty IchnoisCoreId`() =
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.captureTransaction(mock(), mock()))

  @Test
  fun `captureProfileChunk returns empty IchnoisCoreId`() =
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.captureProfileChunk(mock(), mock()))

  @Test
  fun `captureCheckIn returns empty id`() {
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.captureCheckIn(mock(), mock(), mock()))
  }
}
