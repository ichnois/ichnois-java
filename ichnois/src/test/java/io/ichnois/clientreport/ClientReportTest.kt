package io.ichnois.clientreport

import com.google.common.truth.Truth.assertThat
import io.ichnois.Attachment
import io.ichnois.CheckIn
import io.ichnois.CheckInStatus
import io.ichnois.DataCategory
import io.ichnois.DateUtils
import io.ichnois.EventProcessor
import io.ichnois.Hint
import io.ichnois.IScopes
import io.ichnois.NoOpLogger
import io.ichnois.ProfilingTraceData
import io.ichnois.ReplayRecording
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreEnvelope
import io.ichnois.IchnoisCoreEnvelopeHeader
import io.ichnois.IchnoisCoreEnvelopeItem
import io.ichnois.IchnoisCoreEnvelopeItemHeader
import io.ichnois.IchnoisCoreEvent
import io.ichnois.IchnoisCoreItemType
import io.ichnois.IchnoisCoreLogEvent
import io.ichnois.IchnoisCoreLogEvents
import io.ichnois.IchnoisCoreLogLevel
import io.ichnois.IchnoisCoreLongDate
import io.ichnois.IchnoisCoreMetricsEvent
import io.ichnois.IchnoisCoreMetricsEvents
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreReplayEvent
import io.ichnois.IchnoisCoreTracer
import io.ichnois.Session
import io.ichnois.TracesSamplingDecision
import io.ichnois.TransactionContext
import io.ichnois.UncaughtExceptionHandlerIntegration.UncaughtExceptionHint
import io.ichnois.UserFeedback
import io.ichnois.dsnString
import io.ichnois.hints.Retryable
import io.ichnois.protocol.Feedback
import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.protocol.IchnoisCoreTransaction
import io.ichnois.protocol.User
import io.ichnois.test.initForTest
import io.ichnois.util.HintUtils
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private const val LOG_CONTENT_TYPE = "application/vnd.ichnois.items.log+json"
private const val METRIC_CONTENT_TYPE = "application/vnd.ichnois.items.trace-metric+json"
private const val SPAN_CONTENT_TYPE = "application/vnd.ichnois.items.span.v2+json"

class ClientReportTest {
  lateinit var opts: IchnoisCoreOptions
  lateinit var clientReportRecorder: ClientReportRecorder
  lateinit var testHelper: ClientReportTestHelper

  @Test
  fun `callback error has expected discard reason`() {
    assertThat(DiscardReason.CALLBACK_ERROR.reason).isEqualTo("callback_error")
  }

  @Test
  fun `lost envelope can be recorded`() {
    givenClientReportRecorder()
    val scopes = mock<IScopes>()
    whenever(scopes.options).thenReturn(opts)
    val transaction = IchnoisCoreTracer(TransactionContext("name", "op"), scopes)
    val feedbackEvent = IchnoisCoreEvent().apply { contexts.setFeedback(Feedback("message")) }

    val lostClientReport =
      ClientReport(
        DateUtils.getCurrentDateTime(),
        listOf(
          DiscardedEvent(DiscardReason.SAMPLE_RATE.reason, DataCategory.Error.category, 3),
          DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Error.category, 2),
          DiscardedEvent(DiscardReason.QUEUE_OVERFLOW.reason, DataCategory.Transaction.category, 1),
        ),
      )

