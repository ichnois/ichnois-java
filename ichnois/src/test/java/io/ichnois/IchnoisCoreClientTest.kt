package io.ichnois

import com.google.common.truth.Truth.assertThat
import io.ichnois.Scope.IWithPropagationContext
import io.ichnois.IchnoisCoreLevel.WARNING
import io.ichnois.Session.State.Crashed
import io.ichnois.clientreport.ClientReportTestHelper.Companion.assertClientReport
import io.ichnois.clientreport.DiscardReason
import io.ichnois.clientreport.DiscardedEvent
import io.ichnois.exception.IchnoisCoreEnvelopeException
import io.ichnois.hints.AbnormalExit
import io.ichnois.hints.ApplyScopeData
import io.ichnois.hints.Backfillable
import io.ichnois.hints.Cached
import io.ichnois.hints.DiskFlushNotification
import io.ichnois.hints.TransactionEnd
import io.ichnois.internal.eventprocessor.IchnoisCoreEventProcessor
import io.ichnois.logger.ILoggerBatchProcessor
import io.ichnois.logger.ILoggerBatchProcessorFactory
import io.ichnois.metrics.IMetricsBatchProcessor
import io.ichnois.metrics.IMetricsBatchProcessorFactory
import io.ichnois.protocol.Contexts
import io.ichnois.protocol.DebugImage
import io.ichnois.protocol.DebugMeta
import io.ichnois.protocol.Feedback
import io.ichnois.protocol.Mechanism
import io.ichnois.protocol.Message
import io.ichnois.protocol.Request
import io.ichnois.protocol.SdkVersion
import io.ichnois.protocol.IchnoisCoreException
import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.protocol.IchnoisCoreTransaction
import io.ichnois.protocol.User
import io.ichnois.protocol.ViewHierarchy
import io.ichnois.test.callMethod
import io.ichnois.test.injectForField
import io.ichnois.transport.ITransport
import io.ichnois.transport.ITransportGate
import io.ichnois.util.HintUtils
import io.ichnois.util.JsonSerializationUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.Arrays
import java.util.LinkedList
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.mockingDetails
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.msgpack.core.MessagePack

class IchnoisCoreClientTest {

  @get:Rule val tmpDir = TemporaryFolder()

  class Fixture {
    var transport = mock<ITransport>()
    var factory = mock<ITransportFactory>()
    var loggerBatchProcessor = mock<ILoggerBatchProcessor>()
    var loggerBatchProcessorFactory = mock<ILoggerBatchProcessorFactory>()
    var metricsBatchProcessor = mock<IMetricsBatchProcessor>()
    var metricsBatchProcessorFactory = mock<IMetricsBatchProcessorFactory>()
    val maxAttachmentSize: Long = (5 * 1024 * 1024).toLong()
    val scopes = mock<IScopes>()
    val ichnoisTracer: IchnoisCoreTracer
    val profileChunk: ProfileChunk
    val profilingTraceFile = Files.createTempFile("trace", ".trace").toFile()

    var ichnoisOptions: IchnoisCoreOptions =
      IchnoisCoreOptions().apply {
        dsn = dsnString
        sdkVersion = SdkVersion("test", "1.2.3")
        isDebug = true
        setDiagnosticLevel(IchnoisCoreLevel.DEBUG)
        setSerializer(JsonSerializer(this))
        setLogger(mock())
        maxAttachmentSize = this@Fixture.maxAttachmentSize
        setTransportFactory(factory)
        logs.setLoggerBatchProcessorFactory(loggerBatchProcessorFactory)
        metrics.setMetricsBatchProcessorFactory(metricsBatchProcessorFactory)
        release = "0.0.1"
        isTraceSampling = true
      }

    init {
      whenever(factory.create(any(), any())).thenReturn(transport)
      whenever(loggerBatchProcessorFactory.create(any(), any())).thenReturn(loggerBatchProcessor)
      whenever(metricsBatchProcessorFactory.create(any(), any())).thenReturn(metricsBatchProcessor)
      whenever(scopes.options).thenReturn(ichnoisOptions)
      ichnoisTracer =
        IchnoisCoreTracer(
          TransactionContext("a-transaction", "op", TracesSamplingDecision(true)),
          scopes,
        )
      ichnoisTracer.startChild("a-span", "span 1").finish()
      profileChunk =
        ProfileChunk(
          IchnoisCoreId(),
          IchnoisCoreId(),
          profilingTraceFile,
          emptyMap(),
          1.0,
          ProfileChunk.PLATFORM_ANDROID,
          ichnoisOptions,
        )
    }

    var attachment = Attachment("hello".toByteArray(), "hello.txt", "text/plain", true)
    var attachment2 = Attachment("hello2".toByteArray(), "hello2.txt", "text/plain", true)
    var attachment3 = Attachment("hello3".toByteArray(), "hello3.txt", "text/plain", true)
    var profilingTraceData = ProfilingTraceData(profilingTraceFile, ichnoisTracer)
    var profilingNonExistingTraceData = ProfilingTraceData(File("non_existent.trace"), ichnoisTracer)

    fun getSut(optionsCallback: ((IchnoisCoreOptions) -> Unit)? = null): IchnoisCoreClient {
      optionsCallback?.invoke(ichnoisOptions)
      profilingTraceFile.writeText("sampledProfile")
      return IchnoisCoreClient(ichnoisOptions)
    }
  }

  private val fixture = Fixture()

  @Test
  fun `when fixture is unchanged, client is enabled`() {
    val sut = fixture.getSut()
    assertTrue(sut.isEnabled)
  }

  @Test
  fun `when Logs and Metrics options are disabled, batch processors are still created`() {
    val sut = fixture.getSut {
      it.logs.isEnabled = false
      it.metrics.isEnabled = false
    }

    verify(fixture.loggerBatchProcessorFactory).create(fixture.ichnoisOptions, sut)
    verify(fixture.metricsBatchProcessorFactory).create(fixture.ichnoisOptions, sut)
  }

  @Test
  fun `when dsn is an invalid string, client throws`() {
    fixture.ichnoisOptions.dsn = "invalid-dsn"
    assertFailsWith<IllegalArgumentException> { fixture.getSut() }
  }

  @Test
  fun `when dsn is null, client throws`() {
    fixture.ichnoisOptions.dsn = null
    assertFailsWith<IllegalArgumentException> { fixture.getSut() }
  }

  @Test
  fun `when dsn without private key is valid, client is enabled`() {
    fixture.ichnoisOptions.dsn = dsnString
    val sut = fixture.getSut()
    assertTrue(sut.isEnabled)
  }

  @Test
  fun `when dsn with secret is valid, client is enabled`() {
    fixture.ichnoisOptions.dsn = dsnStringLegacy
    val sut = fixture.getSut()
    assertTrue(sut.isEnabled)
  }

  @Test
  fun `when client is closed with isRestarting false, transport waits`() {
    val sut = fixture.getSut { options -> options.logs.isEnabled = true }
    assertTrue(sut.isEnabled)
    sut.close(false)
    assertNotEquals(0, fixture.ichnoisOptions.shutdownTimeoutMillis)
    verify(fixture.transport).flush(eq(fixture.ichnoisOptions.shutdownTimeoutMillis))
    verify(fixture.loggerBatchProcessor).flush(eq(fixture.ichnoisOptions.shutdownTimeoutMillis))
    verify(fixture.metricsBatchProcessor).flush(eq(fixture.ichnoisOptions.shutdownTimeoutMillis))
    verify(fixture.transport).close(eq(false))
    verify(fixture.loggerBatchProcessor).close(eq(false))
    verify(fixture.metricsBatchProcessor).close(eq(false))
  }

  @Test
  fun `when client is closed with isRestarting true, transport does not wait`() {
    val sut = fixture.getSut { options -> options.logs.isEnabled = true }
    assertTrue(sut.isEnabled)
    sut.close(true)
    verify(fixture.transport).flush(eq(0))
    verify(fixture.loggerBatchProcessor).flush(eq(0))
    verify(fixture.metricsBatchProcessor).flush(eq(0))
    verify(fixture.transport).close(eq(true))
    verify(fixture.loggerBatchProcessor).close(eq(true))
    verify(fixture.metricsBatchProcessor).close(eq(true))
  }

  @Test
  fun `when client is closed, client gets disabled`() {
    val sut = fixture.getSut()
    assertTrue(sut.isEnabled)
    sut.close()
    assertFalse(sut.isEnabled)
  }

  @Test
  fun `when beforeSend is set, callback is invoked`() {
    var invoked = false
    fixture.ichnoisOptions.setBeforeSend { e, _ ->
      invoked = true
      e
    }
    val sut = fixture.getSut()
    sut.captureEvent(IchnoisCoreEvent())
    assertTrue(invoked)
  }

