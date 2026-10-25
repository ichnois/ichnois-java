package io.ichnois

import io.ichnois.protocol.IchnoisCoreId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class NoOpHubTest {
  private var sut: NoOpHub = NoOpHub.getInstance()

  @Test
  fun `getLastEventId returns empty IchnoisCoreId`() = assertEquals(IchnoisCoreId.EMPTY_ID, sut.lastEventId)

  @Test fun `addBreadcrumb doesn't throw on null breadcrumb`() = sut.addBreadcrumb("breadcrumb")

  @Test fun `hub is always disabled`() = assertFalse(sut.isEnabled)

  @Test
  fun `captureEvent returns empty IchnoisCoreId`() =
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.captureEvent(IchnoisCoreEvent()))

  @Test
  fun `captureTransaction returns empty IchnoisCoreId`() =
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.captureTransaction(mock(), mock<Hint>()))

  @Test
  fun `captureProfileChunk returns empty IchnoisCoreId`() =
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.captureProfileChunk(mock()))

  @Test
  fun `captureException returns empty IchnoisCoreId`() =
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.captureException(RuntimeException()))

  @Test
  fun `captureMessage returns empty IchnoisCoreId`() =
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.captureMessage("message"))

  @Test
  fun `captureFeedback returns empty IchnoisCoreId`() =
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.captureFeedback(mock()))

  @Test
  fun `close does not affect captureEvent`() {
    sut.close()
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.captureEvent(IchnoisCoreEvent()))
  }

  @Test
  fun `close with isRestarting true does not affect captureEvent`() {
    sut.close(true)
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.captureEvent(IchnoisCoreEvent()))
  }

  @Test
  fun `close with isRestarting false does not affect captureEvent`() {
    sut.close(false)
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.captureEvent(IchnoisCoreEvent()))
  }

  @Test
  fun `close does not affect captureException`() {
    sut.close()
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.captureException(RuntimeException()))
  }

  @Test
  fun `close does not affect captureMessage`() {
    sut.close()
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.captureMessage("message"))
  }

  @Test
  fun `pushScope is no op`() {
    sut.pushScope()
  }

  @Test fun `popScope is no op`() = sut.popScope()

  @Test fun `flush doesn't throw on null param`() = sut.flush(30000)

  @Test fun `clone returns the same instance`() = assertSame(NoOpHub.getInstance(), sut.clone())

  @Test
  fun `getSpan returns null`() {
    assertNull(sut.span)
  }

  @Test fun `setSpanContext doesnt throw`() = sut.setSpanContext(RuntimeException(), mock(), "")

  @Test fun `reportFullyDrawn doesnt throw`() = sut.reportFullyDisplayed()

  @Test
  fun `getBaggage returns null`() {
    assertNull(sut.baggage)
  }

  @Test
  fun `captureCheckIn returns empty id`() {
    assertEquals(IchnoisCoreId.EMPTY_ID, sut.captureCheckIn(mock()))
  }

  @Test
  fun `withScopeCallback is executed on NoOpScope`() {
    val scopeCallback = mock<ScopeCallback>()

    sut.withScope(scopeCallback)
    verify(scopeCallback).run(NoOpScope.getInstance())
  }

  @Test fun `startProfiler doesnt throw`() = sut.startProfiler()

  @Test fun `stopProfiler doesnt throw`() = sut.stopProfiler()
}
