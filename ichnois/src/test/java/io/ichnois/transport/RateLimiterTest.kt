package io.ichnois.transport

import io.ichnois.Attachment
import io.ichnois.CheckIn
import io.ichnois.CheckInStatus
import io.ichnois.DataCategory
import io.ichnois.DataCategory.Replay
import io.ichnois.EnvelopeReader
import io.ichnois.Hint
import io.ichnois.ILogger
import io.ichnois.IScopes
import io.ichnois.IIchnoisCoreExecutorService
import io.ichnois.ISerializer
import io.ichnois.JsonSerializer
import io.ichnois.NoOpLogger
import io.ichnois.ProfileChunk
import io.ichnois.ProfilingTraceData
import io.ichnois.ReplayRecording
import io.ichnois.IchnoisCoreEnvelope
import io.ichnois.IchnoisCoreEnvelopeHeader
import io.ichnois.IchnoisCoreEnvelopeItem
import io.ichnois.IchnoisCoreEvent
import io.ichnois.IchnoisCoreLogEvent
import io.ichnois.IchnoisCoreLogEvents
import io.ichnois.IchnoisCoreLogLevel
import io.ichnois.IchnoisCoreLongDate
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreReplayEvent
import io.ichnois.IchnoisCoreTracer
import io.ichnois.Session
import io.ichnois.TransactionContext
import io.ichnois.UserFeedback
import io.ichnois.clientreport.DiscardReason
import io.ichnois.clientreport.IClientReportRecorder
import io.ichnois.hints.DiskFlushNotification
import io.ichnois.protocol.Feedback
import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.protocol.IchnoisCoreTransaction
import io.ichnois.protocol.User
import io.ichnois.test.DeferredExecutorService
import io.ichnois.test.getProperty
import io.ichnois.time.TestMonotonicTicker
import io.ichnois.util.HintUtils
import java.io.File
import java.util.UUID
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class RateLimiterTest {
  private class Fixture {
    val ticker = TestMonotonicTicker()
    val clientReportRecorder = mock<IClientReportRecorder>()
    val serializer = mock<ISerializer>()
    val executorService = DeferredExecutorService()

    private val config =
      object : RateLimiterConfig {
        override fun getLogger(): ILogger = NoOpLogger.getInstance()

        // qualified because an unqualified `clientReportRecorder` would resolve to this object's
        // own synthetic property for the getter being declared, and recurse
        override fun getClientReportRecorder(): IClientReportRecorder =
          this@Fixture.clientReportRecorder

        override fun getTimerExecutorService(): IIchnoisCoreExecutorService = executorService
      }

    fun getSUT(): RateLimiter = RateLimiter.create(ticker, config)
  }

  private val fixture = Fixture()

  @Test
  fun `uses X-IchnoisCore-Rate-Limit and allows sending if time has passed`() {
    val rateLimiter = fixture.getSUT()
    val eventItem = IchnoisCoreEnvelopeItem.fromEvent(fixture.serializer, IchnoisCoreEvent())
    val envelope = IchnoisCoreEnvelope(IchnoisCoreEnvelopeHeader(), arrayListOf(eventItem))

    rateLimiter.updateRetryAfterLimits(
      "50:transaction:key, 1:default;error;security:organization",
      null,
      1,
    )

    // the shortest limit in the header has now lapsed
    fixture.ticker.advance(1001, MILLISECONDS)

    val result = rateLimiter.filter(envelope, Hint())
    assertNotNull(result)
    assertEquals(1, result.items.count())
  }

  @Test
  fun `parse X-IchnoisCore-Rate-Limit and set its values and retry after should be true`() {
    val rateLimiter = fixture.getSUT()
    val scopes: IScopes = mock()
    whenever(scopes.options).thenReturn(IchnoisCoreOptions())
    val eventItem = IchnoisCoreEnvelopeItem.fromEvent(fixture.serializer, IchnoisCoreEvent())
    val transaction = IchnoisCoreTransaction(IchnoisCoreTracer(TransactionContext("name", "op"), scopes))
    val transactionItem = IchnoisCoreEnvelopeItem.fromEvent(fixture.serializer, transaction)
    val envelope = IchnoisCoreEnvelope(IchnoisCoreEnvelopeHeader(), arrayListOf(eventItem, transactionItem))

    rateLimiter.updateRetryAfterLimits(
      "50:transaction:key, 2700:default;error;security:organization",
      null,
      1,
    )

    val result = rateLimiter.filter(envelope, Hint())
    assertNull(result)
  }

  @Test
  fun `parse X-IchnoisCore-Rate-Limit and set its values and retry after should be false`() {
    val rateLimiter = fixture.getSUT()
    val scopes: IScopes = mock()
    whenever(scopes.options).thenReturn(IchnoisCoreOptions())
    val eventItem = IchnoisCoreEnvelopeItem.fromEvent(fixture.serializer, IchnoisCoreEvent())
    val transaction = IchnoisCoreTransaction(IchnoisCoreTracer(TransactionContext("name", "op"), scopes))
    val transactionItem = IchnoisCoreEnvelopeItem.fromEvent(fixture.serializer, transaction)
    val envelope = IchnoisCoreEnvelope(IchnoisCoreEnvelopeHeader(), arrayListOf(eventItem, transactionItem))

    rateLimiter.updateRetryAfterLimits(
      "1:transaction:key, 1:default;error;metric_bucket;security:organization",
      null,
      1,
    )

    // the shortest limit in the header has now lapsed
    fixture.ticker.advance(1001, MILLISECONDS)

    val result = rateLimiter.filter(envelope, Hint())
    assertNotNull(result)
    assertEquals(2, result.items.count())
  }

  @Test
  fun `When X-IchnoisCore-Rate-Limit categories are empty, applies to all the categories`() {
    val rateLimiter = fixture.getSUT()
    val eventItem = IchnoisCoreEnvelopeItem.fromEvent(fixture.serializer, IchnoisCoreEvent())
    val envelope = IchnoisCoreEnvelope(IchnoisCoreEnvelopeHeader(), arrayListOf(eventItem))

    rateLimiter.updateRetryAfterLimits("50::key", null, 1)

    val result = rateLimiter.filter(envelope, Hint())
    assertNull(result)
  }

  @Test
  fun `When all categories is set but expired, applies only for specific category`() {
    val rateLimiter = fixture.getSUT()
    val eventItem = IchnoisCoreEnvelopeItem.fromEvent(fixture.serializer, IchnoisCoreEvent())
    val envelope = IchnoisCoreEnvelope(IchnoisCoreEnvelopeHeader(), arrayListOf(eventItem))

    rateLimiter.updateRetryAfterLimits("1::key, 60:default;error;security:organization", null, 1)

    // the shortest limit in the header has now lapsed
    fixture.ticker.advance(1001, MILLISECONDS)

    val result = rateLimiter.filter(envelope, Hint())
    assertNull(result)
  }

  @Test
  fun `When category has shorter rate limiting, do not apply new timestamp`() {
    val rateLimiter = fixture.getSUT()
    val eventItem = IchnoisCoreEnvelopeItem.fromEvent(fixture.serializer, IchnoisCoreEvent())
    val envelope = IchnoisCoreEnvelope(IchnoisCoreEnvelopeHeader(), arrayListOf(eventItem))

    rateLimiter.updateRetryAfterLimits("60:error:key, 1:error:organization", null, 1)

    // the shortest limit in the header has now lapsed
    fixture.ticker.advance(1001, MILLISECONDS)

    val result = rateLimiter.filter(envelope, Hint())
    assertNull(result)
  }

  @Test
  fun `When category has longer rate limiting, apply new timestamp`() {
    val rateLimiter = fixture.getSUT()
    val eventItem = IchnoisCoreEnvelopeItem.fromEvent(fixture.serializer, IchnoisCoreEvent())
    val envelope = IchnoisCoreEnvelope(IchnoisCoreEnvelopeHeader(), arrayListOf(eventItem))

    rateLimiter.updateRetryAfterLimits("1:error:key, 5:error:organization", null, 1)

    // the shortest limit in the header has now lapsed
    fixture.ticker.advance(1001, MILLISECONDS)

    val result = rateLimiter.filter(envelope, Hint())
    assertNull(result)
  }

  @Test
  fun `When both retry headers are not present, default delay is set`() {
    val rateLimiter = fixture.getSUT()
    val eventItem = IchnoisCoreEnvelopeItem.fromEvent(fixture.serializer, IchnoisCoreEvent())
    val envelope = IchnoisCoreEnvelope(IchnoisCoreEnvelopeHeader(), arrayListOf(eventItem))

    rateLimiter.updateRetryAfterLimits(null, null, 429)

    // a second in, the 60s default delay is still running
    fixture.ticker.advance(1001, MILLISECONDS)

    val result = rateLimiter.filter(envelope, Hint())
    assertNull(result)
  }

  @Test
  fun `When X-IchnoisCore-Rate-Limit delay is negative, nothing is rate limited`() {
    val rateLimiter = fixture.getSUT()
    val eventItem = IchnoisCoreEnvelopeItem.fromEvent(fixture.serializer, IchnoisCoreEvent())
    val envelope = IchnoisCoreEnvelope(IchnoisCoreEnvelopeHeader(), arrayListOf(eventItem))

    rateLimiter.updateRetryAfterLimits("-1:error:key", null, 1)

    assertNotNull(rateLimiter.filter(envelope, Hint()))
  }

  @Test
  fun `When Retry-After is negative, nothing is rate limited`() {
    val rateLimiter = fixture.getSUT()
    val eventItem = IchnoisCoreEnvelopeItem.fromEvent(fixture.serializer, IchnoisCoreEvent())
    val envelope = IchnoisCoreEnvelope(IchnoisCoreEnvelopeHeader(), arrayListOf(eventItem))

    rateLimiter.updateRetryAfterLimits(null, "-1", 429)

    assertNotNull(rateLimiter.filter(envelope, Hint()))
  }

  @Test
  fun `A negative delay does not lift a standing rate limit`() {
    val rateLimiter = fixture.getSUT()
    val eventItem = IchnoisCoreEnvelopeItem.fromEvent(fixture.serializer, IchnoisCoreEvent())
    val envelope = IchnoisCoreEnvelope(IchnoisCoreEnvelopeHeader(), arrayListOf(eventItem))

    rateLimiter.updateRetryAfterLimits("60:error:key", null, 1)
    rateLimiter.updateRetryAfterLimits("-1:error:key", null, 1)

    assertNull(rateLimiter.filter(envelope, Hint()))
  }

  @Test
  fun `records dropped items as lost`() {
    val rateLimiter = fixture.getSUT()

    val eventItem = IchnoisCoreEnvelopeItem.fromEvent(fixture.serializer, IchnoisCoreEvent())
    val userFeedbackItem =
      IchnoisCoreEnvelopeItem.fromUserFeedback(
        fixture.serializer,
        UserFeedback(IchnoisCoreId(UUID.randomUUID())).also {
          it.comments = "It broke on Android. I don't know why, but this happens."
          it.email = "john@me.com"
          it.setName("John Me")
        },
      )
    val scopes = mock<IScopes>()
    whenever(scopes.options).thenReturn(IchnoisCoreOptions())
    val transaction = IchnoisCoreTracer(TransactionContext("name", "op"), scopes)
    val feedbackEvent = IchnoisCoreEvent().apply { contexts.setFeedback(Feedback("message")) }

    val sessionItem =
      IchnoisCoreEnvelopeItem.fromSession(fixture.serializer, Session("123", User(), "env", "release"))
    val attachmentItem =
      IchnoisCoreEnvelopeItem.fromAttachment(
        fixture.serializer,
        NoOpLogger.getInstance(),
        Attachment("{ \"number\": 10 }".toByteArray(), "log.json"),
        1000,
      )
    val profileItem =
      IchnoisCoreEnvelopeItem.fromProfilingTrace(
        ProfilingTraceData(File(""), transaction),
        1000,
        fixture.serializer,
      )
    val checkInItem =
      IchnoisCoreEnvelopeItem.fromCheckIn(
        fixture.serializer,
        CheckIn("monitor-slug-1", CheckInStatus.ERROR),
      )
    val profileChunkItem = IchnoisCoreEnvelopeItem.fromProfileChunk(ProfileChunk(), fixture.serializer)
    val feedbackEventItem = IchnoisCoreEnvelopeItem.fromEvent(fixture.serializer, feedbackEvent)

    val envelope =
      IchnoisCoreEnvelope(
        IchnoisCoreEnvelopeHeader(),
        arrayListOf(
          eventItem,
          userFeedbackItem,
          sessionItem,
          attachmentItem,
          profileItem,
          checkInItem,
          profileChunkItem,
          feedbackEventItem,
        ),
      )

    rateLimiter.updateRetryAfterLimits(null, null, 429)
    val result = rateLimiter.filter(envelope, Hint())

    assertNull(result)

    verify(fixture.clientReportRecorder, times(1))
      .recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(eventItem))
    verify(fixture.clientReportRecorder, times(1))
      .recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(sessionItem))
    verify(fixture.clientReportRecorder, times(1))
      .recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(userFeedbackItem))
    verify(fixture.clientReportRecorder, times(1))
      .recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(attachmentItem))
    verify(fixture.clientReportRecorder, times(1))
      .recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(profileItem))
    verify(fixture.clientReportRecorder, times(1))
      .recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(checkInItem))
    verify(fixture.clientReportRecorder, times(1))
      .recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(profileChunkItem))
    verify(fixture.clientReportRecorder, times(1))
      .recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(feedbackEventItem))
    verifyNoMoreInteractions(fixture.clientReportRecorder)
  }

  @Test
  fun `records only dropped items as lost`() {
    val rateLimiter = fixture.getSUT()
    val scopes = mock<IScopes>()
    whenever(scopes.options).thenReturn(IchnoisCoreOptions())

    val eventItem = IchnoisCoreEnvelopeItem.fromEvent(fixture.serializer, IchnoisCoreEvent())
    val userFeedbackItem =
      IchnoisCoreEnvelopeItem.fromUserFeedback(
        fixture.serializer,
        UserFeedback(IchnoisCoreId(UUID.randomUUID())).also {
          it.comments = "It broke on Android. I don't know why, but this happens."
          it.email = "john@me.com"
          it.setName("John Me")
        },
      )
    val transaction = IchnoisCoreTracer(TransactionContext("name", "op"), scopes)
    val profileItem =
      IchnoisCoreEnvelopeItem.fromProfilingTrace(
        ProfilingTraceData(File(""), transaction),
        1000,
        fixture.serializer,
      )
    val sessionItem =
      IchnoisCoreEnvelopeItem.fromSession(fixture.serializer, Session("123", User(), "env", "release"))
    val attachmentItem =
      IchnoisCoreEnvelopeItem.fromAttachment(
        fixture.serializer,
        NoOpLogger.getInstance(),
        Attachment("{ \"number\": 10 }".toByteArray(), "log.json"),
        1000,
      )

    val envelope =
      IchnoisCoreEnvelope(
        IchnoisCoreEnvelopeHeader(),
        arrayListOf(eventItem, userFeedbackItem, sessionItem, attachmentItem, profileItem),
      )

    rateLimiter.updateRetryAfterLimits("60:error:key, 1:error:organization", null, 1)
    val result = rateLimiter.filter(envelope, Hint())

    assertNotNull(result)
    assertEquals(4, result.items.toList().size)

    verify(fixture.clientReportRecorder, times(1))
      .recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(eventItem))
    verifyNoMoreInteractions(fixture.clientReportRecorder)
  }

  @Test
  fun `drop profile items as lost`() {
    val rateLimiter = fixture.getSUT()
    val scopes = mock<IScopes>()
    whenever(scopes.options).thenReturn(IchnoisCoreOptions())

    val eventItem = IchnoisCoreEnvelopeItem.fromEvent(fixture.serializer, IchnoisCoreEvent())
    val f = File.createTempFile("test", "trace")
    val transaction = IchnoisCoreTracer(TransactionContext("name", "op"), scopes)
    val profileItem =
      IchnoisCoreEnvelopeItem.fromProfilingTrace(
        ProfilingTraceData(f, transaction),
        1000,
        fixture.serializer,
      )
    val envelope = IchnoisCoreEnvelope(IchnoisCoreEnvelopeHeader(), arrayListOf(eventItem, profileItem))

    rateLimiter.updateRetryAfterLimits("60:profile:key, 1:profile:organization", null, 1)
    val result = rateLimiter.filter(envelope, Hint())

    assertNotNull(result)
    assertEquals(1, result.items.toList().size)

    verify(fixture.clientReportRecorder, times(1))
      .recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(profileItem))
    verifyNoMoreInteractions(fixture.clientReportRecorder)
  }

  @Test
  fun `a limit lapses exactly at its deadline, not a millisecond later`() {
    val rateLimiter = fixture.getSUT()

    rateLimiter.updateRetryAfterLimits("1:error:key", null, 1)

    fixture.ticker.advance(999, MILLISECONDS)
    assertTrue(rateLimiter.isActiveForCategory(DataCategory.Error))

    fixture.ticker.advance(1, MILLISECONDS)
    assertFalse(rateLimiter.isActiveForCategory(DataCategory.Error))
  }

  @Test
  fun `any limit can be checked`() {
    val rateLimiter = fixture.getSUT()
    val eventItem = IchnoisCoreEnvelopeItem.fromEvent(fixture.serializer, IchnoisCoreEvent())
    val envelope = IchnoisCoreEnvelope(IchnoisCoreEnvelopeHeader(), arrayListOf(eventItem))

    assertFalse(rateLimiter.isAnyRateLimitActive)

    rateLimiter.updateRetryAfterLimits(
      "50:transaction:key, 1:default;error;security:organization",
      null,
      1,
    )

    assertTrue(rateLimiter.isAnyRateLimitActive)
  }

  @Test
  fun `on rate limit DiskFlushNotification is marked as flushed`() {
    val rateLimiter = fixture.getSUT()
    val ichnoisEvent = IchnoisCoreEvent()
    val eventItem = IchnoisCoreEnvelopeItem.fromEvent(fixture.serializer, ichnoisEvent)
    val envelope = IchnoisCoreEnvelope(IchnoisCoreEnvelopeHeader(ichnoisEvent.eventId), arrayListOf(eventItem))

    rateLimiter.updateRetryAfterLimits(
      "50:transaction:key, 1:default;error;security:organization",
      null,
      1,
    )

    val hint = mock<DiskFlushNotification>()
    rateLimiter.filter(envelope, HintUtils.createWithTypeCheckHint(hint))

    verify(hint).markFlushed()
  }

  @Test
  fun `drop replay items as lost`() {
    val rateLimiter = fixture.getSUT()
    val scopes = mock<IScopes>()
    whenever(scopes.options).thenReturn(IchnoisCoreOptions())

    val replayItem =
      IchnoisCoreEnvelopeItem.fromReplay(
        fixture.serializer,
        mock<ILogger>(),
        IchnoisCoreReplayEvent(),
        ReplayRecording(),
        false,
      )
    val attachmentItem =
      IchnoisCoreEnvelopeItem.fromAttachment(
        fixture.serializer,
        NoOpLogger.getInstance(),
        Attachment("{ \"number\": 10 }".toByteArray(), "log.json"),
        1000,
      )
    val envelope = IchnoisCoreEnvelope(IchnoisCoreEnvelopeHeader(), arrayListOf(replayItem, attachmentItem))

    rateLimiter.updateRetryAfterLimits("60:replay:key", null, 1)
    val result = rateLimiter.filter(envelope, Hint())

    assertNotNull(result)
    assertEquals(1, result.items.toList().size)

    verify(fixture.clientReportRecorder, times(1))
      .recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(replayItem))
    verifyNoMoreInteractions(fixture.clientReportRecorder)
  }

  @Test
  fun `drop log items as lost`() {
    val rateLimiter = fixture.getSUT()
    val scopes = mock<IScopes>()
    whenever(scopes.options).thenReturn(IchnoisCoreOptions())

    val logEventItem =
      IchnoisCoreEnvelopeItem.fromLogs(
        fixture.serializer,
        IchnoisCoreLogEvents(
          listOf(IchnoisCoreLogEvent(IchnoisCoreId(), IchnoisCoreLongDate(0), "hello", IchnoisCoreLogLevel.INFO))
        ),
      )
    val envelope = IchnoisCoreEnvelope(IchnoisCoreEnvelopeHeader(null), arrayListOf(logEventItem))

    rateLimiter.updateRetryAfterLimits("60:log_item:key", null, 1)
    val result = rateLimiter.filter(envelope, Hint())

    assertNull(result)

    verify(fixture.clientReportRecorder, times(1))
      .recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(logEventItem))
    verifyNoMoreInteractions(fixture.clientReportRecorder)
  }

  @Test
  fun `drop log items as lost when log byte is rate limited`() {
    val rateLimiter = fixture.getSUT()
    val scopes = mock<IScopes>()
    whenever(scopes.options).thenReturn(IchnoisCoreOptions())

    val logEventItem =
      IchnoisCoreEnvelopeItem.fromLogs(
        fixture.serializer,
        IchnoisCoreLogEvents(
          listOf(IchnoisCoreLogEvent(IchnoisCoreId(), IchnoisCoreLongDate(0), "hello", IchnoisCoreLogLevel.INFO))
        ),
      )
    val envelope = IchnoisCoreEnvelope(IchnoisCoreEnvelopeHeader(null), arrayListOf(logEventItem))

    rateLimiter.updateRetryAfterLimits("60:log_byte:key", null, 1)
    val result = rateLimiter.filter(envelope, Hint())

    assertNull(result)

    verify(fixture.clientReportRecorder, times(1))
      .recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(logEventItem))
    verifyNoMoreInteractions(fixture.clientReportRecorder)
  }

  @Test
  fun `drop profileChunkUi items as lost`() {
    val rateLimiter = fixture.getSUT()

    val profileChunkItem = IchnoisCoreEnvelopeItem.fromProfileChunk(ProfileChunk(), fixture.serializer)
    val attachmentItem =
      IchnoisCoreEnvelopeItem.fromAttachment(
        fixture.serializer,
        NoOpLogger.getInstance(),
        Attachment("{ \"number\": 10 }".toByteArray(), "log.json"),
        1000,
      )
    val envelope =
      IchnoisCoreEnvelope(IchnoisCoreEnvelopeHeader(), arrayListOf(profileChunkItem, attachmentItem))

    rateLimiter.updateRetryAfterLimits("60:profile_chunk_ui:key", null, 1)
    val result = rateLimiter.filter(envelope, Hint())

    assertNotNull(result)
    assertEquals(1, result.items.toList().size)

    verify(fixture.clientReportRecorder, times(1))
      .recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(profileChunkItem))
    verifyNoMoreInteractions(fixture.clientReportRecorder)
  }

  @Test
  fun `drop profileChunk items as lost`() {
    val rateLimiter = fixture.getSUT()

    val profileChunkItem = IchnoisCoreEnvelopeItem.fromProfileChunk(ProfileChunk(), fixture.serializer)
    val attachmentItem =
      IchnoisCoreEnvelopeItem.fromAttachment(
        fixture.serializer,
        NoOpLogger.getInstance(),
        Attachment("{ \"number\": 10 }".toByteArray(), "log.json"),
        1000,
      )
    val envelope =
      IchnoisCoreEnvelope(IchnoisCoreEnvelopeHeader(), arrayListOf(profileChunkItem, attachmentItem))

    rateLimiter.updateRetryAfterLimits("60:profile_chunk:key", null, 1)
    val result = rateLimiter.filter(envelope, Hint())

    assertNotNull(result)
    assertEquals(1, result.items.toList().size)

    verify(fixture.clientReportRecorder, times(1))
      .recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(profileChunkItem))
    verifyNoMoreInteractions(fixture.clientReportRecorder)
  }

  @Test
  fun `drop feedback items as lost`() {
    val rateLimiter = fixture.getSUT()

    val feedbackEvent = IchnoisCoreEvent().apply { contexts.setFeedback(Feedback("message")) }
    val feedbackEventItem = IchnoisCoreEnvelopeItem.fromEvent(fixture.serializer, feedbackEvent)
    val attachmentItem =
      IchnoisCoreEnvelopeItem.fromAttachment(
        fixture.serializer,
        NoOpLogger.getInstance(),
        Attachment("{ \"number\": 10 }".toByteArray(), "log.json"),
        1000,
      )
    val envelope =
      IchnoisCoreEnvelope(IchnoisCoreEnvelopeHeader(), arrayListOf(feedbackEventItem, attachmentItem))

    rateLimiter.updateRetryAfterLimits("60:feedback:key", null, 1)
    val result = rateLimiter.filter(envelope, Hint())

    assertNotNull(result)
    assertEquals(1, result.items.toList().size)

    verify(fixture.clientReportRecorder, times(1))
      .recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(feedbackEventItem))
    verifyNoMoreInteractions(fixture.clientReportRecorder)
  }

  @Test
  fun `drop span items as lost`() {
    val rateLimiter = fixture.getSUT()

    // There is no span API yet so we'll create the envelope manually using EnvelopeReader
    // This mimics how hybrid SDKs would send span v2 envelope items
    val spanPayload = """{"items":[]}"""
    val spanItemHeader =
      """{"type":"span","length":${spanPayload.length},"content_type":"application/vnd.ichnois.items.span.v2+json","item_count":1}"""
    val envelopeHeader = """{}"""
    val rawEnvelope = "$envelopeHeader\n$spanItemHeader\n$spanPayload"

    val options = IchnoisCoreOptions()
    val envelopeReader = EnvelopeReader(JsonSerializer(options))
    val spanEnvelope = envelopeReader.read(rawEnvelope.byteInputStream())!!
    val spanItem = spanEnvelope.items.first()

    rateLimiter.updateRetryAfterLimits("60:span:key", null, 1)
    val result = rateLimiter.filter(spanEnvelope, Hint())

    assertNull(result)

    verify(fixture.clientReportRecorder, times(1))
      .recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(spanItem))
    verifyNoMoreInteractions(fixture.clientReportRecorder)
  }

  @Test
  fun `drop trace metric items as lost`() {
    val rateLimiter = fixture.getSUT()

    // There is no span API yet so we'll create the envelope manually using EnvelopeReader
    // This mimics how hybrid SDKs would send trace_metric envelope items
    val spanPayload = """{"items":[]}"""
    val metricItemHeader =
      """{"type":"trace_metric","length":${spanPayload.length},"content_type":"application/vnd.ichnois.items.trace-metric+json","item_count":1}"""
    val envelopeHeader = """{}"""
    val rawEnvelope = "$envelopeHeader\n$metricItemHeader\n$spanPayload"

    val options = IchnoisCoreOptions()
    val envelopeReader = EnvelopeReader(JsonSerializer(options))
    val metricEnvelope = envelopeReader.read(rawEnvelope.byteInputStream())!!
    val metricItem = metricEnvelope.items.first()

    rateLimiter.updateRetryAfterLimits("60:trace_metric:key", null, 1)
    val result = rateLimiter.filter(metricEnvelope, Hint())

    assertNull(result)

    verify(fixture.clientReportRecorder, times(1))
      .recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(metricItem))
    verifyNoMoreInteractions(fixture.clientReportRecorder)
  }

  @Test
  fun `drop trace metric items as lost when trace metric byte is rate limited`() {
    val rateLimiter = fixture.getSUT()

    // There is no span API yet so we'll create the envelope manually using EnvelopeReader
    // This mimics how hybrid SDKs would send trace_metric envelope items
    val spanPayload = """{"items":[]}"""
    val metricItemHeader =
      """{"type":"trace_metric","length":${spanPayload.length},"content_type":"application/vnd.ichnois.items.trace-metric+json","item_count":1}"""
    val envelopeHeader = """{}"""
    val rawEnvelope = "$envelopeHeader\n$metricItemHeader\n$spanPayload"

    val options = IchnoisCoreOptions()
    val envelopeReader = EnvelopeReader(JsonSerializer(options))
    val metricEnvelope = envelopeReader.read(rawEnvelope.byteInputStream())!!
    val metricItem = metricEnvelope.items.first()

    rateLimiter.updateRetryAfterLimits("60:trace_metric_byte:key", null, 1)
    val result = rateLimiter.filter(metricEnvelope, Hint())

    assertNull(result)

    verify(fixture.clientReportRecorder, times(1))
      .recordLostEnvelopeItem(eq(DiscardReason.RATELIMIT_BACKOFF), same(metricItem))
    verifyNoMoreInteractions(fixture.clientReportRecorder)
  }

  @Test
  fun `apply rate limits notifies observers`() {
    val rateLimiter = fixture.getSUT()

    var applied = false
    rateLimiter.addRateLimitObserver { applied = rateLimiter.isActiveForCategory(Replay) }
    rateLimiter.updateRetryAfterLimits("60:replay:key", null, 1)

    assertTrue(applied)
  }

  @Test
  fun `apply rate limits schedules a task to notify observers of lifted limits`() {
    val rateLimiter = fixture.getSUT()
    var applied = true
    rateLimiter.addRateLimitObserver { applied = rateLimiter.isActiveForCategory(Replay) }
    rateLimiter.updateRetryAfterLimits("1:replay:key", null, 1)

    // the notification was scheduled for when the limit lapses
    fixture.ticker.advance(2, SECONDS)
    fixture.executorService.runAll()

    assertFalse(applied)
  }

  @Test
  fun `close cancels pending notify tasks`() {
    val rateLimiter = fixture.getSUT()
    rateLimiter.updateRetryAfterLimits("60:replay:key", null, 1)

    val futures = rateLimiter.getProperty<List<Future<*>>>("notifyObserversFutures")
    assertEquals(1, futures.size)
    val future = futures.first()

    // When the rate limiter is closed
    rateLimiter.close()

    // Then the pending notify task is cancelled and dropped
    assertTrue(future.isCancelled)
    assertTrue(rateLimiter.getProperty<List<Future<*>>>("notifyObserversFutures").isEmpty())
  }
}