    val envelope =
      testHelper.newEnvelope(
        IchnoisCoreEnvelopeItem.fromClientReport(opts.serializer, lostClientReport),
        IchnoisCoreEnvelopeItem.fromEvent(opts.serializer, IchnoisCoreTransaction(transaction)),
        IchnoisCoreEnvelopeItem.fromEvent(opts.serializer, IchnoisCoreEvent()),
        IchnoisCoreEnvelopeItem.fromSession(opts.serializer, Session("dis", User(), "env", "0.0.1")),
        IchnoisCoreEnvelopeItem.fromUserFeedback(
          opts.serializer,
          UserFeedback(IchnoisCoreId(UUID.randomUUID())),
        ),
        IchnoisCoreEnvelopeItem.fromAttachment(
          opts.serializer,
          NoOpLogger.getInstance(),
          Attachment("{ \"number\": 10 }".toByteArray(), "log.json"),
          1000,
        ),
        IchnoisCoreEnvelopeItem.fromProfilingTrace(
          ProfilingTraceData(File(""), transaction),
          1000,
          opts.serializer,
        ),
        IchnoisCoreEnvelopeItem.fromCheckIn(
          opts.serializer,
          CheckIn("monitor-slug-1", CheckInStatus.ERROR),
        ),
        IchnoisCoreEnvelopeItem.fromReplay(
          opts.serializer,
          opts.logger,
          IchnoisCoreReplayEvent(),
          ReplayRecording(),
          false,
        ),
        IchnoisCoreEnvelopeItem.fromEvent(opts.serializer, feedbackEvent),
      )

    clientReportRecorder.recordLostEnvelope(DiscardReason.NETWORK_ERROR, envelope)

