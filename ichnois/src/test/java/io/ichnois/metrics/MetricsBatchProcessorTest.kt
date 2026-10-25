package io.ichnois.metrics

import com.google.common.truth.Truth.assertThat
import io.ichnois.DataCategory
import io.ichnois.IIchnoisCoreClient
import io.ichnois.IIchnoisCoreExecutorService
import io.ichnois.IchnoisCoreMetricsEvent
import io.ichnois.IchnoisCoreMetricsEvents
import io.ichnois.IchnoisCoreNanotimeDate
import io.ichnois.IchnoisCoreOptions
import io.ichnois.clientreport.ClientReportTestHelper
import io.ichnois.clientreport.DiscardReason
import io.ichnois.clientreport.DiscardedEvent
import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.test.DeferredExecutorService
import io.ichnois.test.injectForField
import io.ichnois.util.JsonSerializationUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class MetricsBatchProcessorTest {
  @Test
  fun `flush without accepted items does not submit processor work`() {
    val mockExecutor = mock<IIchnoisCoreExecutorService>()
    val processor = MetricsBatchProcessor(IchnoisCoreOptions(), mock())
    processor.injectForField("executorService", mockExecutor)

    processor.flush(0)

    verifyNoInteractions(mockExecutor)
  }

  @Test
  fun `close without accepted items does not submit processor work`() {
    for (isRestarting in listOf(false, true)) {
      val mockExecutor = mock<IIchnoisCoreExecutorService>()
      val processor = MetricsBatchProcessor(IchnoisCoreOptions(), mock())
      processor.injectForField("executorService", mockExecutor)

      processor.close(isRestarting)

      verify(mockExecutor).close(any())
      verify(mockExecutor, never()).schedule(any(), any())
      verify(mockExecutor, never()).submit(any<Runnable>())
    }
  }

  @Test
  fun `schedules another flush after previous flush has run`() {
    val mockClient = mock<IIchnoisCoreClient>()
    val mockExecutor = DeferredExecutorService()
    val processor = MetricsBatchProcessor(IchnoisCoreOptions(), mockClient)
    processor.injectForField("executorService", mockExecutor)

    processor.add(IchnoisCoreMetricsEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "first", "gauge", 1.0))
    mockExecutor.runAll()

    processor.add(IchnoisCoreMetricsEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "second", "gauge", 2.0))
    assertThat(mockExecutor.hasScheduledRunnables()).isTrue()
    mockExecutor.runAll()

    val captor = argumentCaptor<IchnoisCoreMetricsEvents>()
    verify(mockClient, times(2)).captureBatchedMetricsEvents(captor.capture())
    assertThat(captor.allValues.flatMap { it.items }.map { it.name })
      .containsExactly("first", "second")
      .inOrder()
  }

  @Test
  fun `drops metrics events after reaching MAX_QUEUE_SIZE limit`() {
    // given
    val mockClient = mock<IIchnoisCoreClient>()
    val mockExecutor = DeferredExecutorService()
    val options = IchnoisCoreOptions()
    val processor = MetricsBatchProcessor(options, mockClient)
    processor.injectForField("executorService", mockExecutor)

    for (i in 1..10001) {
      val logEvent =
        IchnoisCoreMetricsEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "name $i", "gauge", i.toDouble())
      processor.add(logEvent)
    }

    // run twice since a non full batch would be scheduled at the end
    mockExecutor.runAll()
    mockExecutor.runAll()

    // assert that the transport received 10000 metrics events
    val captor = argumentCaptor<IchnoisCoreMetricsEvents>()
    verify(mockClient, atLeast(1)).captureBatchedMetricsEvents(captor.capture())

    val allCapturedEvents = mutableListOf<IchnoisCoreMetricsEvent>()
    captor.allValues.forEach { metricsEvents -> allCapturedEvents.addAll(metricsEvents.items) }

    assertEquals(10000, allCapturedEvents.size)

    // assert that metric 10001 did not make it but metric 10000 did get sent
    val metric10000Found = allCapturedEvents.any { it.name == "name 10000" }
    val metric10001Found = allCapturedEvents.any { it.name == "name 10001" }

    assertTrue(metric10000Found, "Metric 10000 should have been sent")
    assertFalse(metric10001Found, "Metric 10001 should not have been sent")
  }

  @Test
  fun `records client report when log event is dropped due to queue overflow`() {
    // given
    val mockClient = mock<IIchnoisCoreClient>()
    val mockExecutor = DeferredExecutorService()
    val options = IchnoisCoreOptions()
    val processor = MetricsBatchProcessor(options, mockClient)
    processor.injectForField("executorService", mockExecutor)

    // fill the queue to MAX_QUEUE_SIZE
    for (i in 1..10000) {
      val logEvent =
        IchnoisCoreMetricsEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "name $i", "gauge", i.toDouble())
      processor.add(logEvent)
    }

    // add one more metrics event that should be dropped
    val droppedMetricsEvent =
      IchnoisCoreMetricsEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "dropped metric", "gauge", 10001.0)
    processor.add(droppedMetricsEvent)

    // verify that a client report was recorded for the dropped metrics item
    val droppedBytes =
      JsonSerializationUtils.byteSizeOf(options.serializer, options.logger, droppedMetricsEvent)
    val expectedEvents =
      mutableListOf(
        DiscardedEvent(DiscardReason.QUEUE_OVERFLOW.reason, DataCategory.TraceMetric.category, 1),
        DiscardedEvent(
          DiscardReason.QUEUE_OVERFLOW.reason,
          DataCategory.TraceMetricByte.category,
          droppedBytes,
        ),
      )

    ClientReportTestHelper.assertClientReport(options.clientReportRecorder, expectedEvents)
  }
}
