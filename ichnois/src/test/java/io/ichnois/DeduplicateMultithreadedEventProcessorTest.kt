package io.ichnois

import io.ichnois.UncaughtExceptionHandlerIntegration.UncaughtExceptionHint
import io.ichnois.hints.EventDropReason
import io.ichnois.protocol.Mechanism
import io.ichnois.protocol.IchnoisCoreException
import io.ichnois.util.HintUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeduplicateMultithreadedEventProcessorTest {
  class Fixture {
    fun getSut(): DeduplicateMultithreadedEventProcessor =
      DeduplicateMultithreadedEventProcessor(IchnoisCoreOptions())

    fun getEvent(type: String? = null, isHandled: Boolean = true, tid: Long? = null): IchnoisCoreEvent {
      val event =
        IchnoisCoreEvent().apply {
          exceptions =
            listOf(
              IchnoisCoreException().apply {
                this.type = type
                this.threadId = tid
                mechanism = Mechanism().apply { this.isHandled = isHandled }
              }
            )
        }
      return event
    }
  }

  private val fixture = Fixture()

  @Test
  fun `does not drop if not a crash`() {
    val processor = fixture.getSut()
    val processedEvent = processor.process(IchnoisCoreEvent(), Hint())

    assertNotNull(processedEvent)
  }

  @Test
  fun `does not drop if no type`() {
    val hint = HintUtils.createWithTypeCheckHint(UncaughtHint())
    val event = fixture.getEvent(isHandled = false)
    val processor = fixture.getSut()
    val processedEvent = processor.process(event, hint)

    assertNotNull(processedEvent)
  }

  @Test
  fun `does not drop if no tid`() {
    val hint = HintUtils.createWithTypeCheckHint(UncaughtHint())
    val event = fixture.getEvent(isHandled = false, type = "OutOfMemoryError")
    val processor = fixture.getSut()
    val processedEvent = processor.process(event, hint)

    assertNotNull(processedEvent)
  }

  @Test
  fun `does not drop if not yet processed`() {
    val hint = HintUtils.createWithTypeCheckHint(UncaughtHint())
    val event = fixture.getEvent(isHandled = false, type = "OutOfMemoryError", tid = 1)
    val processor = fixture.getSut()
    val processedEvent = processor.process(event, hint)

    assertNotNull(processedEvent)
  }

  @Test
  fun `does not drop if an event of this type was not processed yet`() {
    val hint = HintUtils.createWithTypeCheckHint(UncaughtHint())
    val event1 = fixture.getEvent(isHandled = false, type = "OutOfMemoryError", tid = 1)
    val event2 = fixture.getEvent(isHandled = false, type = "RuntimeException", tid = 2)

    val processor = fixture.getSut()

    val processedEvent1 = processor.process(event1, hint)
    assertNotNull(processedEvent1)

    val processedEvent2 = processor.process(event2, hint)
    assertNotNull(processedEvent2)
  }

  @Test
  fun `does not drop if an event of this type is from the same thread`() {
    val hint = HintUtils.createWithTypeCheckHint(UncaughtHint())
    val event1 = fixture.getEvent(isHandled = false, type = "OutOfMemoryError", tid = 1)
    val event2 = fixture.getEvent(isHandled = false, type = "OutOfMemoryError", tid = 1)

    val processor = fixture.getSut()

    val processedEvent1 = processor.process(event1, hint)
    assertNotNull(processedEvent1)

    val processedEvent2 = processor.process(event2, hint)
    assertNotNull(processedEvent2)
  }

  @Test
  fun `drops if the same event of this type is from a different thread`() {
    val hint = HintUtils.createWithTypeCheckHint(UncaughtHint())
    val event1 = fixture.getEvent(isHandled = false, type = "OutOfMemoryError", tid = 1)
    val event2 = fixture.getEvent(isHandled = false, type = "OutOfMemoryError", tid = 2)

    val processor = fixture.getSut()

    val processedEvent1 = processor.process(event1, hint)
    assertNotNull(processedEvent1)

    val processedEvent2 = processor.process(event2, hint)
    assertNull(processedEvent2)
  }

  @Test
  fun `sets drop reason when dropped`() {
    val event1 = fixture.getEvent(isHandled = false, type = "OutOfMemoryError", tid = 1)
    val event2 = fixture.getEvent(isHandled = false, type = "OutOfMemoryError", tid = 2)

    val processor = fixture.getSut()

    val hint = HintUtils.createWithTypeCheckHint(UncaughtHint())
    processor.process(event1, hint)
    processor.process(event2, hint)
    assertEquals(EventDropReason.MULTITHREADED_DEDUPLICATION, HintUtils.getEventDropReason(hint))
  }

  internal class UncaughtHint : UncaughtExceptionHint(0, NoOpLogger.getInstance())
}