    val clientReportAtEnd = clientReportRecorder.resetCountsAndGenerateClientReport()
    testHelper.assertTotalCount(16, clientReportAtEnd)
    testHelper.assertCountFor(DiscardReason.SAMPLE_RATE, DataCategory.Error, 3, clientReportAtEnd)
    testHelper.assertCountFor(DiscardReason.BEFORE_SEND, DataCategory.Error, 2, clientReportAtEnd)
    testHelper.assertCountFor(
      DiscardReason.QUEUE_OVERFLOW,
      DataCategory.Transaction,
      1,
      clientReportAtEnd,
    )
    testHelper.assertCountFor(DiscardReason.NETWORK_ERROR, DataCategory.Span, 1, clientReportAtEnd)
    testHelper.assertCountFor(
      DiscardReason.NETWORK_ERROR,
      DataCategory.Transaction,
      1,
      clientReportAtEnd,
    )
    testHelper.assertCountFor(DiscardReason.NETWORK_ERROR, DataCategory.Error, 1, clientReportAtEnd)
    testHelper.assertCountFor(
      DiscardReason.NETWORK_ERROR,
      DataCategory.UserReport,
      1,
      clientReportAtEnd,
    )
    testHelper.assertCountFor(
      DiscardReason.NETWORK_ERROR,
      DataCategory.Session,
      1,
      clientReportAtEnd,
    )
    testHelper.assertCountFor(
      DiscardReason.NETWORK_ERROR,
      DataCategory.Attachment,
      1,
      clientReportAtEnd,
    )
    testHelper.assertCountFor(
      DiscardReason.NETWORK_ERROR,
      DataCategory.Profile,
      1,
      clientReportAtEnd,
    )
    testHelper.assertCountFor(
      DiscardReason.NETWORK_ERROR,
      DataCategory.Monitor,
      1,
      clientReportAtEnd,
    )
    testHelper.assertCountFor(
      DiscardReason.NETWORK_ERROR,
      DataCategory.Replay,
      1,
      clientReportAtEnd,
    )
    testHelper.assertCountFor(
      DiscardReason.NETWORK_ERROR,
      DataCategory.Feedback,
      1,
      clientReportAtEnd,
    )
  }

  @Test
  fun `lost transaction records dropped spans`() {
    givenClientReportRecorder()
    val scopes = mock<IScopes>()
    whenever(scopes.options).thenReturn(opts)
    val transaction =
      IchnoisCoreTracer(TransactionContext("name", "op", TracesSamplingDecision(true)), scopes)
    transaction.startChild("lost span", "span1").finish()
    transaction.startChild("lost span", "span2").finish()
    transaction.startChild("lost span", "span3").finish()
    transaction.startChild("lost span", "span4").finish()

    val envelope =
      testHelper.newEnvelope(
        IchnoisCoreEnvelopeItem.fromEvent(opts.serializer, IchnoisCoreTransaction(transaction))
      )

    clientReportRecorder.recordLostEnvelope(DiscardReason.NETWORK_ERROR, envelope)

    val clientReportAtEnd = clientReportRecorder.resetCountsAndGenerateClientReport()
    testHelper.assertTotalCount(6, clientReportAtEnd)
    testHelper.assertCountFor(DiscardReason.NETWORK_ERROR, DataCategory.Span, 5, clientReportAtEnd)
    testHelper.assertCountFor(
      DiscardReason.NETWORK_ERROR,
      DataCategory.Transaction,
      1,
      clientReportAtEnd,
    )
  }

  @Test
  fun `lost event can be recorded`() {
    givenClientReportRecorder()

    clientReportRecorder.recordLostEvent(DiscardReason.EVENT_PROCESSOR, DataCategory.Error)

    val clientReport = clientReportRecorder.resetCountsAndGenerateClientReport()
    testHelper.assertTotalCount(1, clientReport)
    testHelper.assertCountFor(DiscardReason.EVENT_PROCESSOR, DataCategory.Error, 1, clientReport)
  }

  @Test
  fun `lost envelope item can be recorded`() {
    givenClientReportRecorder()

    val lostClientReport =
      ClientReport(
        DateUtils.getCurrentDateTime(),
        listOf(
          DiscardedEvent(DiscardReason.SAMPLE_RATE.reason, DataCategory.Error.category, 3),
          DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Feedback.category, 2),
          DiscardedEvent(DiscardReason.QUEUE_OVERFLOW.reason, DataCategory.Transaction.category, 1),
          DiscardedEvent(DiscardReason.SAMPLE_RATE.reason, DataCategory.Profile.category, 2),
        ),
      )

    val envelopeItem = IchnoisCoreEnvelopeItem.fromClientReport(opts.serializer, lostClientReport)

    clientReportRecorder.recordLostEnvelopeItem(DiscardReason.NETWORK_ERROR, envelopeItem)

    val clientReportAtEnd = clientReportRecorder.resetCountsAndGenerateClientReport()
    testHelper.assertTotalCount(8, clientReportAtEnd)
    testHelper.assertCountFor(DiscardReason.SAMPLE_RATE, DataCategory.Error, 3, clientReportAtEnd)
    testHelper.assertCountFor(
      DiscardReason.BEFORE_SEND,
      DataCategory.Feedback,
      2,
      clientReportAtEnd,
    )
    testHelper.assertCountFor(
      DiscardReason.QUEUE_OVERFLOW,
      DataCategory.Transaction,
      1,
      clientReportAtEnd,
    )
    testHelper.assertCountFor(DiscardReason.SAMPLE_RATE, DataCategory.Profile, 2, clientReportAtEnd)
  }

  @Test
  fun `attaching client report to an envelope resets counts`() {
    givenClientReportRecorder()

    clientReportRecorder.recordLostEvent(DiscardReason.CACHE_OVERFLOW, DataCategory.Attachment)
    clientReportRecorder.recordLostEvent(DiscardReason.CACHE_OVERFLOW, DataCategory.Attachment)
    clientReportRecorder.recordLostEvent(DiscardReason.RATELIMIT_BACKOFF, DataCategory.Error)
    clientReportRecorder.recordLostEvent(DiscardReason.QUEUE_OVERFLOW, DataCategory.Error)
    clientReportRecorder.recordLostEvent(DiscardReason.BEFORE_SEND, DataCategory.Profile)

    val envelope = clientReportRecorder.attachReportToEnvelope(testHelper.newEnvelope())

    testHelper.assertTotalCount(0, clientReportRecorder.resetCountsAndGenerateClientReport())

    val envelopeReport = envelope.items.first().getClientReport(opts.serializer)!!
    assertEquals(4, envelopeReport.discardedEvents.size)
    assertEquals(
      2,
      envelopeReport.discardedEvents
        .first {
          it.reason == DiscardReason.CACHE_OVERFLOW.reason &&
            it.category == DataCategory.Attachment.category
        }
        .quantity,
    )
    assertEquals(
      1,
      envelopeReport.discardedEvents
        .first {
          it.reason == DiscardReason.RATELIMIT_BACKOFF.reason &&
            it.category == DataCategory.Error.category
        }
        .quantity,
    )
    assertEquals(
      1,
      envelopeReport.discardedEvents
        .first {
          it.reason == DiscardReason.QUEUE_OVERFLOW.reason &&
            it.category == DataCategory.Error.category
        }
        .quantity,
    )
    assertEquals(
      1,
      envelopeReport.discardedEvents
        .first {
          it.reason == DiscardReason.BEFORE_SEND.reason &&
            it.category == DataCategory.Profile.category
        }
        .quantity,
    )
    assertTrue(
      ChronoUnit.MILLIS.between(
        LocalDateTime.now(),
        envelopeReport.timestamp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime(),
      ) < 10000
    )
  }

  @Test
  fun `restoring counts via recordLostEnvelope does not fire onDiscard again`() {
    assertRestoringCountsDoesNotFireOnDiscard { recorder, envelope ->
      recorder.recordLostEnvelope(DiscardReason.EVENT_PROCESSOR, envelope)
    }
  }

  @Test
  fun `restoring counts via recordLostEnvelopeItem does not fire onDiscard again`() {
    assertRestoringCountsDoesNotFireOnDiscard { recorder, envelope ->
      recorder.recordLostEnvelopeItem(DiscardReason.NETWORK_ERROR, envelope.items.first())
    }
  }

  // Counts restored from an attached client report were already reported once, so replaying them
  // must not fire onDiscard a second time. Both public entry points have to hold the property.
  private fun assertRestoringCountsDoesNotFireOnDiscard(
    recordLost: (ClientReportRecorder, IchnoisCoreEnvelope) -> Unit
  ) {
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    givenClientReportRecorder { options -> options.onDiscard = onDiscardMock }

    clientReportRecorder.recordLostEvent(DiscardReason.CACHE_OVERFLOW, DataCategory.Attachment)
    clientReportRecorder.recordLostEvent(DiscardReason.CACHE_OVERFLOW, DataCategory.Attachment)
    clientReportRecorder.recordLostEvent(DiscardReason.RATELIMIT_BACKOFF, DataCategory.Error)
    clientReportRecorder.recordLostEvent(DiscardReason.QUEUE_OVERFLOW, DataCategory.Error)
    clientReportRecorder.recordLostEvent(DiscardReason.BEFORE_SEND, DataCategory.Profile)

    val envelope = clientReportRecorder.attachReportToEnvelope(testHelper.newEnvelope())
    recordLost(clientReportRecorder, envelope)

    verify(onDiscardMock, times(2))
      .execute(DiscardReason.CACHE_OVERFLOW, DataCategory.Attachment, 1)
    verify(onDiscardMock, times(1)).execute(DiscardReason.RATELIMIT_BACKOFF, DataCategory.Error, 1)
    verify(onDiscardMock, times(1)).execute(DiscardReason.QUEUE_OVERFLOW, DataCategory.Error, 1)
    verify(onDiscardMock, times(1)).execute(DiscardReason.BEFORE_SEND, DataCategory.Profile, 1)
  }

  @Test
  fun `recording lost client report counts log entries`() {
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    givenClientReportRecorder { options -> options.onDiscard = onDiscardMock }

    val envelope =
      testHelper.newEnvelope(
        IchnoisCoreEnvelopeItem.fromLogs(
          opts.serializer,
          IchnoisCoreLogEvents(
            listOf(
              IchnoisCoreLogEvent(IchnoisCoreId(), IchnoisCoreLongDate(1), "log message 1", IchnoisCoreLogLevel.ERROR),
              IchnoisCoreLogEvent(IchnoisCoreId(), IchnoisCoreLongDate(2), "log message 2", IchnoisCoreLogLevel.WARN),
            )
          ),
        )
      )

    clientReportRecorder.recordLostEnvelopeItem(DiscardReason.NETWORK_ERROR, envelope.items.first())

    verify(onDiscardMock, times(1)).execute(DiscardReason.NETWORK_ERROR, DataCategory.LogItem, 2)

    val clientReport = clientReportRecorder.resetCountsAndGenerateClientReport()
    val logItem =
      clientReport!!.discardedEvents!!.first { it.category == DataCategory.LogItem.category }
    assertEquals(2, logItem.quantity)
    val logByte =
      clientReport!!.discardedEvents!!.first { it.category == DataCategory.LogByte.category }
    assertEquals(226, logByte.quantity)
  }

  @Test
  fun `recording lost client report counts metric entries`() {
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    givenClientReportRecorder { options -> options.onDiscard = onDiscardMock }

    val envelope =
      testHelper.newEnvelope(
        IchnoisCoreEnvelopeItem.fromMetrics(
          opts.serializer,
          IchnoisCoreMetricsEvents(
            listOf(
              IchnoisCoreMetricsEvent(IchnoisCoreId(), IchnoisCoreLongDate(1), "metric1", "counter", 1.0),
              IchnoisCoreMetricsEvent(IchnoisCoreId(), IchnoisCoreLongDate(2), "metric2", "gauge", 2.0),
              IchnoisCoreMetricsEvent(IchnoisCoreId(), IchnoisCoreLongDate(3), "metric3", "distribution", 3.0),
            )
          ),
        )
      )

    clientReportRecorder.recordLostEnvelopeItem(DiscardReason.NETWORK_ERROR, envelope.items.first())

    verify(onDiscardMock, times(1))
      .execute(DiscardReason.NETWORK_ERROR, DataCategory.TraceMetric, 3)

    val clientReport = clientReportRecorder.resetCountsAndGenerateClientReport()
    val metricItem =
      clientReport!!.discardedEvents!!.first { it.category == DataCategory.TraceMetric.category }
    assertEquals(3, metricItem.quantity)
    val metricByteItem =
      clientReport.discardedEvents!!.first { it.category == DataCategory.TraceMetricByte.category }
    assertEquals(envelope.items.first().data.size.toLong(), metricByteItem.quantity)
  }

  @Test
  fun `recording lost log item reads count from the header without deserializing the payload`() {
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    givenClientReportRecorder { options -> options.onDiscard = onDiscardMock }

    val payload = "irrelevant payload".toByteArray()
    val item = mockEnvelopeItem(IchnoisCoreItemType.Log, LOG_CONTENT_TYPE, itemCount = 5, data = payload)

    clientReportRecorder.recordLostEnvelopeItem(DiscardReason.NETWORK_ERROR, item)

    // Deserializing here is what pinned CPU cores under sustained rate limiting (JAVA-662), so the
    // count must come from the header and the payload must stay untouched.
    verify(item, never()).getLogs(any())
    verify(onDiscardMock, times(1)).execute(DiscardReason.NETWORK_ERROR, DataCategory.LogItem, 5)

    val clientReport = clientReportRecorder.resetCountsAndGenerateClientReport()
    assertEquals(5, clientReport.quantityOf(DataCategory.LogItem))
    assertEquals(payload.size.toLong(), clientReport.quantityOf(DataCategory.LogByte))
  }

  @Test
  fun `recording lost metric item reads count from the header without deserializing the payload`() {
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    givenClientReportRecorder { options -> options.onDiscard = onDiscardMock }

    val payload = "irrelevant payload".toByteArray()
    val item =
      mockEnvelopeItem(
        IchnoisCoreItemType.TraceMetric,
        METRIC_CONTENT_TYPE,
        itemCount = 5,
        data = payload,
      )

    clientReportRecorder.recordLostEnvelopeItem(DiscardReason.NETWORK_ERROR, item)

    verify(item, never()).getMetrics(any())
    verify(onDiscardMock, times(1))
      .execute(DiscardReason.NETWORK_ERROR, DataCategory.TraceMetric, 5)

    val clientReport = clientReportRecorder.resetCountsAndGenerateClientReport()
    assertEquals(5, clientReport.quantityOf(DataCategory.TraceMetric))
    assertEquals(payload.size.toLong(), clientReport.quantityOf(DataCategory.TraceMetricByte))
  }

  @Test
  fun `recording lost log item without item count in header falls back to one`() {
    givenClientReportRecorder()

    val item =
      mockEnvelopeItem(IchnoisCoreItemType.Log, LOG_CONTENT_TYPE, itemCount = null, data = ByteArray(0))

    clientReportRecorder.recordLostEnvelopeItem(DiscardReason.NETWORK_ERROR, item)

    val clientReport = clientReportRecorder.resetCountsAndGenerateClientReport()
    assertEquals(1, clientReport.quantityOf(DataCategory.LogItem))
  }

  @Test
  fun `recording lost metric item without item count in header falls back to one`() {
    givenClientReportRecorder()

    val item =
      mockEnvelopeItem(
        IchnoisCoreItemType.TraceMetric,
        METRIC_CONTENT_TYPE,
        itemCount = null,
        data = ByteArray(0),
      )

    clientReportRecorder.recordLostEnvelopeItem(DiscardReason.NETWORK_ERROR, item)

    val clientReport = clientReportRecorder.resetCountsAndGenerateClientReport()
    assertEquals(1, clientReport.quantityOf(DataCategory.TraceMetric))
  }

  @Test
  fun `recording lost span item reads count from the header`() {
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    givenClientReportRecorder { options -> options.onDiscard = onDiscardMock }

    // Batched span items reach this SDK from hybrid SDKs (e.g. Flutter span streaming).
    val item =
      mockEnvelopeItem(
        IchnoisCoreItemType.Span,
        SPAN_CONTENT_TYPE,
        itemCount = 500,
        data = "irrelevant payload".toByteArray(),
      )

    clientReportRecorder.recordLostEnvelopeItem(DiscardReason.NETWORK_ERROR, item)

    verify(onDiscardMock, times(1)).execute(DiscardReason.NETWORK_ERROR, DataCategory.Span, 500)

    val clientReport = clientReportRecorder.resetCountsAndGenerateClientReport()
    assertEquals(500, clientReport.quantityOf(DataCategory.Span))
  }

  @Test
  fun `recording rate limited span item reads count from the header`() {
    givenClientReportRecorder()

    val item =
      mockEnvelopeItem(IchnoisCoreItemType.Span, SPAN_CONTENT_TYPE, itemCount = 7, data = ByteArray(0))

    clientReportRecorder.recordLostEnvelopeItem(DiscardReason.RATELIMIT_BACKOFF, item)

    val clientReport = clientReportRecorder.resetCountsAndGenerateClientReport()
    val spanItem =
      clientReport!!.discardedEvents!!.single {
        it.category == DataCategory.Span.category &&
          it.reason == DiscardReason.RATELIMIT_BACKOFF.reason
      }
    assertEquals(7, spanItem.quantity)
  }

  @Test
  fun `recording lost span item without item count in header falls back to one`() {
    givenClientReportRecorder()

    val item =
      mockEnvelopeItem(
        IchnoisCoreItemType.Span,
        SPAN_CONTENT_TYPE,
        itemCount = null,
        data = ByteArray(0),
      )

    clientReportRecorder.recordLostEnvelopeItem(DiscardReason.NETWORK_ERROR, item)

    val clientReport = clientReportRecorder.resetCountsAndGenerateClientReport()
    assertEquals(1, clientReport.quantityOf(DataCategory.Span))
  }

  private fun mockEnvelopeItem(
    type: IchnoisCoreItemType,
    contentType: String,
    itemCount: Int?,
    data: ByteArray,
  ): IchnoisCoreEnvelopeItem {
    val itemHeader = IchnoisCoreEnvelopeItemHeader(type, 0, contentType, null, null, null, itemCount)
    return mock {
      on { it.header } doReturn itemHeader
      on { it.data } doReturn data
    }
  }

  private fun ClientReport?.quantityOf(category: DataCategory): Long =
    this!!.discardedEvents!!.first { it.category == category.category }.quantity

  private fun givenClientReportRecorder(
    callback: IchnoisCore.OptionsConfiguration<IchnoisCoreOptions>? = null
  ) {
    setupIchnoisCore { options -> callback?.configure(options) }
    clientReportRecorder = opts.clientReportRecorder as ClientReportRecorder
    testHelper = ClientReportTestHelper(opts)
  }

  private fun setupIchnoisCore(callback: IchnoisCore.OptionsConfiguration<IchnoisCoreOptions>? = null) {
    initForTest { options ->
      options.dsn = dsnString
      callback?.configure(options)
      opts = options
    }
  }
}