  @Test
  fun `when beforeSend is returns null, event is dropped`() {
    fixture.ichnoisOptions.setBeforeSend { _: IchnoisCoreEvent, _: Any? -> null }
    val sut = fixture.getSut()
    val event = IchnoisCoreEvent()
    sut.captureEvent(event)
    verify(fixture.transport, never()).send(any(), anyOrNull())

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Error.category, 1)),
    )
  }

  @Test
  fun `when beforeSend is returns new instance, new instance is sent`() {
    val expected = IchnoisCoreEvent().apply { setTag("test", "test") }
    fixture.ichnoisOptions.setBeforeSend { _, _ -> expected }
    val sut = fixture.getSut()
    val actual = IchnoisCoreEvent()
    sut.captureEvent(actual)
    verify(fixture.transport)
      .send(
        check {
          val event = getEventFromData(it.items.first().data)
          assertEquals("test", event.tags!!["test"])
        },
        anyOrNull(),
      )
    verifyNoMoreInteractions(fixture.transport)
  }

  @Test
  fun `when beforeSend throws an exception, event is dropped`() {
    val exception = Exception("test")
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()

    fixture.ichnoisOptions.setBeforeSend { _, _ -> throw exception }
    fixture.ichnoisOptions.onDiscard = onDiscardMock
    val sut = fixture.getSut()
    val actual = IchnoisCoreEvent()
    val id = sut.captureEvent(actual)

    assertEquals(IchnoisCoreId.EMPTY_ID, id)

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.CALLBACK_ERROR.reason, DataCategory.Error.category, 1)),
    )
    verify(onDiscardMock).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Error, 1)
  }

  @Test
  fun `when beforeSend captures another event, the nested capture is dropped and does not recurse`() {
    var invocations = 0
    lateinit var sut: IchnoisCoreClient
    fixture.ichnoisOptions.setBeforeSend { e, _ ->
      invocations++
      sut.captureEvent(IchnoisCoreEvent())
      e
    }
    sut = fixture.getSut()

    sut.captureEvent(IchnoisCoreEvent())

    // Callback runs only for the outer event; the nested capture is dropped before its callback.
    assertEquals(1, invocations)
    verify(fixture.transport, times(1)).send(any(), anyOrNull())
  }

  @Test
  fun `when beforeSend captures a log, the nested log is dropped`() {
    val scope = createScope()
    fixture.ichnoisOptions.logs.isEnabled = true
    lateinit var sut: IchnoisCoreClient
    fixture.ichnoisOptions.setBeforeSend { e, _ ->
      sut.captureLog(
        IchnoisCoreLogEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "nested", IchnoisCoreLogLevel.WARN),
        scope,
      )
      e
    }
    sut = fixture.getSut()

    sut.captureEvent(IchnoisCoreEvent())

    // The shared guard spans capture types: a log emitted from beforeSend is dropped too.
    verify(fixture.loggerBatchProcessor, never()).add(any())
    verify(fixture.transport, times(1)).send(any(), anyOrNull())
  }

  @Test
  fun `when beforeSendLog logs again, the nested log is dropped and does not recurse`() {
    val scope = createScope()
    fixture.ichnoisOptions.logs.isEnabled = true
    var invocations = 0
    lateinit var sut: IchnoisCoreClient
    fixture.ichnoisOptions.logs.setBeforeSend { l ->
      invocations++
      sut.captureLog(
        IchnoisCoreLogEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "nested", IchnoisCoreLogLevel.WARN),
        scope,
      )
      l
    }
    sut = fixture.getSut()

    sut.captureLog(
      IchnoisCoreLogEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "outer", IchnoisCoreLogLevel.WARN),
      scope,
    )

    assertEquals(1, invocations)
    verify(fixture.loggerBatchProcessor, times(1)).add(any())
  }

  @Test
  fun `when beforeSend captures feedback before an event, the guard is not cleared prematurely`() {
    val scope = createScope()
    var invocations = 0
    lateinit var sut: IchnoisCoreClient
    fixture.ichnoisOptions.setBeforeSend { e, _ ->
      invocations++
      // Capturing feedback must not clear the re-entrancy guard for captures that follow it in the
      // same callback, otherwise the captureEvent below would recurse.
      sut.captureFeedback(Feedback("feedback"), null, scope)
      sut.captureEvent(IchnoisCoreEvent())
      e
    }
    sut = fixture.getSut()

    sut.captureEvent(IchnoisCoreEvent())

    assertEquals(1, invocations)
    verify(fixture.transport, times(1)).send(any(), anyOrNull())
  }

  @Test
  fun `when beforeSendFeedback captures feedback again, the nested capture is dropped and does not recurse`() {
    val scope = createScope()
    var invocations = 0
    lateinit var sut: IchnoisCoreClient
    fixture.ichnoisOptions.setBeforeSendFeedback { e, _ ->
      invocations++
      sut.captureFeedback(Feedback("nested"), null, scope)
      e
    }
    sut = fixture.getSut()

    sut.captureFeedback(Feedback("outer"), null, scope)

    assertEquals(1, invocations)
  }

  @Test
  fun `when beforeSendLog is set, callback is invoked`() {
    val scope = createScope()
    var invoked = false
    fixture.ichnoisOptions.logs.setBeforeSend { l ->
      invoked = true
      l
    }
    val sut = fixture.getSut()
    sut.captureLog(
      IchnoisCoreLogEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "message", IchnoisCoreLogLevel.WARN),
      scope,
    )
    assertTrue(invoked)
  }

  @Test
  fun `when beforeSendLog returns null, log is dropped`() {
    val scope = createScope()
    fixture.ichnoisOptions.logs.setBeforeSend { _: IchnoisCoreLogEvent -> null }
    val sut = fixture.getSut()
    sut.captureLog(
      IchnoisCoreLogEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "message", IchnoisCoreLogLevel.WARN),
      scope,
    )
    verify(fixture.transport, never()).send(any(), anyOrNull())

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(
        DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.LogItem.category, 1),
        DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.LogByte.category, 109),
      ),
    )
  }

  @Test
  fun `when beforeSendLog throws an exception, log is dropped`() {
    val scope = createScope()
    val exception = Exception("test")
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()

    fixture.ichnoisOptions.logs.setBeforeSend { _ -> throw exception }
    fixture.ichnoisOptions.onDiscard = onDiscardMock
    val sut = fixture.getSut()
    sut.captureLog(
      IchnoisCoreLogEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "message", IchnoisCoreLogLevel.WARN),
      scope,
    )

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(
        DiscardedEvent(DiscardReason.CALLBACK_ERROR.reason, DataCategory.LogItem.category, 1),
        DiscardedEvent(DiscardReason.CALLBACK_ERROR.reason, DataCategory.LogByte.category, 109),
      ),
    )
    verify(onDiscardMock).execute(DiscardReason.CALLBACK_ERROR, DataCategory.LogItem, 1)
    verify(onDiscardMock).execute(DiscardReason.CALLBACK_ERROR, DataCategory.LogByte, 109)
  }

  @Test
  fun `when log is dropped by event processor, log byte discard is recorded`() {
    val scope = createScope()
    val logEvent = IchnoisCoreLogEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "message", IchnoisCoreLogLevel.WARN)
    val logEventNumberOfBytes =
      JsonSerializationUtils.byteSizeOf(
        fixture.ichnoisOptions.serializer,
        fixture.ichnoisOptions.logger,
        logEvent,
      )
    scope.addEventProcessor(
      object : EventProcessor {
        override fun process(event: IchnoisCoreLogEvent): IchnoisCoreLogEvent? = null
      }
    )

    val sut = fixture.getSut()
    sut.captureLog(logEvent, scope)

    verify(fixture.loggerBatchProcessor, never()).add(any())
    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(
        DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.LogItem.category, 1),
        DiscardedEvent(
          DiscardReason.EVENT_PROCESSOR.reason,
          DataCategory.LogByte.category,
          logEventNumberOfBytes,
        ),
      ),
    )
  }

  @Test
  fun `throwing log processor drops log and stops callbacks`() {
    val scope = createScope()
    val logEvent = IchnoisCoreLogEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "message", IchnoisCoreLogLevel.WARN)
    val logEventNumberOfBytes =
      JsonSerializationUtils.byteSizeOf(
        fixture.ichnoisOptions.serializer,
        fixture.ichnoisOptions.logger,
        logEvent,
      )
    val throwingProcessor = mock<EventProcessor>()
    val nextProcessor = mock<EventProcessor>()
    val beforeSend = mock<IchnoisCoreOptions.Logs.BeforeSendLogCallback>()
    val onDiscard = mock<IchnoisCoreOptions.OnDiscardCallback>()
    whenever(throwingProcessor.process(any<IchnoisCoreLogEvent>()))
      .thenThrow(IllegalStateException("test"))
    scope.addEventProcessor(throwingProcessor)
    scope.addEventProcessor(nextProcessor)
    fixture.ichnoisOptions.logs.beforeSend = beforeSend
    fixture.ichnoisOptions.onDiscard = onDiscard

    fixture.getSut().captureLog(logEvent, scope)

    verify(nextProcessor, never()).process(any<IchnoisCoreLogEvent>())
    verify(beforeSend, never()).execute(any())
    verify(fixture.loggerBatchProcessor, never()).add(any())
    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(
        DiscardedEvent(DiscardReason.CALLBACK_ERROR.reason, DataCategory.LogItem.category, 1),
        DiscardedEvent(
          DiscardReason.CALLBACK_ERROR.reason,
          DataCategory.LogByte.category,
          logEventNumberOfBytes,
        ),
      ),
    )
    verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.LogItem, 1)
    verify(onDiscard)
      .execute(DiscardReason.CALLBACK_ERROR, DataCategory.LogByte, logEventNumberOfBytes)
  }

  @Test
  fun `when beforeSendLog is returns new instance, new instance is sent`() {
    val scope = createScope()
    val expected =
      IchnoisCoreLogEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "expected message", IchnoisCoreLogLevel.WARN)
    fixture.ichnoisOptions.logs.setBeforeSend { _ -> expected }
    val sut = fixture.getSut()
    val batchProcessor = mock<ILoggerBatchProcessor>()
    sut.injectForField("loggerBatchProcessor", batchProcessor)
    val actual =
      IchnoisCoreLogEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "actual message", IchnoisCoreLogLevel.WARN)
    sut.captureLog(actual, scope)
    verify(batchProcessor).add(check { assertEquals("expected message", it.body) })
    verifyNoMoreInteractions(batchProcessor)
  }

  @Test
  fun `when beforeSendMetric is set, callback is invoked`() {
    val scope = createScope()
    var invoked = false
    fixture.ichnoisOptions.metrics.setBeforeSend { m, hint ->
      invoked = true
      m
    }
    val sut = fixture.getSut()
    sut.captureMetric(
      IchnoisCoreMetricsEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "name", "gauge", 123.0),
      scope,
      null,
    )
    assertTrue(invoked)
  }

  @Test
  fun `when beforeSendMetric returns null, metric is dropped`() {
    val scope = createScope()
    fixture.ichnoisOptions.metrics.setBeforeSend { _: IchnoisCoreMetricsEvent, hint -> null }
    val sut = fixture.getSut()
    sut.captureMetric(
      IchnoisCoreMetricsEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "name", "gauge", 123.0),
      scope,
      null,
    )
    verify(fixture.transport, never()).send(any(), anyOrNull())

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(
        DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.TraceMetric.category, 1),
        DiscardedEvent(
          DiscardReason.BEFORE_SEND.reason,
          DataCategory.TraceMetricByte.category,
          120,
        ),
      ),
    )
  }

  @Test
  fun `when beforeSendMetric throws an exception, metric is dropped`() {
    val scope = createScope()
    val exception = Exception("test")
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()

    fixture.ichnoisOptions.metrics.setBeforeSend { _, _ -> throw exception }
    fixture.ichnoisOptions.onDiscard = onDiscardMock
    val sut = fixture.getSut()
    sut.captureMetric(
      IchnoisCoreMetricsEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "name", "gauge", 123.0),
      scope,
      null,
    )

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(
        DiscardedEvent(DiscardReason.CALLBACK_ERROR.reason, DataCategory.TraceMetric.category, 1),
        DiscardedEvent(
          DiscardReason.CALLBACK_ERROR.reason,
          DataCategory.TraceMetricByte.category,
          120,
        ),
      ),
    )
    verify(onDiscardMock).execute(DiscardReason.CALLBACK_ERROR, DataCategory.TraceMetric, 1)
    verify(onDiscardMock).execute(DiscardReason.CALLBACK_ERROR, DataCategory.TraceMetricByte, 120)
  }

  @Test
  fun `when metric is dropped by event processor, metric byte discard is recorded`() {
    val scope = createScope()
    val metricsEvent = IchnoisCoreMetricsEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "name", "gauge", 123.0)
    val metricsEventNumberOfBytes =
      JsonSerializationUtils.byteSizeOf(
        fixture.ichnoisOptions.serializer,
        fixture.ichnoisOptions.logger,
        metricsEvent,
      )
    scope.addEventProcessor(
      object : EventProcessor {
        override fun process(event: IchnoisCoreMetricsEvent, hint: Hint): IchnoisCoreMetricsEvent? = null
      }
    )

    val sut = fixture.getSut()
    sut.captureMetric(metricsEvent, scope, null)

    verify(fixture.metricsBatchProcessor, never()).add(any())
    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(
        DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.TraceMetric.category, 1),
        DiscardedEvent(
          DiscardReason.EVENT_PROCESSOR.reason,
          DataCategory.TraceMetricByte.category,
          metricsEventNumberOfBytes,
        ),
      ),
    )
  }

  @Test
  fun `throwing metric processor drops metric and stops callbacks`() {
    val scope = createScope()
    val metricsEvent = IchnoisCoreMetricsEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "name", "gauge", 123.0)
    val metricsEventNumberOfBytes =
      JsonSerializationUtils.byteSizeOf(
        fixture.ichnoisOptions.serializer,
        fixture.ichnoisOptions.logger,
        metricsEvent,
      )
    val throwingProcessor = mock<EventProcessor>()
    val nextProcessor = mock<EventProcessor>()
    val beforeSend = mock<IchnoisCoreOptions.Metrics.BeforeSendMetricCallback>()
    val onDiscard = mock<IchnoisCoreOptions.OnDiscardCallback>()
    whenever(throwingProcessor.process(any<IchnoisCoreMetricsEvent>(), anyOrNull()))
      .thenThrow(IllegalStateException("test"))
    scope.addEventProcessor(throwingProcessor)
    scope.addEventProcessor(nextProcessor)
    fixture.ichnoisOptions.metrics.beforeSend = beforeSend
    fixture.ichnoisOptions.onDiscard = onDiscard

    fixture.getSut().captureMetric(metricsEvent, scope, null)

    verify(nextProcessor, never()).process(any<IchnoisCoreMetricsEvent>(), anyOrNull())
    verify(beforeSend, never()).execute(any(), anyOrNull())
    verify(fixture.metricsBatchProcessor, never()).add(any())
    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(
        DiscardedEvent(
          DiscardReason.CALLBACK_ERROR.reason,
          DataCategory.TraceMetric.category,
          1,
        ),
        DiscardedEvent(
          DiscardReason.CALLBACK_ERROR.reason,
          DataCategory.TraceMetricByte.category,
          metricsEventNumberOfBytes,
        ),
      ),
    )
    verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.TraceMetric, 1)
    verify(onDiscard)
      .execute(
        DiscardReason.CALLBACK_ERROR,
        DataCategory.TraceMetricByte,
        metricsEventNumberOfBytes,
      )
  }

  @Test
  fun `when beforeSendMetric is returns new instance, new instance is sent`() {
    val scope = createScope()
    val expected =
      IchnoisCoreMetricsEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "expected name", "gauge", 123.0)
    fixture.ichnoisOptions.metrics.setBeforeSend { _, hint -> expected }
    val sut = fixture.getSut()
    val batchProcessor = mock<IMetricsBatchProcessor>()
    sut.injectForField("metricsBatchProcessor", batchProcessor)
    val actual =
      IchnoisCoreMetricsEvent(IchnoisCoreId(), IchnoisCoreNanotimeDate(), "actual name", "counter", 97.0)
    sut.captureMetric(actual, scope, null)
    verify(batchProcessor).add(check { assertEquals("expected name", it.name) })
    verifyNoMoreInteractions(batchProcessor)
  }

  @Test
  fun `when event captured with hint, hint passed to connection`() {
    val event = IchnoisCoreEvent()
    fixture.ichnoisOptions.environment = "not to be applied"
    val sut = fixture.getSut()

    val hints = HintUtils.createWithTypeCheckHint(Object())
    sut.captureEvent(event, hints)
    verify(fixture.transport).send(any(), eq(hints))
  }

  @Test
  fun `when captureMessage is called, ichnois event contains formatted message`() {
    var sentEvent: IchnoisCoreEvent? = null
    fixture.ichnoisOptions.setBeforeSend { e, _ ->
      sentEvent = e
      e
    }
    val sut = fixture.getSut()
    val actual = "actual message"
    sut.callMethod(
      "captureMessage",
      parameterTypes = arrayOf(String::class.java, IchnoisCoreLevel::class.java, IScope::class.java),
      actual,
      null,
      null,
    )
    assertEquals(actual, sentEvent!!.message!!.formatted)
  }

  @Test
  fun `when captureMessage is called, ichnois event contains level`() {
    var sentEvent: IchnoisCoreEvent? = null
    fixture.ichnoisOptions.setBeforeSend { e, _ ->
      sentEvent = e
      e
    }
    val sut = fixture.getSut()
    sut.callMethod(
      "captureMessage",
      parameterTypes = arrayOf(String::class.java, IchnoisCoreLevel::class.java),
      null,
      IchnoisCoreLevel.DEBUG,
    )
    assertEquals(IchnoisCoreLevel.DEBUG, sentEvent!!.level)
  }

  @Test
  fun `when event has release, value from options not applied`() {
    val event = IchnoisCoreEvent()
    val expected = "original"
    fixture.ichnoisOptions.environment = "not to be applied"
    event.release = expected
    val sut = fixture.getSut()
    sut.captureEvent(event)
    assertEquals(expected, event.release)
  }

  @Test
  fun `when event doesn't have release, value from options applied`() {
    val event = IchnoisCoreEvent()
    val expected = "original"
    fixture.ichnoisOptions.release = expected
    val sut = fixture.getSut()
    sut.captureEvent(event)
    assertEquals(expected, event.release)
  }

  @Test
  fun `when event has environment, value from options not applied`() {
    val event = IchnoisCoreEvent()
    val expected = "original"
    fixture.ichnoisOptions.environment = "not to be applied"
    event.environment = expected
    val sut = fixture.getSut()
    sut.captureEvent(event)
    assertEquals(expected, event.environment)
  }

  @Test
  fun `when event doesn't have environment, value from options applied`() {
    val event = IchnoisCoreEvent()
    val expected = "original"
    fixture.ichnoisOptions.environment = expected
    val sut = fixture.getSut()
    sut.captureEvent(event)
    assertEquals(expected, event.environment)
  }

  @Test
  fun `when captureEvent with scope, event should have its data if not set`() {
    val event = IchnoisCoreEvent()
    val scope = createScope()

    val sut = fixture.getSut()

    sut.captureEvent(event, scope)
    assertEquals("message", event.breadcrumbs!![0].message)
    assertNotNull(event.extras) { assertEquals("extra", it["extra"]) }
    assertEquals("tags", event.tags!!["tags"])
    assertNotNull(event.fingerprints) { assertEquals("fp", it[0]) }
    assertNotNull(event.user) { assertEquals("id", it.id) }
    assertEquals(IchnoisCoreLevel.FATAL, event.level)
    assertNotNull(event.request) { assertEquals("post", it.method) }
  }

  @Test
  fun `when captureEvent applies scope tags and extras, event map containers are copied`() {
    val event = IchnoisCoreEvent()
    val scope = createScope()

    val sut = fixture.getSut()

    sut.captureEvent(event, scope)
    val eventTags = event.tags!!
    val eventExtras = event.extras!!

    scope.setTag("newTag", "newValue")
    scope.setExtra("newExtra", "newValue")

    assertFalse(eventTags.containsKey("newTag"))
    assertFalse(eventExtras.containsKey("newExtra"))
  }

  @Test
  fun `when breadcrumbs are not empty, sort them out by date`() {
    val b1 = Breadcrumb(DateUtils.getDateTime("2020-03-27T08:52:58.001Z"))
    val b2 = Breadcrumb(DateUtils.getDateTime("2020-03-27T08:52:58.002Z"))
    val scope =
      Scope(IchnoisCoreOptions()).apply {
        addBreadcrumb(b2)
        addBreadcrumb(b1)
      }

    val sut = fixture.getSut()

    val b3 = Breadcrumb(DateUtils.getDateTime("2020-03-27T08:52:58.003Z"))
    val event = IchnoisCoreEvent().apply { breadcrumbs = mutableListOf(b3) }

    sut.captureEvent(event, scope)

    assertNotNull(event.breadcrumbs) {
      assertSame(b1, it[0])
      assertSame(b2, it[1])
      assertSame(b3, it[2])
    }
  }

  @Test
  fun `when event is cached, scope breadcrumbs are not merged into the event breadcrumbs`() {
    val scopeCrumb = Breadcrumb(DateUtils.getDateTime("2020-03-27T08:52:58.001Z"))
    val scope = Scope(IchnoisCoreOptions()).apply { addBreadcrumb(scopeCrumb) }

    val sut = fixture.getSut()

    val eventCrumb = Breadcrumb(DateUtils.getDateTime("2020-03-27T08:52:58.003Z"))
    val event = IchnoisCoreEvent().apply { breadcrumbs = mutableListOf(eventCrumb) }

    val hints = HintUtils.createWithTypeCheckHint(CachedHint())
    sut.captureEvent(event, scope, hints)

    assertNotNull(event.breadcrumbs) {
      assertEquals(1, it.size)
      assertSame(eventCrumb, it[0])
    }
  }

  @Test
  fun `when transaction is cached, scope breadcrumbs are not merged into the transaction breadcrumbs`() {
    val sut = fixture.getSut()
    val scope = Scope(fixture.ichnoisOptions)
    scope.addBreadcrumb(Breadcrumb("from scope"))

    val transaction = IchnoisCoreTransaction(fixture.ichnoisTracer)
    transaction.breadcrumbs = mutableListOf(Breadcrumb("from transaction"))

    val hints = HintUtils.createWithTypeCheckHint(CachedHint())
    sut.captureTransaction(transaction, scope, hints)

    verify(fixture.transport)
      .send(
        check { envelope ->
          val sent = envelope.items.first().getTransaction(fixture.ichnoisOptions.serializer)
          assertNotNull(sent) {
            assertNotNull(it.breadcrumbs) { breadcrumbs ->
              assertEquals(1, breadcrumbs.size)
              assertEquals("from transaction", breadcrumbs.first().message)
            }
          }
        },
        anyOrNull(),
      )
  }

  @Test
  fun `when captureEvent with scope, event data has priority over scope but level and it should append extras, tags and breadcrumbs`() {
    val event = createEvent()

    val scope = createScope()

    val sut = fixture.getSut()

    sut.captureEvent(event, scope)

    // breadcrumbs are appending
    assertNotNull(event.breadcrumbs) {
      assertEquals("eventMessage", it[0].message)
      assertEquals("message", it[1].message)
    }

    // extras are appending
    assertNotNull(event.extras) {
      assertEquals("eventExtra", it["eventExtra"])
      assertEquals("extra", it["extra"])
    }

    // tags are appending
    assertNotNull(event.tags) {
      assertEquals("eventTag", it["eventTag"])
      assertEquals("tags", it["tags"])
    }

    // fingerprint is replaced
    assertNotNull(event.fingerprints) {
      assertEquals("eventFp", it[0])
      assertEquals(1, it.size)
    }

    assertEquals("eventTransaction", event.transaction)

    assertNotNull(event.user) { assertEquals("eventId", it.id) }

    assertEquals(IchnoisCoreLevel.FATAL, event.level)
  }

  @Test
  fun `when captureEvent with scope, event extras and tags are only append if key is absent`() {
    val event = createEvent()

    val scope = createScope()
    scope.setExtra("eventExtra", "extra")
    scope.setTag("eventTag", "tags")

    val sut = fixture.getSut()

    sut.captureEvent(event, scope)

    // extras are appending
    assertNotNull(event.extras) { assertEquals("eventExtra", it["eventExtra"]) }

    // tags are appending
    assertNotNull(event.tags) { assertEquals("eventTag", it["eventTag"]) }
  }

  @Test
  fun `when captureEvent with scope, event should have its level if set`() {
    val event = IchnoisCoreEvent()
    event.level = IchnoisCoreLevel.DEBUG
    val scope = createScope()

    val sut = fixture.getSut()

    sut.captureEvent(event, scope)
    assertEquals(IchnoisCoreLevel.FATAL, event.level)
  }

  @Test
  fun `when captureEvent with sampling, some events not captured`() {
    fixture.ichnoisOptions.sampleRate = 0.000000001
    val sut = fixture.getSut()

    val allEvents = 10
    (0..allEvents).forEach { _ -> sut.captureEvent(IchnoisCoreEvent()) }
    assertTrue(allEvents > mockingDetails(fixture.transport).invocations.size)
  }

  @Test
  fun `events dropped by sampling are recorded as lost`() {
    fixture.ichnoisOptions.sampleRate = 0.000000001
    val sut = fixture.getSut()

    sut.captureEvent(IchnoisCoreEvent())

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.SAMPLE_RATE.reason, DataCategory.Error.category, 1)),
    )
  }

  @Test
  fun `when captureEvent without sampling, all events are captured`() {
    fixture.ichnoisOptions.sampleRate = null
    val sut = fixture.getSut()

    val allEvents = 10
    (0..allEvents).forEach { _ -> sut.captureEvent(IchnoisCoreEvent()) }
    assertEquals(
      allEvents,
      mockingDetails(fixture.transport).invocations.size - 1,
    ) // 1 extra invocation outside .send()
  }

  @Test
  fun `when captureEvent with attachments`() {
    val event = createEvent()

    fixture.getSut().captureEvent(event, createScopeWithAttachments())

    verifyAttachmentsInEnvelope(event.eventId)
  }

  @Test
  fun `when captureUserFeedback with empty id, envelope is not sent`() {
    val sut = fixture.getSut()

    sut.captureUserFeedback(UserFeedback(IchnoisCoreId.EMPTY_ID))

    verify(fixture.transport, never()).send(any(), anyOrNull())
  }

  @Test
  fun `when captureUserFeedback, envelope is sent`() {
    val sut = fixture.getSut()

    sut.captureUserFeedback(userFeedback)

    verify(fixture.transport)
      .send(
        check { actual ->
          assertEquals(userFeedback.eventId, actual.header.eventId)
          assertEquals(fixture.ichnoisOptions.sdkVersion, actual.header.sdkVersion)

          assertEquals(1, actual.items.count())
          val item = actual.items.first()
          assertEquals(IchnoisCoreItemType.UserFeedback, item.header.type)
          assertEquals("application/json", item.header.contentType)

          assertEnvelopeItemDataForUserFeedback(item)
        }
      )
  }

  private fun assertEnvelopeItemDataForUserFeedback(item: IchnoisCoreEnvelopeItem) {
    val stream = ByteArrayOutputStream()
    val writer = stream.bufferedWriter(Charset.forName("UTF-8"))
    fixture.ichnoisOptions.serializer.serialize(userFeedback, writer)
    val expectedData = stream.toByteArray()
    assertTrue(Arrays.equals(expectedData, item.data))
  }

  @Test
  fun `when captureUserFeedback and connection throws, log exception`() {
    val sut = fixture.getSut()

    val exception = IOException("No connection")
    whenever(fixture.transport.send(any())).thenThrow(exception)

    val logger = mock<ILogger>()
    fixture.ichnoisOptions.setLogger(logger)

    sut.captureUserFeedback(userFeedback)

    verify(logger)
      .log(
        IchnoisCoreLevel.WARNING,
        exception,
        "Capturing user feedback %s failed.",
        userFeedback.eventId,
      )
  }

  @Test
  fun `when captureCheckIn, envelope is sent`() {
    val sut = fixture.getSut()

    sut.captureCheckIn(checkIn, null, null)

    verify(fixture.transport)
      .send(
        check { actual ->
          assertEquals(checkIn.checkInId, actual.header.eventId)
          assertEquals(fixture.ichnoisOptions.sdkVersion, actual.header.sdkVersion)

          assertEquals(1, actual.items.count())
          val item = actual.items.first()
          assertEquals(IchnoisCoreItemType.CheckIn, item.header.type)
          assertEquals("application/json", item.header.contentType)

          assertEnvelopeItemDataForCheckIn(item)
        },
        any<Hint>(),
      )
  }

  @Test
  fun `when captureCheckIn, envelope is sent if ignored slug does not match`() {
    val sut = fixture.getSut { options -> options.setIgnoredCheckIns(listOf("non_matching_slug")) }

    sut.captureCheckIn(checkIn, null, null)

    verify(fixture.transport)
      .send(
        check { actual ->
          assertEquals(checkIn.checkInId, actual.header.eventId)
          assertEquals(fixture.ichnoisOptions.sdkVersion, actual.header.sdkVersion)

          assertEquals(1, actual.items.count())
          val item = actual.items.first()
          assertEquals(IchnoisCoreItemType.CheckIn, item.header.type)
          assertEquals("application/json", item.header.contentType)

          assertEnvelopeItemDataForCheckIn(item)
        },
        any<Hint>(),
      )
  }

  @Test
  fun `when captureCheckIn, envelope is not sent if slug is ignored`() {
    val sut = fixture.getSut { options -> options.setIgnoredCheckIns(listOf("some_slug")) }

    sut.captureCheckIn(checkIn, null, null)

    verify(fixture.transport, never()).send(any(), any<Hint>())
  }

  private fun assertEnvelopeItemDataForCheckIn(item: IchnoisCoreEnvelopeItem) {
    val stream = ByteArrayOutputStream()
    val writer = stream.bufferedWriter(Charset.forName("UTF-8"))
    fixture.ichnoisOptions.serializer.serialize(checkIn, writer)
    val expectedData = stream.toByteArray()
    assertTrue(Arrays.equals(expectedData, item.data))
  }

  @Test
  fun `when captureCheckIn and connection throws, log exception`() {
    val sut = fixture.getSut()

    val exception = IOException("No connection")
    whenever(fixture.transport.send(any(), any())).thenThrow(exception)

    val logger = mock<ILogger>()
    fixture.ichnoisOptions.setLogger(logger)

    sut.captureCheckIn(checkIn, null, null)

    verify(logger)
      .log(IchnoisCoreLevel.WARNING, exception, "Capturing check-in %s failed.", checkIn.checkInId)
  }

  @Test
  fun `when hint is Cached, scope is not applied`() {
    val sut = fixture.getSut()

    val event = IchnoisCoreEvent()
    val scope = Scope(IchnoisCoreOptions())
    scope.level = IchnoisCoreLevel.FATAL

    val hints = HintUtils.createWithTypeCheckHint(mock<Cached>())
    sut.captureEvent(event, scope, hints)

    assertNotEquals(scope.level, event.level)
  }

  @Test
  fun `when hint is not Cached, scope is applied`() {
    val sut = fixture.getSut()

    val event = IchnoisCoreEvent()
    val scope = Scope(IchnoisCoreOptions())
    scope.level = IchnoisCoreLevel.FATAL

    val hints = HintUtils.createWithTypeCheckHint(Object())
    sut.captureEvent(event, scope, hints)

    assertEquals(scope.level, event.level)
  }

  @Test
  fun `when hint is ApplyScopeData, scope is applied`() {
    val sut = fixture.getSut()

    val event = IchnoisCoreEvent()
    val scope = Scope(IchnoisCoreOptions())
    scope.level = IchnoisCoreLevel.FATAL

    val hints = HintUtils.createWithTypeCheckHint(mock<ApplyScopeData>())
    sut.captureEvent(event, scope, hints)

    assertEquals(scope.level, event.level)
  }

  @Test
  fun `when hint is Cached but also ApplyScopeData, scope is applied`() {
    val sut = fixture.getSut()

    val event = IchnoisCoreEvent()
    val scope = Scope(IchnoisCoreOptions())
    scope.level = IchnoisCoreLevel.FATAL

    val hints = HintUtils.createWithTypeCheckHint(CustomCachedApplyScopeDataHint())
    sut.captureEvent(event, scope, hints)

    assertEquals(scope.level, event.level)
  }

  @Test
  fun `when hint is Cached, scope attachments are not added to avoid duplication`() {
    val sut = fixture.getSut()

    val event = createEvent()
    val scope = createScopeWithAttachments()

    val hints = HintUtils.createWithTypeCheckHint(CustomCachedApplyScopeDataHint())
    sut.captureEvent(event, scope, hints)

    verify(fixture.transport)
      .send(
        check { actual ->
          assertEquals(0, actual.items.count { it.header.type == IchnoisCoreItemType.Attachment })
        },
        anyOrNull(),
      )
  }

  @Test
  fun `when transport factory is NoOp, it should initialize it`() {
    fixture.ichnoisOptions.setTransportFactory(NoOpTransportFactory.getInstance())
    fixture.getSut()
    assertTrue(fixture.ichnoisOptions.transportFactory is AsyncHttpTransportFactory)
  }

  @Test
  fun `when transport factory is set on options, it should use the custom transport factory`() {
    val ichnoisOptions: IchnoisCoreOptions = IchnoisCoreOptions().apply { dsn = dsnString }
    val transportFactory = mock<ITransportFactory>()
    ichnoisOptions.setTransportFactory(transportFactory)

    IchnoisCoreClient(ichnoisOptions)

    assertEquals(transportFactory, ichnoisOptions.transportFactory)
  }

  @Test
  fun `when transport gate is set on options, it should use the custom transport gate`() {
    val ichnoisOptions: IchnoisCoreOptions = IchnoisCoreOptions().apply { dsn = dsnString }
    val transportGate = CustomTransportGate()
    ichnoisOptions.setTransportGate(transportGate)

    IchnoisCoreClient(ichnoisOptions)

    assertEquals(transportGate, ichnoisOptions.transportGate)
  }

  @Test
  fun `when transport gate is null, it should init an always on transport gate`() {
    val ichnoisOptions: IchnoisCoreOptions = IchnoisCoreOptions().apply { dsn = dsnString }

    IchnoisCoreClient(ichnoisOptions)

    assertNotNull(ichnoisOptions.transportGate)
    assertTrue(ichnoisOptions.transportGate.isConnected)
  }

  @Test
  fun `when scope has event processors, they should be applied`() {
    val event = IchnoisCoreEvent()
    val scope = createScope()
    val processor = mock<EventProcessor>()
    whenever(processor.process(any<IchnoisCoreEvent>(), anyOrNull())).thenReturn(event)
    scope.addEventProcessor(processor)

    val sut = fixture.getSut()

    sut.captureEvent(event, scope)
    verify(processor).process(eq(event), anyOrNull())
  }

  @Test
  fun `when scope has event processors, apply for transactions`() {
    val transaction = IchnoisCoreTransaction(fixture.ichnoisTracer)
    val scope = createScope()
    val processor = mock<EventProcessor>()
    whenever(processor.process(any<IchnoisCoreTransaction>(), anyOrNull())).thenReturn(transaction)
    scope.addEventProcessor(processor)

    val sut = fixture.getSut()

    sut.captureTransaction(transaction, scope, null)
    verify(processor).process(eq(transaction), anyOrNull())
  }

  @Test
  fun `when options have event processors, they should be applied`() {
    val processor = mock<EventProcessor>()
    fixture.ichnoisOptions.addEventProcessor(processor)

    val event = IchnoisCoreEvent()

    fixture.getSut().captureEvent(event)
    verify(processor).process(eq(event), anyOrNull())
  }

  @Test
  fun `when options have event processors, apply for transactions`() {
    val processor = mock<EventProcessor>()
    fixture.ichnoisOptions.addEventProcessor(processor)

    val transaction = IchnoisCoreTransaction(fixture.ichnoisTracer)

    fixture.getSut().captureTransaction(transaction, fixture.ichnoisTracer.traceContext())
    verify(processor).process(eq(transaction), anyOrNull())
  }

  @Test
  fun `transaction dropped by global event processor is recorded`() {
    fixture.ichnoisOptions.addEventProcessor(DropEverythingEventProcessor())

    val transaction = IchnoisCoreTransaction(fixture.ichnoisTracer)

    fixture.getSut().captureTransaction(transaction, fixture.ichnoisTracer.traceContext())

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(
        DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Transaction.category, 1),
        DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Span.category, 2),
      ),
    )
  }

  @Test
  fun `throwing transaction processor drops transaction and stops callbacks`() {
    val throwingProcessor = mock<EventProcessor>()
    val nextProcessor = mock<EventProcessor>()
    val beforeSend = mock<IchnoisCoreOptions.BeforeSendTransactionCallback>()
    val onDiscard = mock<IchnoisCoreOptions.OnDiscardCallback>()
    whenever(throwingProcessor.process(any<IchnoisCoreTransaction>(), anyOrNull()))
      .thenThrow(IllegalStateException("test"))
    fixture.ichnoisOptions.addEventProcessor(throwingProcessor)
    fixture.ichnoisOptions.addEventProcessor(nextProcessor)
    fixture.ichnoisOptions.beforeSendTransaction = beforeSend
    fixture.ichnoisOptions.onDiscard = onDiscard

    val id =
      fixture
        .getSut()
        .captureTransaction(
          IchnoisCoreTransaction(fixture.ichnoisTracer),
          fixture.ichnoisTracer.traceContext(),
        )

    assertThat(id).isEqualTo(IchnoisCoreId.EMPTY_ID)
    verify(nextProcessor, never()).process(any<IchnoisCoreTransaction>(), anyOrNull())
    verify(beforeSend, never()).execute(any(), anyOrNull())
    verify(fixture.transport, never()).send(any(), anyOrNull())
    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(
        DiscardedEvent(DiscardReason.CALLBACK_ERROR.reason, DataCategory.Transaction.category, 1),
        DiscardedEvent(DiscardReason.CALLBACK_ERROR.reason, DataCategory.Span.category, 2),
      ),
    )
    verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Transaction, 1)
    verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Span, 2)
  }

  @Test
  fun `transaction dropped by ignoredTransactions is recorded`() {
    fixture.ichnoisOptions.setIgnoredTransactions(listOf("a-transaction"))

    val transaction = IchnoisCoreTransaction(fixture.ichnoisTracer)

    val eventId =
      fixture.getSut().captureTransaction(transaction, fixture.ichnoisTracer.traceContext())

    verify(fixture.transport, never()).send(any(), anyOrNull())

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(
        DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Transaction.category, 1),
        DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Span.category, 2),
      ),
    )

    assertEquals(IchnoisCoreId.EMPTY_ID, eventId)
  }

  @Test
  fun `transaction dropped by ignoredTransactions with regex is recorded`() {
    fixture.ichnoisOptions.setIgnoredTransactions(listOf("a.*action"))

    val transaction = IchnoisCoreTransaction(fixture.ichnoisTracer)

    val eventId =
      fixture.getSut().captureTransaction(transaction, fixture.ichnoisTracer.traceContext())

    verify(fixture.transport, never()).send(any(), anyOrNull())

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(
        DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Transaction.category, 1),
        DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Span.category, 2),
      ),
    )

    assertEquals(IchnoisCoreId.EMPTY_ID, eventId)
  }

  @Test
  fun `backfillable events are only wired through backfilling processors`() {
    val backfillingProcessor = mock<BackfillingEventProcessor>()
    val nonBackfillingProcessor = mock<EventProcessor>()
    fixture.ichnoisOptions.addEventProcessor(backfillingProcessor)
    fixture.ichnoisOptions.addEventProcessor(nonBackfillingProcessor)

    val event = IchnoisCoreEvent()
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())

    fixture.getSut().captureEvent(event, hint)

    verify(backfillingProcessor).process(eq(event), eq(hint))
    verify(nonBackfillingProcessor, never()).process(any<IchnoisCoreEvent>(), anyOrNull())
  }

  @Test
  fun `scope is not applied to backfillable events`() {
    val event = IchnoisCoreEvent()
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())
    val scope = createScope()

    fixture.getSut().captureEvent(event, scope, hint)

    assertNull(event.user)
    assertNull(event.level)
    assertNull(event.breadcrumbs)
    assertNull(event.request)
  }

  @Test
  fun `tracingContext values are derived from backfillable events`() {
    val traceId = IchnoisCoreId(UUID.randomUUID())
    val event =
      IchnoisCoreEvent().apply {
        environment = "release"
        release = "io.ichnois.samples@22.1.1"
        contexts[Contexts.REPLAY_ID] = "64cf554cc8d74c6eafa3e08b7c984f6d"
        contexts.setTrace(SpanContext(traceId, SpanId(), "ui.load", null, null))
        transaction = "MainActivity"
      }
    val hint = HintUtils.createWithTypeCheckHint(BackfillableHint())
    val scope = createScope()

    fixture.getSut().captureEvent(event, scope, hint)

    verify(fixture.transport)
      .send(
        check {
          assertEquals("release", it.header.traceContext!!.environment)
          assertEquals("io.ichnois.samples@22.1.1", it.header.traceContext!!.release)
          assertEquals(traceId, it.header.traceContext!!.traceId)
          assertEquals("MainActivity", it.header.traceContext!!.transaction)
          assertEquals(
            IchnoisCoreId("64cf554cc8d74c6eafa3e08b7c984f6d"),
            it.header.traceContext!!.replayId,
          )
        },
        anyOrNull(),
      )
  }

  @Test
  fun `non-backfillable events are only wired through regular processors`() {
    val backfillingProcessor = mock<BackfillingEventProcessor>()
    val nonBackfillingProcessor = mock<EventProcessor>()
    fixture.ichnoisOptions.addEventProcessor(backfillingProcessor)
    fixture.ichnoisOptions.addEventProcessor(nonBackfillingProcessor)

    val event = IchnoisCoreEvent()

    fixture.getSut().captureEvent(event)

    verify(backfillingProcessor, never()).process(any<IchnoisCoreEvent>(), anyOrNull())
    verify(nonBackfillingProcessor).process(eq(event), anyOrNull())
  }

  @Test
  fun `transaction dropped by beforeSendTransaction is recorded`() {
    fixture.ichnoisOptions.setBeforeSendTransaction { transaction, hint -> null }

    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    fixture.ichnoisOptions.onDiscard = onDiscardMock

    val transaction = IchnoisCoreTransaction(fixture.ichnoisTracer)

    fixture.getSut().captureTransaction(transaction, fixture.ichnoisTracer.traceContext())

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(
        DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Transaction.category, 1),
        DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Span.category, 2),
      ),
    )

    verify(onDiscardMock, times(1)).execute(DiscardReason.BEFORE_SEND, DataCategory.Transaction, 1)
    verify(onDiscardMock).execute(DiscardReason.BEFORE_SEND, DataCategory.Span, 2)
  }

  @Test
  fun `transaction dropped by scope event processor is recorded`() {
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    fixture.ichnoisOptions.onDiscard = onDiscardMock

    val transaction = IchnoisCoreTransaction(fixture.ichnoisTracer)
    val scope = createScope()
    scope.addEventProcessor(DropEverythingEventProcessor())

    val sut = fixture.getSut()

    sut.captureTransaction(transaction, scope, null)

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(
        DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Transaction.category, 1),
        DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Span.category, 2),
      ),
    )

    verify(onDiscardMock, times(1))
      .execute(DiscardReason.EVENT_PROCESSOR, DataCategory.Transaction, 1)
    verify(onDiscardMock).execute(DiscardReason.EVENT_PROCESSOR, DataCategory.Span, 2)
  }

  @Test
  fun `span dropped by event processor is recorded`() {
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    fixture.ichnoisOptions.onDiscard = onDiscardMock

    fixture.ichnoisTracer.startChild("dropped span", "span1").finish()
    fixture.ichnoisTracer.startChild("dropped span", "span2").finish()
    val transaction = IchnoisCoreTransaction(fixture.ichnoisTracer)
    val scope = createScope()
    scope.addEventProcessor(
      object : EventProcessor {
        override fun process(transaction: IchnoisCoreTransaction, hint: Hint): IchnoisCoreTransaction? {
          // we are removing span1 and a-span from the fixture
          transaction.spans.removeIf { it.description != "span2" }
          return transaction
        }
      }
    )

    fixture.getSut().captureTransaction(transaction, scope, null)

    verify(fixture.transport).send(any(), anyOrNull())

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Span.category, 2)),
    )

    verify(onDiscardMock, times(1)).execute(DiscardReason.EVENT_PROCESSOR, DataCategory.Span, 2)
  }

  @Test
  fun `event dropped by global event processor is recorded`() {
    fixture.ichnoisOptions.addEventProcessor(DropEverythingEventProcessor())
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    fixture.ichnoisOptions.onDiscard = onDiscardMock

    val event = IchnoisCoreEvent()

    fixture.getSut().captureEvent(event)

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Error.category, 1)),
    )

    verify(onDiscardMock, times(1)).execute(DiscardReason.EVENT_PROCESSOR, DataCategory.Error, 1)
  }

  @Test
  fun `event dropped by scope event processor is recorded`() {
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    fixture.ichnoisOptions.onDiscard = onDiscardMock

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Error.category, 0)),
    )
    val event = IchnoisCoreEvent()
    val scope = createScope()
    scope.addEventProcessor(DropEverythingEventProcessor())

    val sut = fixture.getSut()

    sut.captureEvent(event, scope)

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Error.category, 1)),
    )

    verify(onDiscardMock, times(1)).execute(DiscardReason.EVENT_PROCESSOR, DataCategory.Error, 1)
  }

  @Test
  fun `when beforeSendTransaction is set, callback is invoked`() {
    var invoked = false
    fixture.ichnoisOptions.setBeforeSendTransaction { t, _ ->
      invoked = true
      t
    }

    val transaction = IchnoisCoreTransaction(fixture.ichnoisTracer)
    fixture.getSut().captureTransaction(transaction, fixture.ichnoisTracer.traceContext())

    assertTrue(invoked)
  }

  @Test
  fun `when beforeSendTransaction is returns null, event is dropped`() {
    fixture.ichnoisOptions.setBeforeSendTransaction { _: IchnoisCoreTransaction, _: Any? -> null }
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    fixture.ichnoisOptions.onDiscard = onDiscardMock

    val transaction = IchnoisCoreTransaction(fixture.ichnoisTracer)
    fixture.getSut().captureTransaction(transaction, fixture.ichnoisTracer.traceContext())

    verify(fixture.transport, never()).send(any(), anyOrNull())

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(
        DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Transaction.category, 1),
        DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Span.category, 2),
      ),
    )

    verify(onDiscardMock, times(1)).execute(DiscardReason.BEFORE_SEND, DataCategory.Transaction, 1)
    verify(onDiscardMock).execute(DiscardReason.BEFORE_SEND, DataCategory.Span, 2)
  }

  @Test
  fun `when beforeSendTransaction returns new instance, new instance is sent`() {
    val expected = IchnoisCoreTransaction(fixture.ichnoisTracer).apply { setTag("test", "test") }
    fixture.ichnoisOptions.setBeforeSendTransaction { _, _ -> expected }

    val transaction = IchnoisCoreTransaction(fixture.ichnoisTracer)
    fixture.getSut().captureTransaction(transaction, fixture.ichnoisTracer.traceContext())

    verify(fixture.transport)
      .send(
        check {
          val tx = getTransactionFromData(it.items.first().data)
          assertEquals("test", tx.tags!!["test"])
        },
        anyOrNull(),
      )
    verifyNoMoreInteractions(fixture.transport)
  }

  @Test
  fun `when beforeSendTransaction throws an exception, transaction is dropped`() {
    val exception = Exception("test")

    exception.stackTrace.toString()
    fixture.ichnoisOptions.setBeforeSendTransaction { _, _ -> throw exception }

    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    fixture.ichnoisOptions.onDiscard = onDiscardMock

    val transaction = IchnoisCoreTransaction(fixture.ichnoisTracer)
    val id = fixture.getSut().captureTransaction(transaction, fixture.ichnoisTracer.traceContext())

    assertEquals(IchnoisCoreId.EMPTY_ID, id)

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(
        DiscardedEvent(DiscardReason.CALLBACK_ERROR.reason, DataCategory.Transaction.category, 1),
        DiscardedEvent(DiscardReason.CALLBACK_ERROR.reason, DataCategory.Span.category, 2),
      ),
    )

    verify(onDiscardMock, times(1))
      .execute(DiscardReason.CALLBACK_ERROR, DataCategory.Transaction, 1)
    verify(onDiscardMock).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Span, 2)
  }

  @Test
  fun `when beforeSendTransaction drops a span, dropped span is recorded`() {
    fixture.ichnoisTracer.startChild("dropped span", "span1").finish()
    fixture.ichnoisTracer.startChild("dropped span", "span2").finish()
    fixture.ichnoisOptions.setBeforeSendTransaction { t: IchnoisCoreTransaction, _: Any? ->
      t.apply {
        // we are removing span1 and a-span from the fixture
        spans.removeIf { it.description != "span2" }
      }
    }

    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    fixture.ichnoisOptions.onDiscard = onDiscardMock

    val transaction = IchnoisCoreTransaction(fixture.ichnoisTracer)
    fixture.getSut().captureTransaction(transaction, fixture.ichnoisTracer.traceContext())

    verify(fixture.transport).send(any(), anyOrNull())

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Span.category, 2)),
    )

    verify(onDiscardMock, times(1)).execute(DiscardReason.BEFORE_SEND, DataCategory.Span, 2)
  }

  @Test
  fun `captureProfileChunk ignores beforeSend`() {
    var invoked = false
    fixture.ichnoisOptions.setBeforeSendTransaction { t, _ ->
      invoked = true
      t
    }
    fixture.getSut().captureProfileChunk(fixture.profileChunk, mock())
    assertFalse(invoked)
  }

  @Test
  fun `captureProfileChunk ignores Event Processors`() {
    val mockProcessor = mock<EventProcessor>()
    fixture.ichnoisOptions.addEventProcessor(mockProcessor)
    fixture.getSut().captureProfileChunk(fixture.profileChunk, mock())
    verifyNoInteractions(mockProcessor)
  }

  @Test
  fun `when captureSession and no release is set, do nothing`() {
    fixture.getSut().captureSession(createSession(""))
    verify(fixture.transport, never()).send(any(), anyOrNull())
  }

  @Test
  fun `when captureSession and release is set, send an envelope`() {
    fixture.getSut().captureSession(createSession())
    verify(fixture.transport).send(any(), anyOrNull())
  }

  @Test
  fun `when captureSession, sdkInfo should be in the envelope header`() {
    fixture.getSut().captureSession(createSession())
    verify(fixture.transport).send(check { assertNotNull(it.header.sdkVersion) }, anyOrNull())
  }

  @Test
  fun `when capturing the event throws, the hint is marked as capture failed`() {
    whenever(fixture.transport.send(any(), anyOrNull())).thenThrow(IOException())

    val hint = Hint()
    val ichnoisId = fixture.getSut().captureEvent(IchnoisCoreEvent(), hint)

    assertEquals(IchnoisCoreId.EMPTY_ID, ichnoisId)
    assertTrue(HintUtils.isCaptureFailed(hint))
  }

  @Test
  fun `when the event is dropped, the hint is not marked as capture failed`() {
    fixture.ichnoisOptions.setBeforeSend { _, _ -> null }

    val hint = Hint()
    val ichnoisId = fixture.getSut().captureEvent(IchnoisCoreEvent(), hint)

    assertEquals(IchnoisCoreId.EMPTY_ID, ichnoisId)
    assertFalse(HintUtils.isCaptureFailed(hint))
  }

  @Test
  fun `when captureEnvelope and thres an exception, returns empty ichnoisId`() {
    whenever(fixture.transport.send(any(), anyOrNull())).thenThrow(IOException())

    val envelope = IchnoisCoreEnvelope(IchnoisCoreId(UUID.randomUUID()), null, setOf())
    val ichnoisId = fixture.getSut().captureEnvelope(envelope)
    assertEquals(IchnoisCoreId.EMPTY_ID, ichnoisId)
  }

  @Test
  fun `when captureEnvelope and theres no exception, returns envelope header id`() {
    val expectedIchnoisCoreId = IchnoisCoreId(UUID.randomUUID())
    val envelope = IchnoisCoreEnvelope(expectedIchnoisCoreId, null, setOf())
    val ichnoisId = fixture.getSut().captureEnvelope(envelope)
    assertEquals(expectedIchnoisCoreId, ichnoisId)
  }

  @Test
  fun `When event is non handled, mark session as Crashed`() {
    val scope = Scope(fixture.ichnoisOptions)
    scope.startSession()

    val event = IchnoisCoreEvent().apply { exceptions = createNonHandledException() }
    fixture.getSut().updateSessionData(event, Hint(), scope)
    scope.withSession { assertEquals(Session.State.Crashed, it!!.status) }
  }

  @Test
  fun `When event is non handled, end the session`() {
    val scope = Scope(fixture.ichnoisOptions)
    scope.startSession()

    val event = IchnoisCoreEvent().apply { exceptions = createNonHandledException() }
    fixture.getSut().updateSessionData(event, Hint(), scope)
    scope.withSession {
      assertEquals(Session.State.Crashed, it!!.status)
      assertNotNull(it.duration)
    }
  }

  @Test
  fun `When event is handled, keep level as it is`() {
    val scope = Scope(fixture.ichnoisOptions)
    val sessionPair = scope.startSession()
    assertNotNull(sessionPair) {
      val session = it.current
      val level = session.status
      val event = IchnoisCoreEvent()
      fixture.getSut().updateSessionData(event, Hint(), scope)
      assertEquals(level, session.status)
    }
  }

  @Test
  fun `When event is non handled, increase errorCount`() {
    val scope = Scope(fixture.ichnoisOptions)
    scope.startSession()
    val event = IchnoisCoreEvent().apply { exceptions = createNonHandledException() }
    fixture.getSut().updateSessionData(event, Hint(), scope)
    scope.withSession { assertEquals(1, it!!.errorCount()) }
  }

  @Test
  fun `When event is Errored, increase errorCount`() {
    val scope = Scope(fixture.ichnoisOptions)
    scope.startSession()
    val exceptions = mutableListOf<IchnoisCoreException>()
    exceptions.add(IchnoisCoreException())
    val event = IchnoisCoreEvent().apply { setExceptions(exceptions) }
    fixture.getSut().updateSessionData(event, Hint(), scope)
    scope.withSession { assertEquals(1, it!!.errorCount()) }
  }

  @Test
  fun `When event is handled and not errored, do not increase errorsCount`() {
    val scope = Scope(fixture.ichnoisOptions)
    val sessionPair = scope.startSession()
    assertNotNull(sessionPair) {
      val session = it.current
      val errorCount = session.errorCount()
      val event = IchnoisCoreEvent()
      fixture.getSut().updateSessionData(event, Hint(), scope)
      assertEquals(errorCount, session.errorCount())
    }
  }

  @Test
  fun `when event has abnormal hint, sets abnormalMechanism and changes status to abnormal`() {
    val scope = givenScopeWithStartedSession()
    val event = IchnoisCoreEvent().apply { exceptions = listOf(IchnoisCoreException()) }
    val hint = HintUtils.createWithTypeCheckHint(AbnormalHint("anr_foreground"))

    fixture.getSut().updateSessionData(event, hint, scope)

    scope.withSession {
      assertEquals(Session.State.Abnormal, it!!.status)
      assertEquals("anr_foreground", it.abnormalMechanism)
    }
  }

  @Test
  fun `when event has abnormal hint, increases errorCrount`() {
    val scope = givenScopeWithStartedSession()
    val event = IchnoisCoreEvent().apply { exceptions = listOf(IchnoisCoreException()) }
    val hint = HintUtils.createWithTypeCheckHint(AbnormalHint("anr_foreground"))

    fixture.getSut().updateSessionData(event, hint, scope)

    scope.withSession { assertEquals(1, it!!.errorCount()) }
  }

  @Test
  fun `When event has userAgent, set it into session`() {
    val scope = Scope(fixture.ichnoisOptions)
    val sessionPair = scope.startSession()
    assertNotNull(sessionPair) {
      val event =
        IchnoisCoreEvent().apply {
          request = Request().apply { headers = mutableMapOf("user-agent" to "jamesBond") }
        }
      fixture.getSut().updateSessionData(event, Hint(), scope)
      scope.withSession { assertEquals("jamesBond", it!!.userAgent) }
    }
  }

  @Test
  fun `When event has no userAgent, keep as it is`() {
    val scope = Scope(fixture.ichnoisOptions)
    val sessionPair = scope.startSession()
    assertNotNull(sessionPair) {
      val session = it.current
      val userAgent = session.userAgent
      val event = IchnoisCoreEvent().apply { request = Request().apply { headers = mutableMapOf() } }
      fixture.getSut().updateSessionData(event, Hint(), scope)
      assertEquals(userAgent, session.userAgent)
    }
  }

  @Test
  fun `When capture an event and there's no session, do nothing`() {
    val scope = Scope(fixture.ichnoisOptions)
    val event = IchnoisCoreEvent()
    fixture.getSut().updateSessionData(event, Hint(), scope)
    scope.withSession { assertNull(it) }
  }

  @Test
  fun `when captureEvent with sampling, session is still updated`() {
    fixture.ichnoisOptions.sampleRate = 1.0
    val sut = fixture.getSut()

    val event = IchnoisCoreEvent().apply { exceptions = createNonHandledException() }
    val scope = Scope(fixture.ichnoisOptions)
    val sessionPair = scope.startSession()
    assertNotNull(sessionPair) {
      sut.captureEvent(event, scope, null)
      scope.withSession {
        assertEquals(Session.State.Crashed, it!!.status)
        assertEquals(1, it.errorCount())
      }
    }
  }

  @Test
  fun `when context property is missing on the event, property from scope contexts is applied`() {
    val sut = fixture.getSut()

    val scope = Scope(fixture.ichnoisOptions)
    scope.setContexts("key", "abc")
    val sessionPair = scope.startSession()
    assertNotNull(sessionPair) {
      sut.captureEvent(IchnoisCoreEvent(), scope, null)
      verify(fixture.transport)
        .send(
          check {
            val event = getEventFromData(it.items.first().data)
            val map = event.contexts["key"] as Map<*, *>
            assertEquals("abc", map["value"])
          },
          anyOrNull(),
        )
    }
  }

  @Test
  fun `when session is in terminal state, does not send session update`() {
    val sut = fixture.getSut()

    val event = IchnoisCoreEvent().apply { exceptions = createNonHandledException() }
    val scope = Scope(fixture.ichnoisOptions)
    val sessionPair = scope.startSession()
    scope.withSession { it!!.update(Crashed, null, false) }

    assertNotNull(sessionPair) {
      sut.captureEvent(event, scope, null)
      verify(fixture.transport)
        .send(
          check {
            assertNull(it.items.find { item -> item.header.type == IchnoisCoreItemType.Session })
          },
          anyOrNull(),
        )
    }
  }

  @Test
  fun `when contexts property is set on the event, property from scope contexts is not applied`() {
    val sut = fixture.getSut()

    val event = IchnoisCoreEvent()
    event.contexts["key"] = "event value"
    val scope = Scope(fixture.ichnoisOptions)
    scope.setContexts("key", "scope value")
    val sessionPair = scope.startSession()
    assertNotNull(sessionPair) {
      sut.captureEvent(event, scope, null)
      verify(fixture.transport)
        .send(
          check {
            val eventFromData = getEventFromData(it.items.first().data)
            assertEquals("event value", eventFromData.contexts["key"])
          },
          anyOrNull(),
        )
    }
  }

  @Test
  fun `when contexts are not objects, wrap it up within a value object`() {
    val sut = fixture.getSut()

    val event = IchnoisCoreEvent()
    val scope = Scope(fixture.ichnoisOptions)
    scope.setContexts("boolean", true)
    scope.setContexts("string", "test")
    scope.setContexts("number", 1)
    scope.setContexts("collection", listOf("a", "b"))
    scope.setContexts("array", arrayOf("a", "b"))
    scope.setContexts("char", 'a')

    sut.captureEvent(event, scope, null)
    verify(fixture.transport)
      .send(
        check {
          val contexts = getEventFromData(it.items.first().data).contexts

          val bolKey = contexts["boolean"] as Map<*, *>
          assertTrue(bolKey["value"] as Boolean)

          val strKey = contexts["string"] as Map<*, *>
          assertEquals("test", strKey["value"])

          val numKey = contexts["number"] as Map<*, *>
          assertEquals(1, numKey["value"])

          val listKey = contexts["collection"] as Map<*, *>
          assertEquals("a", (listKey["value"] as List<*>)[0])
          assertEquals("b", (listKey["value"] as List<*>)[1])

          val arrKey = contexts["array"] as Map<*, *>
          assertEquals("a", (arrKey["value"] as List<*>)[0])
          assertEquals("b", (arrKey["value"] as List<*>)[1])

          val charKey = contexts["char"] as Map<*, *>
          assertEquals("a", charKey["value"])
        },
        anyOrNull(),
      )
  }

  @Test
  fun `exception thrown by an event processor drops event and stops callbacks`() {
    val throwingProcessor = mock<EventProcessor>()
    val nextProcessor = mock<EventProcessor>()
    val beforeSend = mock<IchnoisCoreOptions.BeforeSendCallback>()
    val onDiscard = mock<IchnoisCoreOptions.OnDiscardCallback>()
    whenever(throwingProcessor.process(any<IchnoisCoreEvent>(), anyOrNull()))
      .thenThrow(IllegalStateException("test"))
    fixture.ichnoisOptions.addEventProcessor(throwingProcessor)
    fixture.ichnoisOptions.addEventProcessor(nextProcessor)
    fixture.ichnoisOptions.beforeSend = beforeSend
    fixture.ichnoisOptions.onDiscard = onDiscard

    val id = fixture.getSut().captureEvent(IchnoisCoreEvent())

    assertThat(id).isEqualTo(IchnoisCoreId.EMPTY_ID)
    verify(nextProcessor, never()).process(any<IchnoisCoreEvent>(), anyOrNull())
    verify(beforeSend, never()).execute(any(), anyOrNull())
    verify(fixture.transport, never()).send(any(), anyOrNull())
    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.CALLBACK_ERROR.reason, DataCategory.Error.category, 1)),
    )
    verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Error, 1)
  }

  @Test
  fun `transactions are sent using connection`() {
    val sut = fixture.getSut()
    sut.captureTransaction(
      IchnoisCoreTransaction(fixture.ichnoisTracer),
      Scope(fixture.ichnoisOptions),
      null,
    )
    verify(fixture.transport)
      .send(
        check {
          val transaction = it.items.first().getTransaction(fixture.ichnoisOptions.serializer)
          assertNotNull(transaction)
          assertEquals("a-transaction", transaction.transaction)
        },
        anyOrNull(),
      )
  }

  @Test
  fun `when captureTransaction with attachments`() {
    val transaction = IchnoisCoreTransaction(fixture.ichnoisTracer)
    fixture.getSut().captureTransaction(transaction, createScopeWithAttachments(), null)

    verifyAttachmentsInEnvelope(transaction.eventId)
    assertFails { verifyProfilingTraceInEnvelope(IchnoisCoreId(fixture.profilingTraceData.profileId)) }
  }

  @Test
  fun `when captureEnvelope with ProfilingTraceData`() {
    val client = fixture.getSut()
    val options = fixture.ichnoisOptions
    val envelope =
      IchnoisCoreEnvelope.from(
        options.serializer,
        fixture.profilingTraceData,
        options.maxTraceFileSize,
        options.sdkVersion,
      )
    client.captureEnvelope(envelope)
    verifyProfilingTraceInEnvelope(IchnoisCoreId(fixture.profilingTraceData.profileId))
  }

  @Test
  fun `when capture profile with empty trace file, profile is not sent`() {
    val client = fixture.getSut()
    val options = fixture.ichnoisOptions
    val envelope =
      IchnoisCoreEnvelope.from(
        options.serializer,
        fixture.profilingTraceData,
        options.maxTraceFileSize,
        options.sdkVersion,
      )
    client.captureEnvelope(envelope)
    fixture.profilingTraceFile.writeText("")
    assertFails { verifyProfilingTraceInEnvelope(IchnoisCoreId(fixture.profilingTraceData.profileId)) }
  }

  @Test
  fun `when capture profile with non existing profiling trace file, profile is not sent`() {
    val client = fixture.getSut()
    val options = fixture.ichnoisOptions
    val envelope =
      IchnoisCoreEnvelope.from(
        options.serializer,
        fixture.profilingNonExistingTraceData,
        options.maxTraceFileSize,
        options.sdkVersion,
      )
    client.captureEnvelope(envelope)
    assertFails {
      verifyProfilingTraceInEnvelope(IchnoisCoreId(fixture.profilingNonExistingTraceData.profileId))
    }
  }

  @Test
  fun `when captureProfileChunk`() {
    val client = fixture.getSut()
    client.captureProfileChunk(fixture.profileChunk, mock())
    verifyProfileChunkInEnvelope(fixture.profileChunk.chunkId)
  }

  @Test
  fun `captureProfileChunk adds options proguard debug meta`() {
    fixture.ichnoisOptions.proguardUuid = "current-uuid"

    val client = fixture.getSut()
    client.captureProfileChunk(fixture.profileChunk, mock())

    verify(fixture.transport)
      .send(
        check { actual ->
          val profileChunk = getProfileChunkFromEnvelope(actual)
          val images = profileChunk.debugMeta!!.images!!

          assertEquals(1, images.size)
          assertEquals(DebugImage.PROGUARD, images[0].type)
          assertEquals("current-uuid", images[0].uuid)
        }
      )
  }

  @Test
  fun `captureProfileChunk preserves existing proguard debug meta`() {
    fixture.ichnoisOptions.proguardUuid = "current-uuid"
    fixture.profileChunk.debugMeta =
      DebugMeta().apply {
        images =
          listOf(
            DebugImage().apply {
              type = DebugImage.PROGUARD
              uuid = "previous-uuid"
            }
          )
      }

    val client = fixture.getSut()
    client.captureProfileChunk(fixture.profileChunk, mock())

    verify(fixture.transport)
      .send(
        check { actual ->
          val profileChunk = getProfileChunkFromEnvelope(actual)
          val images = profileChunk.debugMeta!!.images!!

          assertEquals(1, images.size)
          assertEquals(DebugImage.PROGUARD, images[0].type)
          assertEquals("previous-uuid", images[0].uuid)
        }
      )
  }

  @Test
  fun `when captureProfileChunk with empty trace file, profile chunk is not sent`() {
    val client = fixture.getSut()
    fixture.profilingTraceFile.writeText("")
    client.captureProfileChunk(fixture.profileChunk, mock())
    assertFails { verifyProfilingTraceInEnvelope(fixture.profileChunk.chunkId) }
  }

  @Test
  fun `when captureProfileChunk with non existing profiling trace file, profile chunk is not sent`() {
    val client = fixture.getSut()
    fixture.profilingTraceFile.delete()
    client.captureProfileChunk(fixture.profileChunk, mock())
    assertFails { verifyProfilingTraceInEnvelope(fixture.profileChunk.chunkId) }
  }

  @Test
  fun `when captureTransaction with attachments not added to transaction`() {
    val transaction = IchnoisCoreTransaction(fixture.ichnoisTracer)
    val scope = createScopeWithAttachments()
    scope.addAttachment(Attachment("hello".toByteArray(), "application/octet-stream"))
    fixture.getSut().captureTransaction(transaction, scope, null)

    verifyAttachmentsInEnvelope(transaction.eventId)
  }

  @Test
  fun `when captureTransaction scope is applied to transaction`() {
    val sut = fixture.getSut()
    val scope = Scope(fixture.ichnoisOptions)
    scope.setTag("tag1", "value1")
    scope.setContexts("context-key", "context-value")
    scope.request = Request().apply { url = "/url" }
    scope.addBreadcrumb(Breadcrumb("message"))
    scope.setExtra("a", "b")

    sut.captureTransaction(IchnoisCoreTransaction(fixture.ichnoisTracer), scope, null)
    verify(fixture.transport)
      .send(
        check { envelope ->
          val transaction = envelope.items.first().getTransaction(fixture.ichnoisOptions.serializer)
          assertNotNull(transaction) {
            assertEquals("value1", it.getTag("tag1"))
            assertEquals(mapOf("value" to "context-value"), it.contexts["context-key"])
            assertNotNull(it.request) { request -> assertEquals("/url", request.url) }
            assertNotNull(it.breadcrumbs) { breadcrumbs ->
              assertEquals("message", breadcrumbs.first().message)
            }
            assertEquals("b", it.getExtra("a"))
          }
        },
        anyOrNull(),
      )
  }

  @Test
  fun `when captureTransaction with scope, transaction should use user data`() {
    val scopes: IScopes = mock()
    whenever(scopes.options).thenReturn(IchnoisCoreOptions())
    val transaction = IchnoisCoreTransaction(IchnoisCoreTracer(TransactionContext("tx", "op"), scopes))
    val scope = createScope()

    val sut = fixture.getSut()

    sut.captureTransaction(transaction, scope, null)
    assertNotNull(transaction.user) { assertEquals("id", it.id) }
  }

  @Test
  fun `when scope's active span is a transaction, transaction context is applied to an event`() {
    val event = IchnoisCoreEvent()
    val sut = fixture.getSut()
    val scope = createScope()
    val transaction = fixture.ichnoisTracer
    scope.setTransaction(transaction)
    transaction.finish()
    sut.captureEvent(event, scope)
    assertNotNull(event.contexts.trace)
    assertEquals(transaction.root.spanContext, event.contexts.trace)
  }

  @Test
  fun `when scope's active span is a span, span is applied to an event`() {
    val event = IchnoisCoreEvent()
    val sut = fixture.getSut()
    val scope = createScope()
    val transaction = IchnoisCoreTracer(TransactionContext("a-transaction", "op"), fixture.scopes)
    scope.setTransaction(transaction)
    val span = transaction.startChild("op")
    sut.captureEvent(event, scope)
    assertNotNull(event.contexts.trace)
    assertEquals(span.spanContext, event.contexts.trace)
  }

  @Test
  fun `when scope has an active transaction, trace state is set on the envelope`() {
    val event = IchnoisCoreEvent()
    val sut = fixture.getSut()
    val scope = createScope()
    val transaction = fixture.ichnoisTracer
    scope.setTransaction(transaction)
    transaction.finish()
    sut.captureEvent(event, scope)
    verify(fixture.transport)
      .send(
        check {
          assertNotNull(it.header.traceContext) {
            assertEquals(transaction.spanContext.traceId, it.traceId)
          }
        },
        anyOrNull(),
      )
  }

  @Test
  fun `when scope does not have an active transaction, trace state is set on the envelope from scope`() {
    val sut = fixture.getSut()
    val scope = createScope()
    sut.captureEvent(IchnoisCoreEvent(), scope)
    verify(fixture.transport)
      .send(
        check {
          assertNotNull(it.header.traceContext)
          assertEquals(scope.propagationContext.traceId, it.header.traceContext?.traceId)
        },
        anyOrNull(),
      )
  }

  @Test
  fun `when transaction is captured, trace state is set on the envelope`() {
    val sut = fixture.getSut()
    val transaction = IchnoisCoreTransaction(fixture.ichnoisTracer)
    val traceContext = fixture.ichnoisTracer.traceContext()
    sut.captureTransaction(transaction, traceContext)
    verify(fixture.transport)
      .send(check { assertEquals(traceContext, it.header.traceContext) }, anyOrNull())
  }

  @Test
  fun `when transaction does not have environment and release set, and the environment is set on options, options values are applied to transactions`() {
    fixture.ichnoisOptions.release = "optionsRelease"
    fixture.ichnoisOptions.environment = "optionsEnvironment"
    val sut = fixture.getSut()
    val transaction = IchnoisCoreTransaction(fixture.ichnoisTracer)
    sut.captureTransaction(transaction, fixture.ichnoisTracer.traceContext())
    assertEquals("optionsRelease", transaction.release)
    assertEquals("optionsEnvironment", transaction.environment)
  }

  @Test
  fun `when transaction has environment and release set, and the environment is set on options, options values are not applied to transactions`() {
    fixture.ichnoisOptions.release = "optionsRelease"
    fixture.ichnoisOptions.environment = "optionsEnvironment"
    val sut = fixture.getSut()
    val ichnoisTracer = IchnoisCoreTracer(TransactionContext("name", "op"), fixture.scopes)
    val transaction = IchnoisCoreTransaction(ichnoisTracer)
    transaction.release = "transactionRelease"
    transaction.environment = "transactionEnvironment"
    sut.captureTransaction(transaction, ichnoisTracer.traceContext())
    assertEquals("transactionRelease", transaction.release)
    assertEquals("transactionEnvironment", transaction.environment)
  }

  @Test
  fun `when transaction does not have SDK version set, and the SDK version is set on options, options values are applied to transactions`() {
    fixture.ichnoisOptions.sdkVersion = SdkVersion("sdk.name", "version")
    val sut = fixture.getSut()
    val ichnoisTracer = IchnoisCoreTracer(TransactionContext("name", "op"), fixture.scopes)
    val transaction = IchnoisCoreTransaction(ichnoisTracer)
    sut.captureTransaction(transaction, ichnoisTracer.traceContext())
    assertEquals(fixture.ichnoisOptions.sdkVersion, transaction.sdk)
  }

  @Test
  fun `when transaction has SDK version set, and the SDK version is set on options, options values are not applied to transactions`() {
    fixture.ichnoisOptions.sdkVersion = SdkVersion("sdk.name", "version")
    val sut = fixture.getSut()
    val ichnoisTracer = IchnoisCoreTracer(TransactionContext("name", "op"), fixture.scopes)
    val transaction = IchnoisCoreTransaction(ichnoisTracer)
    val sdkVersion = SdkVersion("transaction.sdk.name", "version")
    transaction.sdk = sdkVersion
    sut.captureTransaction(transaction, ichnoisTracer.traceContext())
    assertEquals(sdkVersion, transaction.sdk)
  }

  @Test
  fun `when transaction does not have tags, and tags are set on options, options values are applied to transactions`() {
    fixture.ichnoisOptions.setTag("tag1", "value1")
    val sut = fixture.getSut()
    val ichnoisTracer = IchnoisCoreTracer(TransactionContext("name", "op"), fixture.scopes)
    val transaction = IchnoisCoreTransaction(ichnoisTracer)
    sut.captureTransaction(transaction, ichnoisTracer.traceContext())
    assertEquals(mapOf("tag1" to "value1"), transaction.tags)
  }

  @Test
  fun `when transaction has tags, and tags are set on options, options tags are added to transactions`() {
    fixture.ichnoisOptions.setTag("tag1", "value1")
    fixture.ichnoisOptions.setTag("tag2", "value2")
    val sut = fixture.getSut()
    val ichnoisTracer = IchnoisCoreTracer(TransactionContext("name", "op"), fixture.scopes)
    val transaction = IchnoisCoreTransaction(ichnoisTracer)
    transaction.setTag("tag3", "value3")
    transaction.setTag("tag2", "transaction-tag")
    sut.captureTransaction(transaction, ichnoisTracer.traceContext())
    assertEquals(
      mapOf("tag1" to "value1", "tag2" to "transaction-tag", "tag3" to "value3"),
      transaction.tags,
    )
  }

  @Test
  fun `captured transactions without a platform, have the default platform set`() {
    val sut = fixture.getSut()
    val ichnoisTracer = IchnoisCoreTracer(TransactionContext("name", "op"), fixture.scopes)
    val transaction = IchnoisCoreTransaction(ichnoisTracer)
    sut.captureTransaction(transaction, ichnoisTracer.traceContext())
    assertEquals("java", transaction.platform)
  }

  @Test
  fun `captured transactions with a platform, do not get the platform overwritten`() {
    val sut = fixture.getSut()
    val ichnoisTracer = IchnoisCoreTracer(TransactionContext("name", "op"), fixture.scopes)
    val transaction = IchnoisCoreTransaction(ichnoisTracer)
    transaction.platform = "abc"
    sut.captureTransaction(transaction, ichnoisTracer.traceContext())
    assertEquals("abc", transaction.platform)
  }

  @Test
  fun `captureTransaction registers trace ID with replay controller`() {
    var registeredTraceId: IchnoisCoreId? = null
    fixture.ichnoisOptions.setReplayController(
      object : ReplayController by NoOpReplayController.getInstance() {
        override fun registerTraceId(traceId: IchnoisCoreId) {
          registeredTraceId = traceId
        }

        override fun registerSegmentName(segmentName: String) {}
      }
    )
    val sut = fixture.getSut()
    val ichnoisTracer = IchnoisCoreTracer(TransactionContext("name", "op"), fixture.scopes)
    val transaction = IchnoisCoreTransaction(ichnoisTracer)
    sut.captureTransaction(transaction, ichnoisTracer.traceContext())
    assertEquals(ichnoisTracer.spanContext.traceId, registeredTraceId)
  }

  @Test
  fun `captureTransaction registers segment name with replay controller`() {
    var registeredSegmentName: String? = null
    fixture.ichnoisOptions.setReplayController(
      object : ReplayController by NoOpReplayController.getInstance() {
        override fun registerSegmentName(segmentName: String) {
          registeredSegmentName = segmentName
        }
      }
    )
    val sut = fixture.getSut()
    val ichnoisTracer =
      IchnoisCoreTracer(TransactionContext("CheckoutActivity", "ui.load"), fixture.scopes)
    val transaction = IchnoisCoreTransaction(ichnoisTracer)
    sut.captureTransaction(transaction, ichnoisTracer.traceContext())
    assertEquals("CheckoutActivity", registeredSegmentName)
  }

  @Test
  fun `when exception type is ignored, capturing event does not send it`() {
    fixture.ichnoisOptions.addIgnoredExceptionForType(IllegalStateException::class.java)
    val sut = fixture.getSut()
    sut.captureException(IllegalStateException())
    verify(fixture.transport, never()).send(any(), anyOrNull())
  }

  @Test
  fun `when event message matches string in ignoredErrors, capturing event does not send it`() {
    fixture.ichnoisOptions.addIgnoredError("hello")
    val sut = fixture.getSut()
    val event = IchnoisCoreEvent()
    val message = Message()
    message.message = "hello"
    event.setMessage(message)
    sut.captureEvent(event)
    verify(fixture.transport, never()).send(any(), anyOrNull())
  }

  @Test
  fun `when event message matches regex pattern in ignoredErrors, capturing event does not send it`() {
    fixture.ichnoisOptions.addIgnoredError("hello .*")
    val sut = fixture.getSut()
    val event = IchnoisCoreEvent()
    val message = Message()
    message.message = "hello world"
    event.setMessage(message)
    sut.captureEvent(event)
    verify(fixture.transport, never()).send(any(), anyOrNull())
  }

  @Test
  fun `when event message does not match regex pattern in ignoredErrors, capturing event sends it`() {
    fixture.ichnoisOptions.addIgnoredError("hello .*")
    val sut = fixture.getSut()
    val event = IchnoisCoreEvent()
    val message = Message()
    message.message = "test"
    event.setMessage(message)
    sut.captureEvent(event)
    verify(fixture.transport).send(any(), anyOrNull())
  }

  @Test
  fun `when exception message matches regex pattern in ignoredErrors, capturing event does not send it`() {
    fixture.ichnoisOptions.addIgnoredError(".*hello .*")
    val sut = fixture.getSut()
    sut.captureException(RuntimeException("hello world"))
    verify(fixture.transport, never()).send(any(), anyOrNull())
  }

  @Test
  fun `when class matches regex pattern in ignoredErrors, capturing event does not send it`() {
    fixture.ichnoisOptions.addIgnoredError("java\\.lang\\..*")
    val sut = fixture.getSut()
    sut.captureException(RuntimeException("hello world"))
    verify(fixture.transport, never()).send(any(), anyOrNull())
  }

  @Test
  fun `when ignoredExceptionsForType and ignoredErrors are not explicitly specified, capturing event sends event`() {
    val sut = fixture.getSut()
    sut.captureException(RuntimeException("test"))
    verify(fixture.transport).send(any(), anyOrNull())
  }

  @Test
  fun `screenshot is added to the envelope from the hint`() {
    val sut = fixture.getSut()
    val attachment = Attachment.fromScreenshot(byteArrayOf())
    val hint = Hint().also { it.screenshot = attachment }

    sut.captureEvent(IchnoisCoreEvent(), hint)

    verify(fixture.transport)
      .send(
        check { envelope ->
          val screenshot = envelope.items.last()
          assertNotNull(screenshot) {
            assertEquals(attachment.filename, screenshot.header.fileName)
          }
        },
        anyOrNull(),
      )
  }

  @Test
  fun `screenshot is dropped from hint via before send`() {
    fixture.ichnoisOptions.beforeSend = CustomBeforeSendCallback()
    val sut = fixture.getSut()
    val attachment = Attachment.fromScreenshot(byteArrayOf())
    val hint = Hint().also { it.screenshot = attachment }

    sut.captureEvent(IchnoisCoreEvent(), hint)

    verify(fixture.transport)
      .send(check { envelope -> assertEquals(1, envelope.items.count()) }, anyOrNull())
  }

  @Test
  fun `view hierarchy is added to the envelope from the hint`() {
    val sut = fixture.getSut()
    val attachment = Attachment.fromViewHierarchy(ViewHierarchy("android_view_system", emptyList()))
    val hint = Hint().also { it.viewHierarchy = attachment }

    sut.captureEvent(IchnoisCoreEvent(), hint)

    verify(fixture.transport)
      .send(
        check { envelope ->
          val viewHierarchy = envelope.items.last()
          assertNotNull(viewHierarchy) {
            assertEquals(attachment.filename, viewHierarchy.header.fileName)
          }
        },
        anyOrNull(),
      )
  }

  @Test
  fun `view hierarchy is dropped from hint via before send`() {
    fixture.ichnoisOptions.beforeSend = CustomBeforeSendCallback()
    val sut = fixture.getSut()
    val attachment = Attachment.fromViewHierarchy(ViewHierarchy("android_view_system", emptyList()))
    val hint = Hint().also { it.viewHierarchy = attachment }

    sut.captureEvent(IchnoisCoreEvent(), hint)

    verify(fixture.transport)
      .send(check { envelope -> assertEquals(1, envelope.items.count()) }, anyOrNull())
  }

  @Test
  fun `thread dump is added to the envelope from the hint`() {
    val sut = fixture.getSut()
    val attachment = Attachment.fromThreadDump(byteArrayOf())
    val hint = Hint().also { it.threadDump = attachment }

    sut.captureEvent(IchnoisCoreEvent(), hint)

    verify(fixture.transport)
      .send(
        check { envelope ->
          val threadDump = envelope.items.last()
          assertNotNull(threadDump) {
            assertEquals(attachment.filename, threadDump.header.fileName)
          }
        },
        anyOrNull(),
      )
  }

  @Test
  fun `thread dump is dropped from hint via before send`() {
    fixture.ichnoisOptions.beforeSend = CustomBeforeSendCallback()
    val sut = fixture.getSut()
    val attachment = Attachment.fromThreadDump(byteArrayOf())
    val hint = Hint().also { it.threadDump = attachment }

    sut.captureEvent(IchnoisCoreEvent(), hint)

    verify(fixture.transport)
      .send(check { envelope -> assertEquals(1, envelope.items.count()) }, anyOrNull())
  }

  @Test
  fun `tombstone is added to the envelope from the hint`() {
    val sut = fixture.getSut()
    val attachment = Attachment.fromTombstone(byteArrayOf())
    val hint = Hint().also { it.tombstone = attachment }

    sut.captureEvent(IchnoisCoreEvent(), hint)

    verify(fixture.transport)
      .send(
        check { envelope ->
          val tombstone = envelope.items.last()
          assertNotNull(tombstone) { assertEquals(attachment.filename, tombstone.header.fileName) }
        },
        anyOrNull(),
      )
  }

  @Test
  fun `tombstone is dropped from hint via before send`() {
    fixture.ichnoisOptions.beforeSend = CustomBeforeSendCallback()
    val sut = fixture.getSut()
    val attachment = Attachment.fromTombstone(byteArrayOf())
    val hint = Hint().also { it.tombstone = attachment }

    sut.captureEvent(IchnoisCoreEvent(), hint)

    verify(fixture.transport)
      .send(check { envelope -> assertEquals(1, envelope.items.count()) }, anyOrNull())
  }

  @Test
  fun `capturing an error updates session and sends event + session`() {
    val sut = fixture.getSut()
    val scope = givenScopeWithStartedSession()

    sut.captureEvent(IchnoisCoreEvent().apply { exceptions = createHandledException() }, scope)

    thenSessionIsErrored(scope)
    thenEnvelopeIsSentWith(eventCount = 1, sessionCount = 1)
  }

  @Test
  fun `dropping a captured error from beforeSend has no effect on session and does not send anything`() {
    val sut = fixture.getSut { options ->
      options.beforeSend = IchnoisCoreOptions.BeforeSendCallback { _, _ -> null }
    }
    val scope = givenScopeWithStartedSession()

    sut.captureEvent(IchnoisCoreEvent().apply { exceptions = createHandledException() }, scope)

    thenSessionIsStillOK(scope)
    thenNothingIsSent()
  }

  @Test
  fun `dropping a captured error from eventProcessor has no effect on session and does not send anything`() {
    val sut = fixture.getSut { options ->
      options.addEventProcessor(DropEverythingEventProcessor())
    }
    val scope = givenScopeWithStartedSession()

    sut.captureEvent(IchnoisCoreEvent().apply { exceptions = createHandledException() }, scope)

    thenSessionIsStillOK(scope)
    thenNothingIsSent()
  }

  @Test
  fun `dropping a captured error via sampling updates the session and only sends the session for a new session`() {
    val sut = fixture.getSut { options -> options.sampleRate = 0.000000000001 }
    val scope = givenScopeWithStartedSession()

    sut.captureEvent(IchnoisCoreEvent().apply { exceptions = createHandledException() }, scope)

    thenSessionIsErrored(scope)
    thenEnvelopeIsSentWith(eventCount = 0, sessionCount = 1)
  }

  @Test
  fun `dropping a captured error via sampling updates the session and does not send anything for an errored session`() {
    val sut = fixture.getSut { options -> options.sampleRate = 0.000000000001 }
    val scope = givenScopeWithStartedSession(errored = true)

    sut.captureEvent(IchnoisCoreEvent().apply { exceptions = createHandledException() }, scope)

    thenSessionIsErrored(scope)
    thenNothingIsSent()
  }

  @Test
  fun `dropping a captured error via sampling updates the session and does not send anything for a crashed session`() {
    val sut = fixture.getSut { options -> options.sampleRate = 0.000000000001 }
    val scope = givenScopeWithStartedSession(crashed = true)

    sut.captureEvent(IchnoisCoreEvent().apply { exceptions = createHandledException() }, scope)

    thenSessionIsCrashed(scope)
    thenNothingIsSent()
  }

  @Test
  fun `dropping a captured crash via sampling updates the session and only sends the session for a new session`() {
    val sut = fixture.getSut { options -> options.sampleRate = 0.000000000001 }
    val scope = givenScopeWithStartedSession()

    sut.captureEvent(IchnoisCoreEvent().apply { exceptions = createNonHandledException() }, scope)

    thenSessionIsCrashed(scope)
    thenEnvelopeIsSentWith(eventCount = 0, sessionCount = 1)
  }

  @Test
  fun `dropping a captured crash via sampling updates the session and sends the session for an errored session`() {
    val sut = fixture.getSut { options -> options.sampleRate = 0.000000000001 }
    val scope = givenScopeWithStartedSession(errored = true)

    sut.captureEvent(IchnoisCoreEvent().apply { exceptions = createNonHandledException() }, scope)

    thenSessionIsCrashed(scope)
    thenEnvelopeIsSentWith(eventCount = 0, sessionCount = 1)
  }

  @Test
  fun `dropping a captured crash via sampling updates the session and does not send anything for a crashed session`() {
    val sut = fixture.getSut { options -> options.sampleRate = 0.000000000001 }
    val scope = givenScopeWithStartedSession(crashed = true)

    sut.captureEvent(IchnoisCoreEvent().apply { exceptions = createNonHandledException() }, scope)

    thenSessionIsCrashed(scope)
    thenNothingIsSent()
  }

  @Test
  fun `ignored exceptions are checked before other filter mechanisms`() {
    val beforeSendMock = mock<IchnoisCoreOptions.BeforeSendCallback>()
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    val scopedEventProcessorMock = mock<EventProcessor>()
    val globalEventProcessorMock = mock<EventProcessor>()

    whenever(scopedEventProcessorMock.process(any<IchnoisCoreEvent>(), anyOrNull())).thenReturn(null)
    whenever(globalEventProcessorMock.process(any<IchnoisCoreEvent>(), anyOrNull())).thenReturn(null)
    whenever(beforeSendMock.execute(any(), anyOrNull())).thenReturn(null)

    fixture.ichnoisOptions.onDiscard = onDiscardMock

    val sut = fixture.getSut { options ->
      options.sampleRate = 0.000000000001
      options.addIgnoredExceptionForType(NegativeArraySizeException::class.java)
      options.beforeSend = beforeSendMock
      options.addEventProcessor(globalEventProcessorMock)
    }
    val scope = givenScopeWithStartedSession()
    scope.addEventProcessor(scopedEventProcessorMock)

    sut.captureException(NegativeArraySizeException(), scope)

    verify(scopedEventProcessorMock, never()).process(any<IchnoisCoreEvent>(), anyOrNull())
    verify(globalEventProcessorMock, never()).process(any<IchnoisCoreEvent>(), anyOrNull())
    verify(beforeSendMock, never()).execute(any(), anyOrNull())

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Error.category, 1)),
    )

    verify(onDiscardMock, times(1)).execute(DiscardReason.EVENT_PROCESSOR, DataCategory.Error, 1)
  }

  @Test
  fun `sampling is last filter mechanism`() {
    val beforeSendMock = mock<IchnoisCoreOptions.BeforeSendCallback>()
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    val scopedEventProcessorMock = mock<EventProcessor>()
    val globalEventProcessorMock = mock<EventProcessor>()

    fixture.ichnoisOptions.onDiscard = onDiscardMock

    whenever(scopedEventProcessorMock.process(any<IchnoisCoreEvent>(), anyOrNull())).doAnswer {
      it.arguments.first() as IchnoisCoreEvent
    }
    whenever(globalEventProcessorMock.process(any<IchnoisCoreEvent>(), anyOrNull())).doAnswer {
      it.arguments.first() as IchnoisCoreEvent
    }
    whenever(beforeSendMock.execute(any(), anyOrNull())).doAnswer {
      it.arguments.first() as IchnoisCoreEvent
    }

    val sut = fixture.getSut { options ->
      options.sampleRate = 0.000000000001
      options.addIgnoredExceptionForType(NegativeArraySizeException::class.java)
      options.beforeSend = beforeSendMock
      options.addEventProcessor(globalEventProcessorMock)
    }
    val scope = givenScopeWithStartedSession()
    scope.addEventProcessor(scopedEventProcessorMock)

    sut.captureException(IllegalStateException(), scope)

    val order = inOrder(scopedEventProcessorMock, globalEventProcessorMock, beforeSendMock)

    order.verify(scopedEventProcessorMock, times(1)).process(any<IchnoisCoreEvent>(), anyOrNull())
    order.verify(globalEventProcessorMock, times(1)).process(any<IchnoisCoreEvent>(), anyOrNull())
    order.verify(beforeSendMock, times(1)).execute(any(), anyOrNull())

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.SAMPLE_RATE.reason, DataCategory.Error.category, 1)),
    )

    verify(onDiscardMock, times(1)).execute(DiscardReason.SAMPLE_RATE, DataCategory.Error, 1)
  }

  @Test
  fun `filter mechanism order check for beforeSend`() {
    val beforeSendMock = mock<IchnoisCoreOptions.BeforeSendCallback>()
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    val scopedEventProcessorMock = mock<EventProcessor>()
    val globalEventProcessorMock = mock<EventProcessor>()

    fixture.ichnoisOptions.onDiscard = onDiscardMock

    whenever(scopedEventProcessorMock.process(any<IchnoisCoreEvent>(), anyOrNull())).doAnswer {
      it.arguments.first() as IchnoisCoreEvent
    }
    whenever(globalEventProcessorMock.process(any<IchnoisCoreEvent>(), anyOrNull())).doAnswer {
      it.arguments.first() as IchnoisCoreEvent
    }
    whenever(beforeSendMock.execute(any(), anyOrNull())).thenReturn(null)

    val sut = fixture.getSut { options ->
      options.sampleRate = 0.000000000001
      options.addIgnoredExceptionForType(NegativeArraySizeException::class.java)
      options.beforeSend = beforeSendMock
      options.addEventProcessor(globalEventProcessorMock)
    }
    val scope = givenScopeWithStartedSession()
    scope.addEventProcessor(scopedEventProcessorMock)

    sut.captureException(IllegalStateException(), scope)

    val order = inOrder(scopedEventProcessorMock, globalEventProcessorMock, beforeSendMock)

    order.verify(scopedEventProcessorMock, times(1)).process(any<IchnoisCoreEvent>(), anyOrNull())
    order.verify(globalEventProcessorMock, times(1)).process(any<IchnoisCoreEvent>(), anyOrNull())
    order.verify(beforeSendMock, times(1)).execute(any(), anyOrNull())

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Error.category, 1)),
    )

    verify(onDiscardMock, times(1)).execute(DiscardReason.BEFORE_SEND, DataCategory.Error, 1)
  }

  @Test
  fun `filter mechanism order check for scoped eventProcessor`() {
    val beforeSendMock = mock<IchnoisCoreOptions.BeforeSendCallback>()
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    val scopedEventProcessorMock = mock<EventProcessor>()
    val globalEventProcessorMock = mock<EventProcessor>()

    fixture.ichnoisOptions.onDiscard = onDiscardMock

    whenever(scopedEventProcessorMock.process(any<IchnoisCoreEvent>(), anyOrNull())).thenReturn(null)
    whenever(globalEventProcessorMock.process(any<IchnoisCoreEvent>(), anyOrNull())).thenReturn(null)
    whenever(beforeSendMock.execute(any(), anyOrNull())).thenReturn(null)

    val sut = fixture.getSut { options ->
      options.sampleRate = 0.000000000001
      options.addIgnoredExceptionForType(NegativeArraySizeException::class.java)
      options.beforeSend = beforeSendMock
      options.addEventProcessor(globalEventProcessorMock)
    }
    val scope = givenScopeWithStartedSession()
    scope.addEventProcessor(scopedEventProcessorMock)

    sut.captureException(IllegalStateException(), scope)

    val order = inOrder(scopedEventProcessorMock, globalEventProcessorMock, beforeSendMock)

    order.verify(scopedEventProcessorMock, times(1)).process(any<IchnoisCoreEvent>(), anyOrNull())
    order.verify(globalEventProcessorMock, never()).process(any<IchnoisCoreEvent>(), anyOrNull())
    order.verify(beforeSendMock, never()).execute(any(), anyOrNull())

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Error.category, 1)),
    )

    verify(onDiscardMock, times(1)).execute(DiscardReason.EVENT_PROCESSOR, DataCategory.Error, 1)
  }

  @Test
  fun `filter mechanism order check for global eventProcessor`() {
    val beforeSendMock = mock<IchnoisCoreOptions.BeforeSendCallback>()
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    val scopedEventProcessorMock = mock<EventProcessor>()
    val globalEventProcessorMock = mock<EventProcessor>()

    fixture.ichnoisOptions.onDiscard = onDiscardMock

    whenever(scopedEventProcessorMock.process(any<IchnoisCoreEvent>(), anyOrNull())).doAnswer {
      it.arguments.first() as IchnoisCoreEvent
    }
    whenever(globalEventProcessorMock.process(any<IchnoisCoreEvent>(), anyOrNull())).thenReturn(null)
    whenever(beforeSendMock.execute(any(), anyOrNull())).thenReturn(null)

    val sut = fixture.getSut { options ->
      options.sampleRate = 0.000000000001
      options.addIgnoredExceptionForType(NegativeArraySizeException::class.java)
      options.beforeSend = beforeSendMock
      options.addEventProcessor(globalEventProcessorMock)
    }
    val scope = givenScopeWithStartedSession()
    scope.addEventProcessor(scopedEventProcessorMock)

    sut.captureException(IllegalStateException(), scope)

    val order = inOrder(scopedEventProcessorMock, globalEventProcessorMock, beforeSendMock)

    order.verify(scopedEventProcessorMock, times(1)).process(any<IchnoisCoreEvent>(), anyOrNull())
    order.verify(globalEventProcessorMock, times(1)).process(any<IchnoisCoreEvent>(), anyOrNull())
    order.verify(beforeSendMock, never()).execute(any(), anyOrNull())

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Error.category, 1)),
    )

    verify(onDiscardMock, times(1)).execute(DiscardReason.EVENT_PROCESSOR, DataCategory.Error, 1)
  }

  @Test
  fun `can pass an attachment via hints`() {
    val sut = fixture.getSut()

    sut.captureException(IllegalStateException(), Hint.withAttachment(fixture.attachment))

    thenEnvelopeIsSentWith(eventCount = 1, sessionCount = 0, attachmentCount = 1)
  }

  @Test
  fun `an attachment passed via hint is used with scope attachments`() {
    val sut = fixture.getSut()

    val scope = givenScopeWithStartedSession()
    scope.addAttachment(fixture.attachment2)
    sut.captureException(IllegalStateException(), scope, Hint.withAttachment(fixture.attachment))

    thenEnvelopeIsSentWith(eventCount = 1, sessionCount = 1, attachmentCount = 2)
  }

  @Test
  fun `can add to attachments in beforeSend`() {
    val sut = fixture.getSut { options ->
      options.setBeforeSend { event, hints ->
        assertEquals(listOf(fixture.attachment, fixture.attachment2), hints.attachments)
        hints.addAttachment(fixture.attachment3)
        event
      }
    }

    val scope = givenScopeWithStartedSession()
    scope.addAttachment(fixture.attachment2)
    sut.captureException(IllegalStateException(), scope, Hint.withAttachment(fixture.attachment))

    thenEnvelopeIsSentWith(eventCount = 1, sessionCount = 1, attachmentCount = 3)
  }

  @Test
  fun `can replace attachments in beforeSend`() {
    val sut = fixture.getSut { options ->
      options.setBeforeSend { event, hints ->
        hints.replaceAttachments(listOf(fixture.attachment3))
        event
      }
    }

    val scope = givenScopeWithStartedSession()
    scope.addAttachment(fixture.attachment2)
    sut.captureException(IllegalStateException(), scope, Hint.withAttachment(fixture.attachment))

    thenEnvelopeIsSentWith(eventCount = 1, sessionCount = 1, attachmentCount = 1)
  }

  @Test
  fun `can add to attachments in eventProcessor`() {
    val sut = fixture.getSut { options ->
      options.addEventProcessor(
        object : EventProcessor {
          override fun process(event: IchnoisCoreEvent, hint: Hint): IchnoisCoreEvent? {
            assertEquals(listOf(fixture.attachment, fixture.attachment2), hint.attachments)
            hint.addAttachment(fixture.attachment3)
            return event
          }

          override fun process(transaction: IchnoisCoreTransaction, hint: Hint): IchnoisCoreTransaction? {
            return transaction
          }
        }
      )
    }

    val scope = givenScopeWithStartedSession()
    scope.addAttachment(fixture.attachment2)
    sut.captureException(IllegalStateException(), scope, Hint.withAttachment(fixture.attachment))

    thenEnvelopeIsSentWith(eventCount = 1, sessionCount = 1, attachmentCount = 3)
  }

  @Test
  fun `can replace attachments in eventProcessor`() {
    val sut = fixture.getSut { options ->
      options.addEventProcessor(
        object : EventProcessor {
          override fun process(event: IchnoisCoreEvent, hint: Hint): IchnoisCoreEvent? {
            hint.replaceAttachments(listOf(fixture.attachment3))
            return event
          }

          override fun process(transaction: IchnoisCoreTransaction, hint: Hint): IchnoisCoreTransaction? {
            return transaction
          }
        }
      )
    }

    val scope = givenScopeWithStartedSession()
    scope.addAttachment(fixture.attachment2)
    sut.captureException(IllegalStateException(), scope, Hint.withAttachment(fixture.attachment))

    thenEnvelopeIsSentWith(eventCount = 1, sessionCount = 1, attachmentCount = 1)
  }

  @Test
  fun `can pass an attachment via hints for transactions`() {
    val sut = fixture.getSut()
    val scope = createScope()

    sut.captureTransaction(
      IchnoisCoreTransaction(fixture.ichnoisTracer),
      scope,
      Hint.withAttachment(fixture.attachment),
    )

    thenEnvelopeIsSentWith(
      eventCount = 0,
      sessionCount = 0,
      attachmentCount = 1,
      transactionCount = 1,
    )
  }

  @Test
  fun `an attachment passed via hint is used with scope attachments for transactions`() {
    val sut = fixture.getSut()

    val scope = givenScopeWithStartedSession()
    scope.addAttachment(fixture.attachment2)

    sut.captureTransaction(
      IchnoisCoreTransaction(fixture.ichnoisTracer),
      scope,
      Hint.withAttachment(fixture.attachment),
    )

    thenEnvelopeIsSentWith(
      eventCount = 0,
      sessionCount = 0,
      attachmentCount = 2,
      transactionCount = 1,
    )
  }

  @Test
  fun `can add to attachments in eventProcessor for transactions`() {
    val sut = fixture.getSut { options ->
      options.addEventProcessor(
        object : EventProcessor {
          override fun process(event: IchnoisCoreEvent, hint: Hint): IchnoisCoreEvent? {
            return event
          }

          override fun process(transaction: IchnoisCoreTransaction, hint: Hint): IchnoisCoreTransaction? {
            assertEquals(listOf(fixture.attachment, fixture.attachment2), hint.attachments)
            hint.addAttachment(fixture.attachment3)
            return transaction
          }
        }
      )
    }

    val scope = givenScopeWithStartedSession()
    scope.addAttachment(fixture.attachment2)

    sut.captureTransaction(
      IchnoisCoreTransaction(fixture.ichnoisTracer),
      scope,
      Hint.withAttachment(fixture.attachment),
    )

    thenEnvelopeIsSentWith(
      eventCount = 0,
      sessionCount = 0,
      attachmentCount = 3,
      transactionCount = 1,
    )
  }

  @Test
  fun `can replace attachments in eventProcessor for transactions`() {
    val sut = fixture.getSut { options ->
      options.addEventProcessor(
        object : EventProcessor {
          override fun process(event: IchnoisCoreEvent, hint: Hint): IchnoisCoreEvent? {
            return event
          }

          override fun process(transaction: IchnoisCoreTransaction, hint: Hint): IchnoisCoreTransaction? {
            hint.replaceAttachments(listOf(fixture.attachment3))
            return transaction
          }
        }
      )
    }

    val scope = givenScopeWithStartedSession()
    scope.addAttachment(fixture.attachment2)

    sut.captureTransaction(
      IchnoisCoreTransaction(fixture.ichnoisTracer),
      scope,
      Hint.withAttachment(fixture.attachment),
    )

    thenEnvelopeIsSentWith(
      eventCount = 0,
      sessionCount = 0,
      attachmentCount = 1,
      transactionCount = 1,
    )
  }

  @Test
  fun `passing attachments via hint into breadcrumb ignores them`() {
    val sut = fixture.getSut { options ->
      options.setBeforeBreadcrumb { breadcrumb, hints -> breadcrumb }
    }

    val scope = givenScopeWithStartedSession()
    scope.addBreadcrumb(
      Breadcrumb.info("hello from breadcrumb"),
      Hint.withAttachment(fixture.attachment),
    )

    sut.captureException(IllegalStateException(), scope)

    thenEnvelopeIsSentWith(eventCount = 1, sessionCount = 1, attachmentCount = 0)
  }

  @Test
  fun `adding attachments in beforeBreadcrumb ignores them`() {
    val sut = fixture.getSut { options ->
      options.setBeforeBreadcrumb { breadcrumb, hints ->
        hints.addAttachment(fixture.attachment)
        breadcrumb
      }
    }

    val scope = givenScopeWithStartedSession()
    scope.addBreadcrumb(Breadcrumb.info("hello from breadcrumb"))

    sut.captureException(IllegalStateException(), scope)

    thenEnvelopeIsSentWith(eventCount = 1, sessionCount = 1, attachmentCount = 0)
  }

  @Test
  fun `TransactionEnds automatically trigger force-stop of any running transaction`() {
    val sut = fixture.getSut()

    // build up a running transaction
    val spanContext = SpanContext("op.load")
    val transaction = mock<ITransaction>()
    whenever(transaction.name).thenReturn("transaction")
    whenever(transaction.spanContext).thenReturn(spanContext)

    // scope
    val scope = mock<IScope>()
    whenever(scope.transaction).thenReturn(transaction)
    whenever(scope.breadcrumbs).thenReturn(LinkedList<Breadcrumb>())
    whenever(scope.extras).thenReturn(emptyMap())
    whenever(scope.contexts).thenReturn(Contexts())
    val scopePropagationContext = PropagationContext()
    whenever(scope.propagationContext).thenReturn(scopePropagationContext)
    doAnswer {
        (it.arguments[0] as IWithPropagationContext).accept(scopePropagationContext)
        scopePropagationContext
      }
      .whenever(scope)
      .withPropagationContext(any())

    val transactionEnd = object : TransactionEnd {}
    val transactionEndHint = HintUtils.createWithTypeCheckHint(transactionEnd)

    sut.captureEvent(IchnoisCoreEvent(), scope, transactionEndHint)

    verify(transaction).forceFinish(SpanStatus.ABORTED, false, null)
    verify(fixture.transport).send(check { assertEquals(1, it.items.count()) }, any())
  }

  @Test
  fun `when event has DiskFlushNotification, TransactionEnds set transaction id as flushable`() {
    val sut = fixture.getSut()

    // build up a running transaction
    val spanContext = SpanContext("op.load")
    val transaction = mock<ITransaction>()
    whenever(transaction.name).thenReturn("transaction")
    whenever(transaction.eventId).thenReturn(IchnoisCoreId())
    whenever(transaction.spanContext).thenReturn(spanContext)

    // scope
    val scope = mock<Scope>()
    whenever(scope.transaction).thenReturn(transaction)
    whenever(scope.breadcrumbs).thenReturn(LinkedList<Breadcrumb>())
    whenever(scope.extras).thenReturn(emptyMap())
    whenever(scope.contexts).thenReturn(Contexts())
    whenever(scope.replayId).thenReturn(IchnoisCoreId.EMPTY_ID)
    val scopePropagationContext = PropagationContext()
    whenever(scope.propagationContext).thenReturn(scopePropagationContext)
    doAnswer {
        (it.arguments[0] as IWithPropagationContext).accept(scopePropagationContext)
        scopePropagationContext
      }
      .whenever(scope)
      .withPropagationContext(any())

    var capturedEventId: IchnoisCoreId? = null
    val transactionEnd =
      object : TransactionEnd, DiskFlushNotification {
        override fun markFlushed() {}

        override fun isFlushable(eventId: IchnoisCoreId?): Boolean = true

        override fun setFlushable(eventId: IchnoisCoreId) {
          capturedEventId = eventId
        }
      }
    val transactionEndHint = HintUtils.createWithTypeCheckHint(transactionEnd)

    sut.captureEvent(IchnoisCoreEvent(), scope, transactionEndHint)

    assertEquals(transaction.eventId, capturedEventId)
    verify(transaction).forceFinish(SpanStatus.ABORTED, false, transactionEndHint)
    verify(fixture.transport).send(check { assertEquals(1, it.items.count()) }, any())
  }

  @Test
  fun `attaches trace context from span if none present yet`() {
    val sut = fixture.getSut()

    // build up a running transaction
    val spanContext = SpanContext("op.load")
    val transaction = mock<ITransaction>()
    whenever(transaction.name).thenReturn("transaction")
    whenever(transaction.spanContext).thenReturn(spanContext)

    // scope
    val scope = mock<IScope>()
    whenever(scope.transaction).thenReturn(transaction)
    whenever(scope.breadcrumbs).thenReturn(LinkedList<Breadcrumb>())
    whenever(scope.extras).thenReturn(emptyMap())
    whenever(scope.contexts).thenReturn(Contexts())
    val scopePropagationContext = PropagationContext()
    whenever(scope.propagationContext).thenReturn(scopePropagationContext)
    whenever(scope.span).thenReturn(transaction)
    doAnswer {
        (it.arguments[0] as IWithPropagationContext).accept(scopePropagationContext)
        scopePropagationContext
      }
      .whenever(scope)
      .withPropagationContext(any())

    val ichnoisEvent = IchnoisCoreEvent()
    sut.captureEvent(ichnoisEvent, scope)

    verify(fixture.transport).send(check { assertEquals(1, it.items.count()) }, any())

    assertEquals(spanContext.traceId, ichnoisEvent.contexts.trace!!.traceId)
    assertEquals(spanContext.spanId, ichnoisEvent.contexts.trace!!.spanId)
    assertNotEquals(scopePropagationContext.traceId, ichnoisEvent.contexts.trace!!.traceId)
    assertNotEquals(scopePropagationContext.spanId, ichnoisEvent.contexts.trace!!.spanId)
  }

  @Test
  fun `attaches trace context from scope if none present yet and no span on scope`() {
    val sut = fixture.getSut()

    // scope
    val scope = mock<IScope>()
    whenever(scope.breadcrumbs).thenReturn(LinkedList<Breadcrumb>())
    whenever(scope.extras).thenReturn(emptyMap())
    whenever(scope.contexts).thenReturn(Contexts())
    whenever(scope.replayId).thenReturn(IchnoisCoreId())
    val scopePropagationContext = PropagationContext()
    whenever(scope.propagationContext).thenReturn(scopePropagationContext)
    doAnswer {
        (it.arguments[0] as IWithPropagationContext).accept(scopePropagationContext)
        scopePropagationContext
      }
      .whenever(scope)
      .withPropagationContext(any())

    val ichnoisEvent = IchnoisCoreEvent()
    sut.captureEvent(ichnoisEvent, scope)

    verify(fixture.transport).send(check { assertEquals(1, it.items.count()) }, any())

    assertEquals(scopePropagationContext.traceId, ichnoisEvent.contexts.trace!!.traceId)
    assertEquals(scopePropagationContext.spanId, ichnoisEvent.contexts.trace!!.spanId)
  }

  @Test
  fun `keeps existing trace context if already present`() {
    val sut = fixture.getSut()

    // build up a running transaction
    val spanContext = SpanContext("op.load")
    val transaction = mock<ITransaction>()
    whenever(transaction.name).thenReturn("transaction")
    whenever(transaction.spanContext).thenReturn(spanContext)

    // scope
    val scope = mock<IScope>()
    whenever(scope.transaction).thenReturn(transaction)
    whenever(scope.breadcrumbs).thenReturn(LinkedList<Breadcrumb>())
    whenever(scope.extras).thenReturn(emptyMap())
    whenever(scope.contexts).thenReturn(Contexts())
    val scopePropagationContext = PropagationContext()
    whenever(scope.propagationContext).thenReturn(scopePropagationContext)
    doAnswer {
        (it.arguments[0] as IWithPropagationContext).accept(scopePropagationContext)
        scopePropagationContext
      }
      .whenever(scope)
      .withPropagationContext(any())

    val preExistingSpanContext = SpanContext("op.load")

    val ichnoisEvent = IchnoisCoreEvent()
    ichnoisEvent.contexts.setTrace(preExistingSpanContext)
    sut.captureEvent(ichnoisEvent, scope)

    verify(fixture.transport).send(check { assertEquals(1, it.items.count()) }, any())

    assertEquals(preExistingSpanContext.traceId, ichnoisEvent.contexts.trace!!.traceId)
    assertEquals(preExistingSpanContext.spanId, ichnoisEvent.contexts.trace!!.spanId)
    assertNotEquals(spanContext.traceId, ichnoisEvent.contexts.trace!!.traceId)
    assertNotEquals(spanContext.spanId, ichnoisEvent.contexts.trace!!.spanId)
    assertNotEquals(scopePropagationContext.traceId, ichnoisEvent.contexts.trace!!.traceId)
    assertNotEquals(scopePropagationContext.spanId, ichnoisEvent.contexts.trace!!.spanId)
  }

  @Test
  fun `uses propagation context on scope for trace header if no transaction is on scope`() {
    val sut = fixture.getSut()

    // scope
    val scope = mock<IScope>()
    whenever(scope.breadcrumbs).thenReturn(LinkedList<Breadcrumb>())
    whenever(scope.extras).thenReturn(emptyMap())
    whenever(scope.contexts).thenReturn(Contexts())
    val replayId = IchnoisCoreId()
    whenever(scope.replayId).thenReturn(replayId)
    val scopePropagationContext = PropagationContext()
    doAnswer {
        (it.arguments[0] as IWithPropagationContext).accept(scopePropagationContext)
        scopePropagationContext
      }
      .whenever(scope)
      .withPropagationContext(any())
    whenever(scope.propagationContext).thenReturn(scopePropagationContext)
    doAnswer {
        (it.arguments[0] as IWithPropagationContext).accept(scopePropagationContext)
        scopePropagationContext
      }
      .whenever(scope)
      .withPropagationContext(any())

    val ichnoisEvent = IchnoisCoreEvent()
    sut.captureEvent(ichnoisEvent, scope)

    verify(fixture.transport)
      .send(
        check {
          assertNotNull(it.header.traceContext)
          assertEquals(scopePropagationContext.traceId, it.header.traceContext!!.traceId)
          assertEquals(replayId, it.header.traceContext!!.replayId)
        },
        any(),
      )
  }

  @Test
  fun `uses trace context on transaction for trace header if a transaction is on scope`() {
    val sut = fixture.getSut()

    // build up a running transaction
    val spanContext = SpanContext("op.load")
    val transaction = mock<ITransaction>()
    whenever(transaction.name).thenReturn("transaction")
    whenever(transaction.spanContext).thenReturn(spanContext)
    val transactionTraceContext = TraceContext(IchnoisCoreId(), "pubkey")
    whenever(transaction.traceContext()).thenReturn(transactionTraceContext)

    // scope
    val scope = mock<IScope>()
    whenever(scope.transaction).thenReturn(transaction)
    whenever(scope.breadcrumbs).thenReturn(LinkedList<Breadcrumb>())
    whenever(scope.extras).thenReturn(emptyMap())
    whenever(scope.contexts).thenReturn(Contexts())
    val scopePropagationContext = PropagationContext()
    whenever(scope.propagationContext).thenReturn(scopePropagationContext)
    doAnswer {
        (it.arguments[0] as IWithPropagationContext).accept(scopePropagationContext)
        scopePropagationContext
      }
      .whenever(scope)
      .withPropagationContext(any())

    val preExistingSpanContext = SpanContext("op.load")

    val ichnoisEvent = IchnoisCoreEvent()
    ichnoisEvent.contexts.setTrace(preExistingSpanContext)
    sut.captureEvent(ichnoisEvent, scope)

    verify(fixture.transport)
      .send(
        check {
          assertNotNull(it.header.traceContext)
          assertEquals(transactionTraceContext.traceId, it.header.traceContext!!.traceId)
        },
        any(),
      )
  }

  @Test
  fun `beforeEnvelopeCallback is executed`() {
    var beforeEnvelopeCalled = false
    val sut = fixture.getSut { options ->
      options.beforeEnvelopeCallback = IchnoisCoreOptions.BeforeEnvelopeCallback { _, _ ->
        beforeEnvelopeCalled = true
      }
    }

    sut.captureEvent(IchnoisCoreEvent(), Hint())

    assertTrue(beforeEnvelopeCalled)
  }

  @Test
  fun `when beforeEnvelopeCallback captures another event, the nested capture is dropped and does not recurse`() {
    var invocations = 0
    lateinit var sut: IchnoisCoreClient
    val options = { options: IchnoisCoreOptions ->
      options.beforeEnvelopeCallback = IchnoisCoreOptions.BeforeEnvelopeCallback { _, _ ->
        invocations++
        sut.captureEvent(IchnoisCoreEvent())
      }
    }
    sut = fixture.getSut(options)

    sut.captureEvent(IchnoisCoreEvent(), Hint())

    assertEquals(1, invocations)
    verify(fixture.transport, times(1)).send(any(), anyOrNull())
  }

  @Test
  fun `when beforeEnvelopeCallback captures an envelope, the nested envelope is dropped and does not recurse`() {
    var invocations = 0
    lateinit var sut: IchnoisCoreClient
    val options = { options: IchnoisCoreOptions ->
      options.beforeEnvelopeCallback = IchnoisCoreOptions.BeforeEnvelopeCallback { _, _ ->
        invocations++
        sut.captureEnvelope(IchnoisCoreEnvelope(IchnoisCoreId(UUID.randomUUID()), null, setOf()))
      }
    }
    sut = fixture.getSut(options)

    sut.captureEvent(IchnoisCoreEvent(), Hint())

    // Callback runs only for the outer envelope; the nested captureEnvelope is dropped in
    // sendEnvelope before its callback would run.
    assertEquals(1, invocations)
    verify(fixture.transport, times(1)).send(any(), anyOrNull())
  }

  @Test
  fun `beforeEnvelopeCallback may fail, but the transport is still sends the envelope `() {
    val sut = fixture.getSut { options ->
      options.beforeEnvelopeCallback = IchnoisCoreOptions.BeforeEnvelopeCallback { _, _ ->
        RuntimeException("hook failed")
      }
    }

    sut.captureEvent(IchnoisCoreEvent(), Hint())
    verify(fixture.transport).send(anyOrNull(), anyOrNull())
  }

  // region Replay

  @Test
  fun `when captureReplayEvent, envelope is sent`() {
    val sut = fixture.getSut()
    val replayEvent = createReplayEvent()

    sut.captureReplayEvent(replayEvent, null, null)

    verify(fixture.transport)
      .send(
        check { actual ->
          assertEquals(replayEvent.eventId, actual.header.eventId)
          assertEquals(fixture.ichnoisOptions.sdkVersion, actual.header.sdkVersion)

          assertEquals(1, actual.items.count())
          val item = actual.items.first()
          assertEquals(IchnoisCoreItemType.ReplayVideo, item.header.type)

          val unpacker = MessagePack.newDefaultUnpacker(item.data)
          val mapSize = unpacker.unpackMapHeader()
          assertEquals(1, mapSize)
        },
        any<Hint>(),
      )
  }

  @Test
  fun `when captureReplayEvent with recording, adds it to payload`() {
    val sut = fixture.getSut()
    val replayEvent = createReplayEvent()

    val hint = Hint().apply { replayRecording = createReplayRecording() }
    sut.captureReplayEvent(replayEvent, null, hint)

    verify(fixture.transport)
      .send(
        check { actual ->
          assertEquals(replayEvent.eventId, actual.header.eventId)
          assertEquals(fixture.ichnoisOptions.sdkVersion, actual.header.sdkVersion)

          assertEquals(1, actual.items.count())
          val item = actual.items.first()
          assertEquals(IchnoisCoreItemType.ReplayVideo, item.header.type)

          val unpacker = MessagePack.newDefaultUnpacker(item.data)
          val mapSize = unpacker.unpackMapHeader()
          assertEquals(2, mapSize)
        },
        any<Hint>(),
      )
  }

  @Test
  fun `when captureReplayEvent, omits breadcrumbs and extras from scope`() {
    val sut = fixture.getSut()
    val replayEvent = createReplayEvent()

    sut.captureReplayEvent(replayEvent, createScope(), null)

    verify(fixture.transport)
      .send(
        check { actual ->
          val item = actual.items.first()

          val unpacker = MessagePack.newDefaultUnpacker(item.data)
          val mapSize = unpacker.unpackMapHeader()
          for (i in 0 until mapSize) {
            val key = unpacker.unpackString()
            when (key) {
              IchnoisCoreItemType.ReplayEvent.itemType -> {
                val replayEventLength = unpacker.unpackBinaryHeader()
                val replayEventBytes = unpacker.readPayload(replayEventLength)
                val actualReplayEvent =
                  fixture.ichnoisOptions.serializer.deserialize(
                    InputStreamReader(replayEventBytes.inputStream()),
                    IchnoisCoreReplayEvent::class.java,
                  )
                // sanity check
                assertEquals("id", actualReplayEvent!!.user!!.id)

                assertNull(actualReplayEvent.breadcrumbs)
                assertNull(actualReplayEvent.extras)
              }
            }
          }
        },
        any<Hint>(),
      )
  }

  @Test
  fun `when replay event is dropped, captures client report with datacategory replay`() {
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()

    fixture.ichnoisOptions.onDiscard = onDiscardMock

    fixture.ichnoisOptions.addEventProcessor(DropEverythingEventProcessor())
    val sut = fixture.getSut()
    val replayEvent = createReplayEvent()

    sut.captureReplayEvent(replayEvent, createScope(), null)

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Replay.category, 1)),
    )

    verify(onDiscardMock, times(1)).execute(DiscardReason.EVENT_PROCESSOR, DataCategory.Replay, 1)
  }

  @Test
  fun `throwing replay processor drops replay and stops callbacks`() {
    val throwingProcessor = mock<EventProcessor>()
    val nextProcessor = mock<EventProcessor>()
    val beforeSend = mock<IchnoisCoreOptions.BeforeSendReplayCallback>()
    val onDiscard = mock<IchnoisCoreOptions.OnDiscardCallback>()
    whenever(throwingProcessor.process(any<IchnoisCoreReplayEvent>(), anyOrNull()))
      .thenThrow(IllegalStateException("test"))
    fixture.ichnoisOptions.addEventProcessor(throwingProcessor)
    fixture.ichnoisOptions.addEventProcessor(nextProcessor)
    fixture.ichnoisOptions.beforeSendReplay = beforeSend
    fixture.ichnoisOptions.onDiscard = onDiscard

    val id = fixture.getSut().captureReplayEvent(createReplayEvent(), createScope(), null)

    assertThat(id).isEqualTo(IchnoisCoreId.EMPTY_ID)
    verify(nextProcessor, never()).process(any<IchnoisCoreReplayEvent>(), anyOrNull())
    verify(beforeSend, never()).execute(any(), anyOrNull())
    verify(fixture.transport, never()).send(any(), anyOrNull())
    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.CALLBACK_ERROR.reason, DataCategory.Replay.category, 1)),
    )
    verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Replay, 1)
  }

  @Test
  fun `throwing SDK replay processor keeps replay and runs remaining callbacks`() {
    val processor = mock<IchnoisCoreEventProcessor>()
    val nextProcessor = mock<EventProcessor>()
    val beforeSend = mock<IchnoisCoreOptions.BeforeSendReplayCallback>()
    val onDiscard = mock<IchnoisCoreOptions.OnDiscardCallback>()
    val logger = mock<ILogger>()
    val failure = IllegalStateException("SDK processor failed")
    val replay = createReplayEvent()
    whenever(processor.process(any<IchnoisCoreReplayEvent>(), any())).thenThrow(failure)
    whenever(nextProcessor.process(any<IchnoisCoreReplayEvent>(), any())).thenAnswer { it.arguments[0] }
    whenever(beforeSend.execute(any(), any())).thenAnswer { it.arguments[0] }
    fixture.ichnoisOptions.addEventProcessor(processor)
    fixture.ichnoisOptions.addEventProcessor(nextProcessor)
    fixture.ichnoisOptions.beforeSendReplay = beforeSend
    fixture.ichnoisOptions.onDiscard = onDiscard
    fixture.ichnoisOptions.setLogger(logger)

    val id = fixture.getSut().captureReplayEvent(replay, createScope(), null)

    assertThat(id).isEqualTo(replay.eventId)
    verify(nextProcessor).process(eq(replay), any())
    verify(beforeSend).execute(eq(replay), any())
    verify(fixture.transport)
      .send(
        check {
          assertThat(it.header.eventId).isEqualTo(id)
          assertThat(it.items.first().header.type).isEqualTo(IchnoisCoreItemType.ReplayVideo)
        },
        anyOrNull(),
      )
    verify(logger)
      .log(
        eq(IchnoisCoreLevel.ERROR),
        eq(failure),
        eq("An exception occurred while processing replay event by processor: %s"),
        eq(processor.javaClass.name),
      )
    assertClientReport(fixture.ichnoisOptions.clientReportRecorder, emptyList())
    verifyNoInteractions(onDiscard)
  }

  @Test
  fun `calls captureReplay on replay controller for error events`() {
    var called = false
    fixture.ichnoisOptions.setReplayController(
      object : ReplayController by NoOpReplayController.getInstance() {
        override fun captureReplay(isTerminating: Boolean?): IchnoisCoreId {
          called = true
          return IchnoisCoreId.EMPTY_ID
        }
      }
    )
    val sut = fixture.getSut()

    sut.captureEvent(IchnoisCoreEvent().apply { exceptions = listOf(IchnoisCoreException()) })
    assertTrue(called)
  }

  @Test
  fun `calls captureReplay on replay controller for crash events and sets isTerminating`() {
    var terminated: Boolean? = false
    fixture.ichnoisOptions.setReplayController(
      object : ReplayController by NoOpReplayController.getInstance() {
        override fun captureReplay(isTerminating: Boolean?): IchnoisCoreId {
          terminated = isTerminating
          return IchnoisCoreId.EMPTY_ID
        }
      }
    )
    val sut = fixture.getSut()

    sut.captureEvent(
      IchnoisCoreEvent().apply {
        exceptions =
          listOf(IchnoisCoreException().apply { mechanism = Mechanism().apply { isHandled = false } })
      }
    )
    assertTrue(terminated == true)
  }

  @Test
  fun `sets replayId on frozen transaction baggage after captureReplay for error events`() {
    val replayId = IchnoisCoreId()
    fixture.ichnoisOptions.setReplayController(
      object : ReplayController by NoOpReplayController.getInstance() {
        override fun captureReplay(isTerminating: Boolean?): IchnoisCoreId = replayId
      }
    )
    val sut = fixture.getSut()

    val baggage = Baggage(fixture.ichnoisOptions.logger)
    baggage.traceId = IchnoisCoreId().toString()
    baggage.freeze()

    val spanContext = SpanContext("op.load")
    spanContext.baggage = baggage
    val transaction = mock<ITransaction>()
    whenever(transaction.spanContext).thenReturn(spanContext)
    whenever(transaction.traceContext()).thenReturn(baggage.toTraceContext())

    val scope = mock<IScope>()
    whenever(scope.transaction).thenReturn(transaction)
    whenever(scope.span).thenReturn(transaction)
    whenever(scope.replayId).thenReturn(replayId)
    whenever(scope.breadcrumbs).thenReturn(LinkedList<Breadcrumb>())
    whenever(scope.extras).thenReturn(emptyMap())
    whenever(scope.contexts).thenReturn(Contexts())
    whenever(scope.propagationContext).thenReturn(PropagationContext())

    sut.captureEvent(IchnoisCoreEvent().apply { exceptions = listOf(IchnoisCoreException()) }, scope)

    assertEquals(replayId.toString(), baggage.getReplayId())
  }

  @Test
  fun `cleans up replay folder for Backfillable replay events`() {
    val dir = File(tmpDir.newFolder().absolutePath)
    val sut = fixture.getSut()
    val replayEvent =
      createReplayEvent().apply { videoFile = File(dir, "hello.txt").apply { writeText("hello") } }

    sut.captureReplayEvent(
      replayEvent,
      createScope(),
      HintUtils.createWithTypeCheckHint(BackfillableHint()),
    )

    verify(fixture.transport)
      .send(
        check { actual ->
          val item = actual.items.first()
          item.data
          assertFalse(dir.exists())
        },
        any<Hint>(),
      )
  }

  @Test
  fun `does not captureReplay for backfillable events`() {
    var called = false
    fixture.ichnoisOptions.setReplayController(
      object : ReplayController by NoOpReplayController.getInstance() {
        override fun captureReplay(isTerminating: Boolean?): IchnoisCoreId {
          called = true
          return IchnoisCoreId.EMPTY_ID
        }
      }
    )
    val sut = fixture.getSut()

    sut.captureEvent(
      IchnoisCoreEvent().apply {
        exceptions =
          listOf(IchnoisCoreException().apply { mechanism = Mechanism().apply { isHandled = false } })
      },
      HintUtils.createWithTypeCheckHint(BackfillableHint()),
    )
    assertFalse(called)
  }

  @Test
  fun `does not captureReplay for cached events`() {
    var called = false
    fixture.ichnoisOptions.setReplayController(
      object : ReplayController by NoOpReplayController.getInstance() {
        override fun captureReplay(isTerminating: Boolean?): IchnoisCoreId {
          called = true
          return IchnoisCoreId.EMPTY_ID
        }
      }
    )
    val sut = fixture.getSut()

    sut.captureEvent(
      IchnoisCoreEvent().apply {
        exceptions =
          listOf(IchnoisCoreException().apply { mechanism = Mechanism().apply { isHandled = false } })
      },
      HintUtils.createWithTypeCheckHint(CachedHint()),
    )
    assertFalse(called)
  }

  @Test
  fun `when beforeErrorSampling captures another event, the nested capture is dropped and does not recurse`() {
    var invocations = 0
    lateinit var sut: IchnoisCoreClient
    fixture.ichnoisOptions.sessionReplay.beforeErrorSampling =
      IchnoisCoreReplayOptions.BeforeErrorSamplingCallback { _, _ ->
        invocations++
        sut.captureEvent(IchnoisCoreEvent().apply { exceptions = listOf(IchnoisCoreException()) })
        true
      }
    sut = fixture.getSut()

    sut.captureEvent(IchnoisCoreEvent().apply { exceptions = listOf(IchnoisCoreException()) })

    assertEquals(1, invocations)
    verify(fixture.transport, times(1)).send(any(), anyOrNull())
  }

  @Test
  fun `beforeErrorSampling returning false skips captureReplay`() {
    var called = false
    fixture.ichnoisOptions.setReplayController(
      object : ReplayController by NoOpReplayController.getInstance() {
        override fun captureReplay(isTerminating: Boolean?): IchnoisCoreId {
          called = true
          return IchnoisCoreId.EMPTY_ID
        }
      }
    )
    fixture.ichnoisOptions.sessionReplay.beforeErrorSampling =
      IchnoisCoreReplayOptions.BeforeErrorSamplingCallback { _, _ ->
        false
      }
    val sut = fixture.getSut()

    sut.captureEvent(IchnoisCoreEvent().apply { exceptions = listOf(IchnoisCoreException()) })
    assertFalse(called)
  }

  @Test
  fun `beforeErrorSampling returning true proceeds with captureReplay`() {
    var called = false
    fixture.ichnoisOptions.setReplayController(
      object : ReplayController by NoOpReplayController.getInstance() {
        override fun captureReplay(isTerminating: Boolean?): IchnoisCoreId {
          called = true
          return IchnoisCoreId.EMPTY_ID
        }
      }
    )
    fixture.ichnoisOptions.sessionReplay.beforeErrorSampling =
      IchnoisCoreReplayOptions.BeforeErrorSamplingCallback { _, _ ->
        true
      }
    val sut = fixture.getSut()

    sut.captureEvent(IchnoisCoreEvent().apply { exceptions = listOf(IchnoisCoreException()) })
    assertTrue(called)
  }

  @Test
  fun `beforeErrorSampling not set proceeds with captureReplay`() {
    var called = false
    fixture.ichnoisOptions.setReplayController(
      object : ReplayController by NoOpReplayController.getInstance() {
        override fun captureReplay(isTerminating: Boolean?): IchnoisCoreId {
          called = true
          return IchnoisCoreId.EMPTY_ID
        }
      }
    )
    val sut = fixture.getSut()

    sut.captureEvent(IchnoisCoreEvent().apply { exceptions = listOf(IchnoisCoreException()) })
    assertTrue(called)
  }

  @Test
  fun `beforeErrorSampling throwing exception skips replay but still sends errors and crashes`() {
    val replayController = mock<ReplayController>()
    whenever(replayController.replayId).thenReturn(IchnoisCoreId())
    whenever(replayController.captureReplay(anyOrNull())).thenReturn(IchnoisCoreId.EMPTY_ID)
    fixture.ichnoisOptions.setReplayController(replayController)
    val logger = mock<ILogger>()
    fixture.ichnoisOptions.setLogger(logger)
    val exception = RuntimeException("test")
    fixture.ichnoisOptions.sessionReplay.beforeErrorSampling =
      IchnoisCoreReplayOptions.BeforeErrorSamplingCallback { _, _ ->
        throw exception
      }
    val sut = fixture.getSut()
    val events =
      listOf(true, false).map { handled ->
        IchnoisCoreEvent().apply {
          exceptions =
            listOf(
              IchnoisCoreException().apply { mechanism = Mechanism().apply { isHandled = handled } }
            )
        }
      }

    events.forEach { event ->
      assertThat(sut.captureEvent(event)).isEqualTo(event.eventId)
    }

    verify(replayController, never()).captureReplay(anyOrNull())
    val envelopes = argumentCaptor<IchnoisCoreEnvelope>()
    verify(fixture.transport, times(2)).send(envelopes.capture(), anyOrNull())
    assertThat(envelopes.allValues.map { getEventFromData(it.items.first().data).eventId })
      .containsExactlyElementsIn(events.map { it.eventId })
      .inOrder()
    verify(logger, times(2))
      .log(
        IchnoisCoreLevel.ERROR,
        "The beforeErrorSampling callback threw an exception. Skipping replay capture.",
        exception,
      )
    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.CALLBACK_ERROR.reason, DataCategory.Replay.category, 2)),
    )
  }

  @Test
  fun `beforeErrorSampling throwing exception does not prevent replay capture for subsequent events`() {
    val replayController = mock<ReplayController>()
    whenever(replayController.replayId).thenReturn(IchnoisCoreId.EMPTY_ID)
    whenever(replayController.captureReplay(anyOrNull())).thenReturn(IchnoisCoreId.EMPTY_ID)
    fixture.ichnoisOptions.setReplayController(replayController)
    var invocations = 0
    fixture.ichnoisOptions.sessionReplay.beforeErrorSampling =
      IchnoisCoreReplayOptions.BeforeErrorSamplingCallback { _, _ ->
        invocations++
        if (invocations == 1) {
          throw RuntimeException("test")
        }
        true
      }
    val sut = fixture.getSut()

    sut.captureEvent(IchnoisCoreEvent().apply { exceptions = listOf(IchnoisCoreException()) })
    verify(replayController, never()).captureReplay(anyOrNull())

    val event = IchnoisCoreEvent().apply { exceptions = listOf(IchnoisCoreException()) }
    assertThat(sut.captureEvent(event)).isEqualTo(event.eventId)

    assertThat(invocations).isEqualTo(2)
    verify(replayController).captureReplay(false)
    verify(fixture.transport, times(2)).send(any(), anyOrNull())
    assertClientReport(fixture.ichnoisOptions.clientReportRecorder, emptyList())
  }

  @Test
  fun `beforeErrorSampling receives correct event and hint`() {
    var receivedEvent: IchnoisCoreEvent? = null
    var receivedHint: Hint? = null
    fixture.ichnoisOptions.setReplayController(
      object : ReplayController by NoOpReplayController.getInstance() {
        override fun captureReplay(isTerminating: Boolean?): IchnoisCoreId = IchnoisCoreId.EMPTY_ID
      }
    )
    fixture.ichnoisOptions.sessionReplay.beforeErrorSampling =
      IchnoisCoreReplayOptions.BeforeErrorSamplingCallback { event, hint ->
        receivedEvent = event
        receivedHint = hint
        true
      }
    val sut = fixture.getSut()

    val event = IchnoisCoreEvent().apply { exceptions = listOf(IchnoisCoreException()) }
    val hint = Hint()
    sut.captureEvent(event, hint)
    assertSame(event, receivedEvent)
    assertSame(hint, receivedHint)
  }

  @Test
  fun `captures replay for cached events with apply scope`() {
    var called = false
    fixture.ichnoisOptions.setReplayController(
      object : ReplayController by NoOpReplayController.getInstance() {
        override fun captureReplay(isTerminating: Boolean?): IchnoisCoreId {
          called = true
          return IchnoisCoreId.EMPTY_ID
        }
      }
    )
    val sut = fixture.getSut()

    sut.captureEvent(
      IchnoisCoreEvent().apply {
        exceptions =
          listOf(IchnoisCoreException().apply { mechanism = Mechanism().apply { isHandled = false } })
      },
      HintUtils.createWithTypeCheckHint(CachedWithApplyScopeHint()),
    )
    assertTrue(called)
  }

  @Test
  fun `when beforeSendReplay is set, callback is invoked`() {
    var invoked = false
    fixture.ichnoisOptions.setBeforeSendReplay { replay: IchnoisCoreReplayEvent, _: Hint ->
      invoked = true
      replay
    }

    fixture.getSut().captureReplayEvent(IchnoisCoreReplayEvent(), Scope(fixture.ichnoisOptions), Hint())

    assertTrue(invoked)
  }

  @Test
  fun `when beforeSendReplay returns null, event is dropped`() {
    fixture.ichnoisOptions.setBeforeSendReplay { replay: IchnoisCoreReplayEvent, _: Hint -> null }

    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    fixture.ichnoisOptions.onDiscard = onDiscardMock

    fixture.getSut().captureReplayEvent(IchnoisCoreReplayEvent(), Scope(fixture.ichnoisOptions), Hint())

    verify(fixture.transport, never()).send(any(), anyOrNull())

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Replay.category, 1)),
    )

    verify(onDiscardMock, times(1)).execute(DiscardReason.BEFORE_SEND, DataCategory.Replay, 1)
  }

  @Test
  fun `when beforeSendReplay returns new instance, new instance is sent`() {
    val expected = IchnoisCoreReplayEvent().apply { tags = mapOf("test" to "test") }
    fixture.ichnoisOptions.setBeforeSendReplay { _, _ -> expected }

    fixture.getSut().captureReplayEvent(IchnoisCoreReplayEvent(), Scope(fixture.ichnoisOptions), Hint())

    verify(fixture.transport)
      .send(
        check {
          val replay = getReplayFromData(it.items.first().data)
          assertEquals("test", replay!!.tags!!["test"])
        },
        anyOrNull(),
      )
    verifyNoMoreInteractions(fixture.transport)
  }

  @Test
  fun `when beforeSendReplay throws an exception, replay is dropped`() {
    val exception = Exception("test")

    exception.stackTrace.toString()
    fixture.ichnoisOptions.setBeforeSendReplay { _, _ -> throw exception }

    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    fixture.ichnoisOptions.onDiscard = onDiscardMock

    val id =
      fixture.getSut().captureReplayEvent(IchnoisCoreReplayEvent(), Scope(fixture.ichnoisOptions), Hint())

    assertEquals(IchnoisCoreId.EMPTY_ID, id)

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.CALLBACK_ERROR.reason, DataCategory.Replay.category, 1)),
    )

    verify(onDiscardMock, times(1)).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Replay, 1)
  }

  // endregion

  // region Feedback

  @Test
  fun `when captureFeedback is called, ichnois event contains feedback in contexts and header type`() {
    var sentEvent: IchnoisCoreEvent? = null
    fixture.ichnoisOptions.setBeforeSendFeedback { e, _ ->
      sentEvent = e
      e
    }
    val sut = fixture.getSut()
    val scope = createScope()
    sut.captureFeedback(Feedback("message"), null, scope)

    val sentFeedback = sentEvent!!.contexts.feedback
    assertNotNull(sentFeedback)
    assertEquals("message", sentFeedback.message)
    assertNull(sentFeedback.replayId)
    assertNull(sentFeedback.url)

    verify(fixture.transport)
      .send(
        check { assertEquals(IchnoisCoreItemType.Feedback, it.items.first().header.type) },
        anyOrNull(),
      )
  }

  @Test
  fun `when captureFeedback, scope data is attached to feedback`() {
    var sentEvent: IchnoisCoreEvent? = null
    fixture.ichnoisOptions.setBeforeSendFeedback { e, _ ->
      sentEvent = e
      e
    }
    val sut = fixture.getSut()
    val scope = createScope()
    val scopeReplayId = IchnoisCoreId()
    scope.contexts.setTrace(SpanContext("test"))
    scope.setContexts("context-key", "context-value")
    scope.screen = "screen"
    scope.replayId = scopeReplayId
    sut.captureFeedback(Feedback("message"), null, scope)

    val sentFeedback = sentEvent!!.contexts.feedback
    assertNotNull(sentFeedback)
    // User, tags and contexts are applied to the feedback
    assertEquals(scope.user, sentEvent!!.user)
    assertEquals("tags", sentEvent!!.tags!!["tags"])
    assertEquals(
      scope.contexts.trace!!.traceId.toString(),
      sentEvent!!.contexts.trace!!.traceId.toString(),
    )
    assertEquals(mapOf("value" to "context-value"), sentEvent!!.contexts["context-key"])
    // currently running replay id set in scope is applied to feedback
    assertEquals(scopeReplayId, sentFeedback.replayId)
    // screen set to scope is applied as url
    assertEquals("screen", sentFeedback.url)
    // extras and breadcrumbs are not applied to feedback
    assertNull(sentEvent!!.extras)
    assertNull(sentEvent!!.breadcrumbs)
  }

  @Test
  fun `when captureFeedback, replay controller is stopped if no replay id is provided`() {
    var sentEvent: IchnoisCoreEvent? = null
    fixture.ichnoisOptions.setBeforeSendFeedback { e, _ ->
      sentEvent = e
      e
    }
    val replayController = mock<ReplayController>()
    val replayId = IchnoisCoreId()
    val scope = createScope()
    whenever(replayController.captureReplay(any())).thenReturn(replayId)
    val sut = fixture.getSut { it.setReplayController(replayController) }
    // When there is no replay id in the feedback
    sut.captureFeedback(Feedback("message"), null, scope)

    // Then the replay controller captures the replay
    verify(replayController).captureReplay(eq(false))

    val sentFeedback = sentEvent!!.contexts.feedback
    assertNotNull(sentFeedback)
    // And the replay id returned by the replay controller is set
    assertEquals(replayId, sentFeedback.replayId)
  }

  @Test
  fun `when captureFeedback, replay controller is not stopped if replay id is provided`() {
    var sentEvent: IchnoisCoreEvent? = null
    fixture.ichnoisOptions.setBeforeSendFeedback { e, _ ->
      sentEvent = e
      e
    }
    val replayController = mock<ReplayController>()
    val replayId = IchnoisCoreId()
    val scope = createScope()
    whenever(replayController.captureReplay(any())).thenReturn(replayId)
    val sut = fixture.getSut { it.setReplayController(replayController) }
    // When there is replay id in the feedback
    val feedback = Feedback("message")
    feedback.setReplayId(IchnoisCoreId())
    sut.captureFeedback(feedback, null, scope)

    // Then the replay controller doesn't capture the replay
    verify(replayController, never()).captureReplay(any())

    val sentFeedback = sentEvent!!.contexts.feedback
    assertNotNull(sentFeedback)
    // And the replay id is set to the one from the scope (coming from the replay controller)
    assertNotNull(sentFeedback.replayId)
    assertNotEquals(replayId, sentFeedback.replayId)
  }

  @Test
  fun `when beforeSendFeedback is set, callback is invoked`() {
    var invoked = false
    fixture.ichnoisOptions.setBeforeSendFeedback { event: IchnoisCoreEvent, _: Hint ->
      invoked = true
      event
    }
    fixture.getSut().captureFeedback(Feedback("message"), null, createScope())
    assertTrue(invoked)
  }

  @Test
  fun `when beforeSendFeedback returns null, feedback is dropped`() {
    fixture.ichnoisOptions.setBeforeSendFeedback { event: IchnoisCoreEvent, _: Hint -> null }
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    fixture.ichnoisOptions.onDiscard = onDiscardMock

    fixture.getSut().captureFeedback(Feedback("message"), null, createScope())
    verify(fixture.transport, never()).send(any(), anyOrNull())

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(DiscardedEvent(DiscardReason.BEFORE_SEND.reason, DataCategory.Feedback.category, 1)),
    )

    verify(onDiscardMock, times(1)).execute(DiscardReason.BEFORE_SEND, DataCategory.Feedback, 1)
  }

  @Test
  fun `when beforeSendFeedback returns new instance, new instance is sent`() {
    val expected = IchnoisCoreEvent().apply { contexts.setFeedback(Feedback("expected")) }
    fixture.ichnoisOptions.setBeforeSendFeedback { _, _ -> expected }

    fixture.getSut().captureFeedback(Feedback("sent"), null, Scope(fixture.ichnoisOptions))

    verify(fixture.transport)
      .send(
        check {
          val event = getEventFromData(it.items.first().data)
          assertEquals("expected", event.contexts.feedback!!.message)
        },
        anyOrNull(),
      )
    verifyNoMoreInteractions(fixture.transport)
  }

  @Test
  fun `when beforeSendFeedback throws an exception, feedback is dropped`() {
    val exception = Exception("test")
    fixture.ichnoisOptions.setBeforeSendFeedback { _, _ -> throw exception }

    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    fixture.ichnoisOptions.onDiscard = onDiscardMock

    val id =
      fixture.getSut().captureFeedback(Feedback("message"), null, Scope(fixture.ichnoisOptions))
    assertEquals(IchnoisCoreId.EMPTY_ID, id)

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(
        DiscardedEvent(DiscardReason.CALLBACK_ERROR.reason, DataCategory.Feedback.category, 1)
      ),
    )

    verify(onDiscardMock, times(1)).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Feedback, 1)
  }

  @Test
  fun `when feedback is dropped, captures client report with datacategory feedback`() {
    fixture.ichnoisOptions.addEventProcessor(DropEverythingEventProcessor())
    val onDiscardMock = mock<IchnoisCoreOptions.OnDiscardCallback>()
    fixture.ichnoisOptions.onDiscard = onDiscardMock

    val sut = fixture.getSut()
    sut.captureFeedback(Feedback("message"), null, createScope())

    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(
        DiscardedEvent(DiscardReason.EVENT_PROCESSOR.reason, DataCategory.Feedback.category, 1)
      ),
    )

    verify(onDiscardMock, times(1)).execute(DiscardReason.EVENT_PROCESSOR, DataCategory.Feedback, 1)
  }

  @Test
  fun `throwing feedback processor drops feedback and stops callbacks`() {
    val throwingProcessor = mock<EventProcessor>()
    val nextProcessor = mock<EventProcessor>()
    val beforeSend = mock<IchnoisCoreOptions.BeforeSendCallback>()
    val onDiscard = mock<IchnoisCoreOptions.OnDiscardCallback>()
    whenever(throwingProcessor.process(any<IchnoisCoreEvent>(), anyOrNull()))
      .thenThrow(IllegalStateException("test"))
    fixture.ichnoisOptions.addEventProcessor(throwingProcessor)
    fixture.ichnoisOptions.addEventProcessor(nextProcessor)
    fixture.ichnoisOptions.beforeSendFeedback = beforeSend
    fixture.ichnoisOptions.onDiscard = onDiscard

    val id = fixture.getSut().captureFeedback(Feedback("message"), null, createScope())

    assertThat(id).isEqualTo(IchnoisCoreId.EMPTY_ID)
    verify(nextProcessor, never()).process(any<IchnoisCoreEvent>(), anyOrNull())
    verify(beforeSend, never()).execute(any(), anyOrNull())
    verify(fixture.transport, never()).send(any(), anyOrNull())
    assertClientReport(
      fixture.ichnoisOptions.clientReportRecorder,
      listOf(
        DiscardedEvent(DiscardReason.CALLBACK_ERROR.reason, DataCategory.Feedback.category, 1)
      ),
    )
    verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Feedback, 1)
  }

  // endregion

  private fun givenScopeWithStartedSession(
    errored: Boolean = false,
    crashed: Boolean = false,
  ): IScope {
    val scope = createScope(fixture.ichnoisOptions)
    scope.startSession()

    if (errored) {
      scope.withSession { it?.update(Session.State.Ok, "some-user-agent", true) }
    }

    if (crashed) {
      scope.withSession { it?.update(Session.State.Crashed, "some-user-agent", true) }
    }

    return scope
  }

  private fun thenNothingIsSent() {
    verify(fixture.transport, never()).send(anyOrNull(), anyOrNull())
  }

  private fun thenEnvelopeIsSentWith(
    eventCount: Int,
    sessionCount: Int,
    attachmentCount: Int = 0,
    transactionCount: Int = 0,
  ) {
    val argumentCaptor = argumentCaptor<IchnoisCoreEnvelope>()
    verify(fixture.transport, times(1)).send(argumentCaptor.capture(), anyOrNull())

    val envelope = argumentCaptor.firstValue
    val envelopeItemTypes = envelope.items.map { it.header.type }
    assertEquals(eventCount, envelopeItemTypes.count { it == IchnoisCoreItemType.Event })
    assertEquals(sessionCount, envelopeItemTypes.count { it == IchnoisCoreItemType.Session })
    assertEquals(attachmentCount, envelopeItemTypes.count { it == IchnoisCoreItemType.Attachment })
    assertEquals(transactionCount, envelopeItemTypes.count { it == IchnoisCoreItemType.Transaction })
  }

  private fun thenSessionIsStillOK(scope: IScope) {
    val sessionAfterCapture = scope.withSession {}!!
    assertEquals(0, sessionAfterCapture.errorCount())
    assertEquals(Session.State.Ok, sessionAfterCapture.status)
  }

  private fun thenSessionIsErrored(scope: IScope) {
    val sessionAfterCapture = scope.withSession {}!!
    assertTrue(sessionAfterCapture.errorCount() > 0)
    assertEquals(Session.State.Ok, sessionAfterCapture.status)
  }

  private fun thenSessionIsCrashed(scope: IScope) {
    val sessionAfterCapture = scope.withSession {}!!
    assertTrue(sessionAfterCapture.errorCount() > 0)
    assertEquals(Session.State.Crashed, sessionAfterCapture.status)
  }

  class CustomBeforeSendCallback : IchnoisCoreOptions.BeforeSendCallback {
    override fun execute(event: IchnoisCoreEvent, hint: Hint): IchnoisCoreEvent? {
      hint.screenshot = null
      hint.viewHierarchy = null
      hint.threadDump = null
      hint.tombstone = null
      return event
    }
  }

  private fun createReplayEvent(): IchnoisCoreReplayEvent =
    IchnoisCoreReplayEvent().apply {
      replayId = IchnoisCoreId("f715e1d64ef64ea3ad7744b5230813c3")
      segmentId = 0
      timestamp = DateUtils.getDateTimeWithMillisPrecision("987654321.123")
      replayStartTimestamp = DateUtils.getDateTimeWithMillisPrecision("987654321.123")
      urls = listOf("ScreenOne")
      errorIds = listOf("ab3a347a4cc14fd4b4cf1dc56b670c5b")
      traceIds = listOf("340cfef948204549ac07c3b353c81c50")
    }

  private fun createReplayRecording(): ReplayRecording =
    ReplayRecording().apply {
      segmentId = 0
      payload = emptyList()
    }

  private fun createScope(options: IchnoisCoreOptions = IchnoisCoreOptions()): IScope {
    return Scope(options).apply {
      addBreadcrumb(Breadcrumb().apply { message = "message" })
      setExtra("extra", "extra")
      setTag("tags", "tags")
      fingerprint.add("fp")
      level = IchnoisCoreLevel.FATAL
      user = User().apply { id = "id" }
      request = Request().apply { method = "post" }
    }
  }

  private fun createScopeWithAttachments(): IScope {
    return createScope().apply {
      addAttachment(fixture.attachment)
      addAttachment(fixture.attachment)

      val bytesTooBig = ByteArray((fixture.maxAttachmentSize + 1).toInt()) { 0 }
      addAttachment(
        Attachment(bytesTooBig, "will_get_dropped.txt", "application/octet-stream", true)
      )
    }
  }

  private fun createEvent(): IchnoisCoreEvent {
    return IchnoisCoreEvent().apply {
      addBreadcrumb(Breadcrumb().apply { message = "eventMessage" })
      setExtra("eventExtra", "eventExtra")
      setTag("eventTag", "eventTag")
      fingerprints = listOf("eventFp")
      transaction = "eventTransaction"
      level = IchnoisCoreLevel.DEBUG
      user = User().apply { id = "eventId" }
    }
  }

  private fun createSession(release: String = "rel"): Session {
    return Session("dis", User(), "env", release)
  }

  private val userFeedback: UserFeedback
    get() {
      val eventId = IchnoisCoreId("c2fb8fee2e2b49758bcb67cda0f713c7")
      val userFeedback = UserFeedback(eventId)
      userFeedback.apply {
        name = "John"
        email = "john@me.com"
        comments = "comment"
      }

      return userFeedback
    }

  private val checkIn = CheckIn("some_slug", CheckInStatus.OK)

  internal class CustomTransportGate : ITransportGate {
    override fun isConnected(): Boolean = false
  }

  private fun createNonHandledException(): List<IchnoisCoreException> {
    val exception = IchnoisCoreException().apply { mechanism = Mechanism().apply { isHandled = false } }
    return listOf(exception)
  }

  private fun createHandledException(): List<IchnoisCoreException> {
    return listOf(IchnoisCoreException())
  }

  private fun getEventFromData(data: ByteArray): IchnoisCoreEvent {
    val inputStream = InputStreamReader(ByteArrayInputStream(data))
    return fixture.ichnoisOptions.serializer.deserialize(inputStream, IchnoisCoreEvent::class.java)!!
  }

  private fun getTransactionFromData(data: ByteArray): IchnoisCoreTransaction {
    val inputStream = InputStreamReader(ByteArrayInputStream(data))
    return fixture.ichnoisOptions.serializer.deserialize(
      inputStream,
      IchnoisCoreTransaction::class.java,
    )!!
  }

  private fun getProfileChunkFromData(data: ByteArray): ProfileChunk {
    val inputStream = InputStreamReader(ByteArrayInputStream(data))
    return fixture.ichnoisOptions.serializer.deserialize(inputStream, ProfileChunk::class.java)!!
  }

  private fun getProfileChunkFromEnvelope(envelope: IchnoisCoreEnvelope): ProfileChunk {
    val profileChunkItem =
      envelope.items.first { item -> item.header.type == IchnoisCoreItemType.ProfileChunk }
    return getProfileChunkFromData(profileChunkItem.data)
  }

  private fun getReplayFromData(data: ByteArray): IchnoisCoreReplayEvent? {
    val unpacker = MessagePack.newDefaultUnpacker(data)
    val mapSize = unpacker.unpackMapHeader()
    for (i in 0 until mapSize) {
      val key = unpacker.unpackString()
      when (key) {
        IchnoisCoreItemType.ReplayEvent.itemType -> {
          val replayEventLength = unpacker.unpackBinaryHeader()
          val replayEventBytes = unpacker.readPayload(replayEventLength)
          return fixture.ichnoisOptions.serializer.deserialize(
            InputStreamReader(replayEventBytes.inputStream()),
            IchnoisCoreReplayEvent::class.java,
          )!!
        }
      }
    }
    return null
  }

  private fun verifyAttachmentsInEnvelope(eventId: IchnoisCoreId?) {
    verify(fixture.transport)
      .send(
        check { actual ->
          assertEquals(eventId, actual.header.eventId)

          assertEquals(fixture.ichnoisOptions.sdkVersion, actual.header.sdkVersion)

          assertEquals(4, actual.items.filter { it.header.type != IchnoisCoreItemType.Profile }.count())
          val attachmentItems =
            actual.items.filter { item -> item.header.type == IchnoisCoreItemType.Attachment }.toList()

          assertEquals(3, attachmentItems.size)

          val attachmentItem = attachmentItems.first()
          assertEquals(fixture.attachment.contentType, attachmentItem.header.contentType)
          assertEquals(fixture.attachment.filename, attachmentItem.header.fileName)
          assertEquals(fixture.attachment.bytes?.size, attachmentItem.header.length)

          val expectedBytes = fixture.attachment.bytes!!
          assertArrayEquals(expectedBytes, attachmentItem.data)

          val attachmentItemTooBig = attachmentItems.last()
          assertFailsWith<IchnoisCoreEnvelopeException>(
            "Getting data from attachment should" +
              "throw an exception, because the attachment is too big."
          ) {
            attachmentItemTooBig.data
          }
        },
        anyOrNull(),
      )
  }

  private fun verifyProfilingTraceInEnvelope(eventId: IchnoisCoreId?) {
    verify(fixture.transport)
      .send(
        check { actual ->
          assertEquals(eventId, actual.header.eventId)

          val profilingTraceItem =
            actual.items.firstOrNull { item -> item.header.type == IchnoisCoreItemType.Profile }
          assertNotNull(profilingTraceItem?.data)
        },
        anyOrNull(),
      )
  }

  private fun verifyProfileChunkInEnvelope(eventId: IchnoisCoreId?) {
    verify(fixture.transport)
      .send(
        check { actual ->
          assertEquals(eventId, actual.header.eventId)

          val profilingTraceItem =
            actual.items.firstOrNull { item -> item.header.type == IchnoisCoreItemType.ProfileChunk }
          assertNotNull(profilingTraceItem?.data)
        }
      )
  }

  private class AbnormalHint(private val mechanism: String? = null) : AbnormalExit {
    override fun mechanism(): String? = mechanism

    override fun ignoreCurrentThread(): Boolean = false

    override fun timestamp(): Long? = null
  }

  private class BackfillableHint : Backfillable {
    override fun shouldEnrich(): Boolean = false
  }

  private class CachedHint : Cached

  private class CachedWithApplyScopeHint : Cached, ApplyScopeData
}

class DropEverythingEventProcessor : EventProcessor {

  override fun process(event: IchnoisCoreEvent, hint: Hint): IchnoisCoreEvent? {
    return null
  }

  override fun process(transaction: IchnoisCoreTransaction, hint: Hint): IchnoisCoreTransaction? {
    return null
  }

  override fun process(event: IchnoisCoreReplayEvent, hint: Hint): IchnoisCoreReplayEvent? {
    return null
  }
}
