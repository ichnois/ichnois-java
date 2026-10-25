package io.ichnois.transport.apache

import io.ichnois.Hint
import io.ichnois.ILogger
import io.ichnois.RequestDetails
import io.ichnois.IchnoisCoreEnvelope
import io.ichnois.IchnoisCoreEvent
import io.ichnois.IchnoisCoreLevel
import io.ichnois.IchnoisCoreNanotimeDate
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreOptionsManipulator
import io.ichnois.clientreport.NoOpClientReportRecorder
import io.ichnois.transport.RateLimiter
import io.ichnois.transport.ReusableCountLatch
import java.util.Date
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient
import org.apache.hc.core5.concurrent.FutureCallback
import org.apache.hc.core5.io.CloseMode
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ApacheHttpClientTransportTest {
  class Fixture {
    val options: IchnoisCoreOptions
    val logger = mock<ILogger>()
    val rateLimiter = mock<RateLimiter>()
    val clientReportRecorder = NoOpClientReportRecorder()
    val requestDetails =
      RequestDetails("http://key@localhost/proj", mapOf("header-name" to "header-value"))
    val client = mock<CloseableHttpAsyncClient>()
    val currentlyRunning = spy<ReusableCountLatch>()
    val executorService = Executors.newFixedThreadPool(2)

    init {
      whenever(rateLimiter.filter(any(), anyOrNull())).thenAnswer { it.arguments[0] }
      options = IchnoisCoreOptions()
      options.setSerializer(mock())
      options.setDiagnosticLevel(IchnoisCoreLevel.WARNING)
      options.setDebug(true)
      options.setLogger(logger)
      IchnoisCoreOptionsManipulator.setClientReportRecorder(options, clientReportRecorder)
    }

    fun getSut(
      response: SimpleHttpResponse? = null,
      queueFull: Boolean = false,
    ): ApacheHttpClientTransport {
      val transport =
        ApacheHttpClientTransport(options, requestDetails, client, rateLimiter, currentlyRunning)

      if (response != null) {
        whenever(client.execute(any(), any())).thenAnswer {
          (it.arguments[1] as FutureCallback<SimpleHttpResponse>).completed(response)
          CompletableFuture.completedFuture(response)
        }
      }

      if (queueFull) {
        whenever(currentlyRunning.count).thenReturn(options.maxQueueSize)
      }
      return transport
    }
  }

  private val fixture = Fixture()

  @AfterTest
  fun `shutdown executor`() {
    fixture.executorService.shutdownNow()
  }

  @Test
  fun `updates retry on rate limiter`() {
    val response = SimpleHttpResponse(200)
    response.addHeader("Retry-After", "10")
    response.addHeader("X-IchnoisCore-Rate-Limits", "1000")

    val sut = fixture.getSut(response = response)
    sut.send(IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null))

    verify(fixture.rateLimiter).updateRetryAfterLimits("1000", "10", 200)
  }

  @Test
  fun `uses common headers and url`() {
    val sut = fixture.getSut(response = SimpleHttpResponse(200))

    sut.send(IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null))

    verify(fixture.client)
      .execute(
        check {
          assertEquals("http://localhost/proj", it.uri.toString())
          assertEquals("header-value", it.getFirstHeader("header-name").value)
        },
        any(),
      )
  }

  @Test
  fun `does not submit when queue is full`() {
    val sut = fixture.getSut(queueFull = true)
    sut.send(IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null))
    verify(fixture.client, never()).execute(any(), any())
  }

  @Test
  fun `close waits for shutdown`() {
    val sut = fixture.getSut()
    sut.close()
    verify(fixture.client).awaitShutdown(check { assertNotEquals(0L, it.duration) })
  }

  @Test
  fun `close with isRestarting false waits for shutdown`() {
    val sut = fixture.getSut()
    sut.close(false)
    verify(fixture.client).awaitShutdown(check { assertNotEquals(0L, it.duration) })
  }

  @Test
  fun `close with isRestarting true does not wait for shutdown`() {
    val sut = fixture.getSut()
    sut.close(true)
    verify(fixture.client).awaitShutdown(check { assertEquals(0L, it.duration) })
  }

  @Test
  fun `close shuts down gracefully`() {
    val sut = fixture.getSut()
    sut.close()
    verify(fixture.client).close(CloseMode.GRACEFUL)
  }

  @Test
  fun `flush waits till all requests are finished`() {
    val sut = fixture.getSut()
    whenever(fixture.client.execute(any(), any())).then {
      fixture.executorService.submit {
        Thread.sleep(5)
        (it.arguments[1] as FutureCallback<SimpleHttpResponse>).completed(SimpleHttpResponse(200))
      }
    }
    sut.send(IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null))
    sut.send(IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null))
    sut.send(IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null))

    sut.flush(100)

    verify(fixture.currentlyRunning, times(3)).decrement()
  }

  @Test
  fun `keeps sending events after flush`() {
    val sut = fixture.getSut()
    whenever(fixture.client.execute(any(), any())).then {
      fixture.executorService.submit {
        Thread.sleep(5)
        (it.arguments[1] as FutureCallback<SimpleHttpResponse>).completed(SimpleHttpResponse(200))
      }
    }
    sut.send(IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null))
    sut.send(IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null))
    sut.flush(50)
    sut.send(IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null))
    sut.send(IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null))
    sut.flush(50)

    verify(fixture.currentlyRunning, times(4)).decrement()
  }

  @Test
  fun `logs warning when flush timeout was lower than time needed to execute all events`() {
    val sut = fixture.getSut()
    whenever(fixture.client.execute(any(), any()))
      .then {
        fixture.executorService.submit {
          Thread.sleep(1000)
          (it.arguments[1] as FutureCallback<SimpleHttpResponse>).completed(SimpleHttpResponse(200))
        }
      }
      .then {
        fixture.executorService.submit {
          Thread.sleep(20)
          (it.arguments[1] as FutureCallback<SimpleHttpResponse>).completed(SimpleHttpResponse(200))
        }
      }
    sut.send(IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null))
    sut.send(IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null))

    sut.flush(200)

    verify(fixture.logger).log(IchnoisCoreLevel.WARNING, "Failed to flush all events within %s ms", 200L)
    verify(fixture.currentlyRunning, times(1)).decrement()
  }

  @Test
  fun `sets current date to sent_at in envelope header`() {
    val now = Date(9001)
    val sut = fixture.getSut()
    fixture.options.dateProvider = mock()
    whenever(fixture.options.dateProvider.now()).thenReturn(IchnoisCoreNanotimeDate(now.time, 0))

    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null)
    sut.send(envelope)

    assertEquals(envelope.header.sentAt, now)
  }

  @Test
  fun `sets current date to sent_at in envelope header when sent with hint`() {
    val now = Date(9001)
    val sut = fixture.getSut()
    fixture.options.dateProvider = mock()
    whenever(fixture.options.dateProvider.now()).thenReturn(IchnoisCoreNanotimeDate(now.time, 0))

    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null)
    sut.send(envelope, Hint())

    assertEquals(envelope.header.sentAt, now)
  }
}