class DropEverythingEventProcessor : EventProcessor {
  override fun process(event: IchnoisCoreEvent, hint: Hint): IchnoisCoreEvent? = null

  override fun process(transaction: IchnoisCoreTransaction, hint: Hint): IchnoisCoreTransaction? = null
}

class ClientReportTestHelper(val options: IchnoisCoreOptions) {
  val reasons = DiscardReason.values()
  val categories = DataCategory.values()

  fun assertTotalCount(expectedCount: Long, clientReport: ClientReport?) {
    assertEquals(expectedCount, clientReport?.discardedEvents?.sumOf { it.quantity } ?: 0L)
  }

  fun assertCountFor(
    reason: DiscardReason,
    category: DataCategory,
    expectedCount: Long,
    clientReport: ClientReport?,
  ) {
    val discardedEvent =
      clientReport?.discardedEvents?.first {
        it.category == category.category && it.reason == reason.reason
      }
    assertEquals(expectedCount, discardedEvent?.quantity ?: 0L)
  }

  fun randomCategory(): DataCategory = categories.random()

  fun randomReason(): DiscardReason = reasons.random()

  fun newEnvelope(vararg items: IchnoisCoreEnvelopeItem): IchnoisCoreEnvelope {
    val header = IchnoisCoreEnvelopeHeader(IchnoisCoreId(UUID.randomUUID()))
    return IchnoisCoreEnvelope(header, items.toList())
  }

