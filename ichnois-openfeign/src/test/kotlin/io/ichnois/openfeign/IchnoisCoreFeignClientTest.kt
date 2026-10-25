package io.ichnois.openfeign

import com.google.common.truth.Truth.assertThat
import feign.Client
import feign.Feign
import feign.FeignException
import feign.HeaderMap
import feign.Request
import feign.RequestLine
import io.ichnois.BaggageHeader
import io.ichnois.Breadcrumb
import io.ichnois.DataCategory
import io.ichnois.ILogger
import io.ichnois.IScopes
import io.ichnois.Scope
import io.ichnois.ScopeCallback
import io.ichnois.IchnoisCoreLevel
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreTraceHeader
import io.ichnois.IchnoisCoreTracer
import io.ichnois.SpanDataConvention
import io.ichnois.SpanStatus
import io.ichnois.TransactionContext
import io.ichnois.W3CTraceparentHeader
import io.ichnois.clientreport.DiscardReason
import io.ichnois.mockServerRequestTimeoutMillis
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class IchnoisCoreFeignClientTest {
  class Fixture {
    val scopes = mock<IScopes>()
    val server = MockWebServer()
    val ichnoisTracer: IchnoisCoreTracer
    val ichnoisOptions = IchnoisCoreOptions().apply { dsn = "http://key@localhost/proj" }
    val scope = Scope(ichnoisOptions)

    init {
      whenever(scopes.options).thenReturn(ichnoisOptions)
      doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }
        .whenever(scopes)
        .configureScope(any())
      ichnoisTracer = IchnoisCoreTracer(TransactionContext("name", "op"), scopes)
    }

    fun getSut(
      isSpanActive: Boolean = true,
      httpStatusCode: Int = 201,
      responseBody: String = "success",
      networkError: Boolean = false,
      beforeSpan: IchnoisCoreFeignClient.BeforeSpanCallback? = null,
    ): MockApi {
      if (isSpanActive) {
        whenever(scopes.span).thenReturn(ichnoisTracer)
      }
      server.enqueue(MockResponse().setBody(responseBody).setResponseCode(httpStatusCode))
      server.start()

      return if (!networkError) {
          Feign.builder().addCapability(IchnoisCoreCapability(scopes, beforeSpan))
        } else {
          val mockClient = mock<Client>()
          whenever(mockClient.execute(any(), any())).thenThrow(RuntimeException::class.java)
          Feign.builder().client(IchnoisCoreFeignClient(mockClient, scopes, beforeSpan))
        }
        .target(MockApi::class.java, server.url("/").toUrl().toString())
    }
  }

  private lateinit var fixture: Fixture

  @BeforeTest
  fun setup() {
    fixture = Fixture()
  }

  @Test
  fun `when there is an active span, adds ichnois trace header to the request`() {
    fixture.ichnoisOptions.isTraceSampling = true
    fixture.ichnoisOptions.dsn = "https://key@ichnois-h2jw.onrender.com/proj"
    val sut = fixture.getSut()
    sut.getOk()
    val recorderRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recorderRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNotNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is an active span, existing baggage headers are merged with ichnois baggage into single header`() {
    fixture.ichnoisOptions.isTraceSampling = true
    fixture.ichnoisOptions.dsn = "https://key@ichnois-h2jw.onrender.com/proj"
    val sut = fixture.getSut()

    sut.getOkWithBaggageHeader(
      mapOf(
        "baggage" to
          listOf(
            "thirdPartyBaggage=someValue",
            "secondThirdPartyBaggage=secondValue; property;propertyKey=propertyValue,anotherThirdPartyBaggage=anotherValue",
          )
      )
    )

    val recorderRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recorderRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNotNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])

    val baggageHeaderValues = recorderRequest.headers.values(BaggageHeader.BAGGAGE_HEADER)
    assertEquals(baggageHeaderValues.size, 1)
    assertTrue(
      baggageHeaderValues[0].startsWith(
        "thirdPartyBaggage=someValue,secondThirdPartyBaggage=secondValue; property;propertyKey=propertyValue,anotherThirdPartyBaggage=anotherValue"
      )
    )
    assertTrue(baggageHeaderValues[0].contains("ichnois-public_key=key"))
    assertTrue(baggageHeaderValues[0].contains("ichnois-transaction=name"))
    assertTrue(baggageHeaderValues[0].contains("ichnois-trace_id"))
  }

  @Test
  fun `when there is no active span, adds ichnois trace header to the request from scope`() {
    fixture.ichnoisOptions.dsn = "https://key@ichnois-h2jw.onrender.com/proj"
    val sut = fixture.getSut(isSpanActive = false)
    sut.getOk()
    val recorderRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recorderRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNotNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `does not add ichnois trace header when span origin is ignored`() {
    fixture.ichnoisOptions.dsn = "https://key@ichnois-h2jw.onrender.com/proj"
    fixture.ichnoisOptions.setIgnoredSpanOrigins(listOf("auto.http.openfeign"))
    val sut = fixture.getSut(isSpanActive = false)
    sut.getOk()
    val recorderRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNull(recorderRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is no active span, does not add ichnois trace header to the request if host is disallowed`() {
    fixture.ichnoisOptions.setTracePropagationTargets(listOf("some-host-that-does-not-exist"))
    fixture.ichnoisOptions.dsn = "https://key@ichnois-h2jw.onrender.com/proj"
    val sut = fixture.getSut(isSpanActive = false)
    sut.getOk()
    val recorderRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNull(recorderRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when request url not in tracing origins, does not add ichnois trace header to the request`() {
    fixture.ichnoisOptions.setTracePropagationTargets(listOf("http://some-other-url.ichnois.io"))
    fixture.ichnoisOptions.isTraceSampling = true
    fixture.ichnoisOptions.dsn = "https://key@ichnois-h2jw.onrender.com/proj"
    val sut = fixture.getSut()
    sut.getOk()
    val recorderRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNull(recorderRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `does not overwrite response body`() {
    val sut = fixture.getSut()
    val response = sut.getOk()
    assertEquals("success", response)
  }

  @Test
  fun `creates a span around the request`() {
    val sut = fixture.getSut()
    sut.getOk()
    assertEquals(1, fixture.ichnoisTracer.children.size)
    val httpClientSpan = fixture.ichnoisTracer.children.first()
    assertEquals("http.client", httpClientSpan.operation)
    assertEquals("GET ${fixture.server.url("/status/200")}", httpClientSpan.description)
    assertEquals(201, httpClientSpan.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
    assertEquals("GET", httpClientSpan.data[SpanDataConvention.HTTP_METHOD_KEY])
    assertEquals(SpanStatus.OK, httpClientSpan.status)
    assertEquals("auto.http.openfeign", httpClientSpan.spanContext.origin)
    assertTrue(httpClientSpan.isFinished)
  }

  @Test
  fun `maps http status code to SpanStatus`() {
    val sut = fixture.getSut(httpStatusCode = 400)
    try {
      sut.getOk()
    } catch (e: FeignException) {
      val httpClientSpan = fixture.ichnoisTracer.children.first()
      assertEquals(400, httpClientSpan.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
      assertEquals(SpanStatus.INVALID_ARGUMENT, httpClientSpan.status)
    }
  }

  @Test
  fun `does not map unmapped http status code to SpanStatus`() {
    val sut = fixture.getSut(httpStatusCode = 502)
    try {
      sut.getOk()
    } catch (e: FeignException) {
      val httpClientSpan = fixture.ichnoisTracer.children.first()
      assertEquals(502, httpClientSpan.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
      assertNull(httpClientSpan.status)
    }
  }

  @Test
  fun `adds breadcrumb when http calls succeeds`() {
    val sut = fixture.getSut(responseBody = "response body")
    sut.postWithBody("request-body")
    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("http", it.type)
          assertEquals(13, it.data["response_body_size"])
          assertEquals(12, it.data["request_body_size"])
        },
        anyOrNull(),
      )
  }

  @Test
  fun `adds breadcrumb when http calls succeeds even though response body is null`() {
    val sut = fixture.getSut(responseBody = "")
    sut.postWithBody("request-body")
    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("http", it.type)
          assertEquals(0, it.data["response_body_size"])
          assertEquals(12, it.data["request_body_size"])
        },
        anyOrNull(),
      )
  }

  @SuppressWarnings("SwallowedException")
  @Test
  fun `adds breadcrumb when http calls results in exception`() {
    val sut = fixture.getSut(networkError = true)

    try {
      sut.getOk()
      fail()
    } catch (e: Exception) {
      // ignore me
    }
    verify(fixture.scopes)
      .addBreadcrumb(check<Breadcrumb> { assertEquals("http", it.type) }, anyOrNull())
  }

  @SuppressWarnings("SwallowedException")
  @Test
  fun `sets status and throwable when call results in in exception`() {
    val sut = fixture.getSut(networkError = true)
    try {
      sut.getOk()
      fail()
    } catch (e: Exception) {
      // ignore
    }
    val httpClientSpan = fixture.ichnoisTracer.children.first()
    assertNull(httpClientSpan.data[SpanDataConvention.HTTP_STATUS_CODE_KEY])
    assertEquals(SpanStatus.INTERNAL_ERROR, httpClientSpan.status)
    assertTrue(httpClientSpan.throwable is Exception)
  }

  @Test
  fun `reports callback errors only for sampled spans`() {
    for (sampled in listOf(true, false, null)) {
      val fixture = Fixture()
      val onDiscard = mock<IchnoisCoreOptions.OnDiscardCallback>()
      val sut =
        fixture.getSut(
          beforeSpan = { span, _, _ ->
            span.spanContext.sampled = false
            throw IllegalStateException("callback failed")
          }
        )
      fixture.ichnoisTracer.spanContext.sampled = sampled
      fixture.ichnoisOptions.onDiscard = onDiscard

      assertThat(sut.getOk()).isEqualTo("success")
      fixture.ichnoisTracer.finish()

      if (sampled == true) {
        verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Span, 1)
      }
      verifyNoMoreInteractions(onDiscard)
    }
  }

  @Test
  fun `when beforeSpan throws a nonfatal throwable, drops span and preserves response`() {
    val failure = AssertionError("callback failed")
    val logger = mock<ILogger>()
    fixture.ichnoisOptions.isDebug = true
    fixture.ichnoisOptions.setLogger(logger)
    val sut =
      fixture.getSut(
        beforeSpan = { span, _, _ ->
          span.description = "partially modified"
          throw failure
        }
      )

    assertThat(sut.getOk()).isEqualTo("success")
    val span = fixture.ichnoisTracer.children.single()
    assertThat(span.isSampled).isFalse()
    assertThat(span.isFinished).isTrue()
    verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    verify(logger)
      .log(
        IchnoisCoreLevel.ERROR,
        "The beforeSpan callback threw an exception in IchnoisCoreFeignClient. Dropping span.",
        failure,
      )
  }

  @Test
  fun `fatal error from beforeSpan is not swallowed`() {
    val failure = OutOfMemoryError("callback failed")
    val sut = fixture.getSut(beforeSpan = { _, _, _ -> throw failure })

    val thrown = assertFailsWith<OutOfMemoryError> { sut.getOk() }

    assertThat(thrown).isSameInstanceAs(failure)
  }

  @Test
  fun `when beforeSpan throws, preserves original request exception`() {
    val requestFailure = IOException("request failed")
    val delegate = mock<Client>()
    whenever(delegate.execute(any(), any())).thenThrow(requestFailure)
    whenever(fixture.scopes.span).thenReturn(fixture.ichnoisTracer)
    val sut =
      IchnoisCoreFeignClient(delegate, fixture.scopes) { _, _, _ ->
        throw IllegalStateException("callback failed")
      }
    val request =
      Request.create(
        Request.HttpMethod.GET,
        "https://example.com",
        emptyMap<String, Collection<String>>(),
        null as ByteArray?,
        null,
      )

    val thrown = assertFailsWith<IOException> { sut.execute(request, Request.Options()) }
    assertThat(thrown).isSameInstanceAs(requestFailure)
    val span = fixture.ichnoisTracer.children.single()
    assertThat(span.throwable).isSameInstanceAs(requestFailure)
    assertThat(span.isSampled).isFalse()
    assertThat(span.isFinished).isTrue()
    verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
  }

  @Test
  fun `customizer modifies span`() {
    val sut = fixture.getSut { span, _, _ ->
      span.description = "overwritten description"
      span
    }
    sut.getOk()
    assertEquals(1, fixture.ichnoisTracer.children.size)
    val httpClientSpan = fixture.ichnoisTracer.children.first()
    assertEquals("overwritten description", httpClientSpan.description)
  }

  @Test
  fun `customizer receives request and response`() {
    val sut = fixture.getSut { span, request, response ->
      assertEquals(request.url(), request.url())
      assertEquals(request.httpMethod().name, request.httpMethod().name)
      assertNotNull(response) { assertEquals(201, it.status()) }
      span
    }
    sut.getOk()
  }

  @Test
  fun `customizer can drop the span`() {
    val sut = fixture.getSut { _, _, _ -> null }
    val onDiscard = mock<IchnoisCoreOptions.OnDiscardCallback>()
    fixture.ichnoisOptions.onDiscard = onDiscard
    fixture.ichnoisTracer.spanContext.sampled = true
    sut.getOk()
    verifyNoMoreInteractions(onDiscard)
    val httpClientSpan = fixture.ichnoisTracer.children.first()
    assertNotNull(httpClientSpan.spanContext.sampled) { assertFalse(it) }
  }

  @Test
  fun `adds W3C traceparent header when propagateTraceparent is enabled`() {
    fixture.ichnoisOptions.isTraceSampling = true
    fixture.ichnoisOptions.isPropagateTraceparent = true
    fixture.ichnoisOptions.dsn = "https://key@ichnois-h2jw.onrender.com/proj"
    val sut = fixture.getSut()
    sut.getOk()
    val recorderRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recorderRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNotNull(recorderRequest.headers[W3CTraceparentHeader.TRACEPARENT_HEADER])
  }

  @Test
  fun `does not add W3C traceparent header when propagateTraceparent is disabled`() {
    fixture.ichnoisOptions.isTraceSampling = true
    fixture.ichnoisOptions.isPropagateTraceparent = false
    fixture.ichnoisOptions.dsn = "https://key@ichnois-h2jw.onrender.com/proj"
    val sut = fixture.getSut()
    sut.getOk()
    val recorderRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recorderRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNull(recorderRequest.headers[W3CTraceparentHeader.TRACEPARENT_HEADER])
  }

  interface MockApi {
    @RequestLine("GET /status/200") fun getOk(): String

    @RequestLine("GET /status/200")
    fun getOkWithBaggageHeader(@HeaderMap headers: Map<String, Any>): String

    @RequestLine("POST /post-with-body") fun postWithBody(body: String)
  }
}