  companion object {
    fun retryableHint() = HintUtils.createWithTypeCheckHint(TestRetryable())

    fun uncaughtExceptionHint() = HintUtils.createWithTypeCheckHint(TestUncaughtExceptionHint())

    fun retryableUncaughtExceptionHint() =
      HintUtils.createWithTypeCheckHint(TestRetryableUncaughtException())

    fun assertClientReport(
      clientReportRecorder: IClientReportRecorder,
      expectedEvents: List<DiscardedEvent>,
    ) {
      val recorder = clientReportRecorder as ClientReportRecorder
      val clientReport = recorder.resetCountsAndGenerateClientReport()
      assertClientReport(clientReport, expectedEvents)
    }

    fun assertClientReport(clientReport: ClientReport?, expectedEvents: List<DiscardedEvent>) {
      assertEquals(
        expectedEvents.filter { it.quantity > 0 }.size,
        clientReport?.discardedEvents?.size ?: 0,
      )

      expectedEvents.forEach { expectedEvent ->
        val actualEvent =
          clientReport?.discardedEvents?.firstOrNull {
            it.reason == expectedEvent.reason && it.category == expectedEvent.category
          }
        assertEquals(
          expectedEvent.quantity,
          actualEvent?.quantity ?: 0,
          clientReport?.discardedEvents?.toString(),
        )
      }
    }
  }
}

class TestRetryable : Retryable {
  private var retry = false

  override fun setRetry(retry: Boolean) {
    this.retry = retry
  }

  override fun isRetry(): Boolean = this.retry
}

class TestRetryableUncaughtException :
  UncaughtExceptionHint(0, NoOpLogger.getInstance()), Retryable {
  private var retry = false
  var flushed = false

  override fun setRetry(retry: Boolean) {
    this.retry = retry
  }

  override fun isRetry(): Boolean = this.retry

  override fun markFlushed() {
    flushed = true
  }
}

class TestUncaughtExceptionHint : UncaughtExceptionHint(0, NoOpLogger.getInstance()) {
  var flushed = false

  override fun markFlushed() {
    flushed = true
  }
}
