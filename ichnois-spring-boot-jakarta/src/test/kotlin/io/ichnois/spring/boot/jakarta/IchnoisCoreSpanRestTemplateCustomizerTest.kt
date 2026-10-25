package io.ichnois.spring.boot.jakarta

import io.ichnois.BaggageHeader
import io.ichnois.Breadcrumb
import io.ichnois.IScopes
import io.ichnois.Scope
import io.ichnois.ScopeCallback
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreTraceHeader
import io.ichnois.IchnoisCoreTracer
import io.ichnois.SpanStatus
import io.ichnois.TracesSamplingDecision
import io.ichnois.TransactionContext
import io.ichnois.W3CTraceparentHeader
import io.ichnois.mockServerRequestTimeoutMillis
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertNull
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestTemplate

class IchnoisCoreSpanRestTemplateCustomizerTest {
  class Fixture {
    val ichnoisOptions = IchnoisCoreOptions()
    val scopes = mock<IScopes>()
    val restTemplate =
      RestTemplateBuilder()
        .setConnectTimeout(Duration.ofSeconds(2))
        .setReadTimeout(Duration.ofSeconds(2))
        .build()
    var mockServer = MockWebServer()
    val transaction: IchnoisCoreTracer
    internal val customizer = IchnoisCoreSpanRestTemplateCustomizer(scopes)
    val url = mockServer.url("/test/123").toString()
    val scope = Scope(ichnoisOptions)

    init {
      whenever(scopes.options).thenReturn(ichnoisOptions)
      doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }
        .whenever(scopes)
        .configureScope(any())
      transaction =
        IchnoisCoreTracer(TransactionContext("aTransaction", "op", TracesSamplingDecision(true)), scopes)
    }

    fun getSut(
      isTransactionActive: Boolean,
      status: HttpStatus = HttpStatus.OK,
      socketPolicy: SocketPolicy = SocketPolicy.KEEP_OPEN,
      includeMockServerInTracingOrigins: Boolean = true,
    ): RestTemplate {
      customizer.customize(restTemplate)

      if (includeMockServerInTracingOrigins) {
        ichnoisOptions.setTracePropagationTargets(listOf(mockServer.hostName))
      } else {
        ichnoisOptions.setTracePropagationTargets(listOf("other-api"))
      }

      ichnoisOptions.dsn = "https://key@ichnois-h2jw.onrender.com/proj"
      ichnoisOptions.isTraceSampling = true

      mockServer.enqueue(
        MockResponse().setBody("OK").setSocketPolicy(socketPolicy).setResponseCode(status.value())
      )

      if (isTransactionActive) {
        whenever(scopes.span).thenReturn(transaction)
      }

      return restTemplate
    }
  }

  private val fixture = Fixture()

  @Test
  fun `when transaction is active, creates span around RestTemplate HTTP call`() {
    val result =
      fixture.getSut(isTransactionActive = true).getForObject(fixture.url, String::class.java)

    assertThat(result).isEqualTo("OK")
    assertThat(fixture.transaction.spans).hasSize(1)
    val span = fixture.transaction.spans.first()
    assertThat(span.operation).isEqualTo("http.client")
    assertThat(span.description).isEqualTo("GET ${fixture.url}")
    assertThat(span.status).isEqualTo(SpanStatus.OK)

    val recordedRequest =
      fixture.mockServer.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertThat(recordedRequest.headers["ichnois-trace"]!!)
      .startsWith(fixture.transaction.spanContext.traceId.toString())
      .endsWith("-1")
      .doesNotContain(fixture.transaction.spanContext.spanId.toString())
    assertThat(recordedRequest.headers["baggage"]!!)
      .contains(fixture.transaction.spanContext.traceId.toString())
  }

  @Test
  fun `when there is an active span, existing baggage headers are merged with ichnois baggage into single header`() {
    val sut = fixture.getSut(isTransactionActive = true)
    val headers = HttpHeaders()
    headers.add("baggage", "thirdPartyBaggage=someValue")
    headers.add(
      "baggage",
      "secondThirdPartyBaggage=secondValue; property;propertyKey=propertyValue,anotherThirdPartyBaggage=anotherValue",
    )

    val requestEntity = HttpEntity<Unit>(headers)

    sut.exchange(fixture.url, HttpMethod.GET, requestEntity, String::class.java)

    val recorderRequest =
      fixture.mockServer.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
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
    assertTrue(baggageHeaderValues[0].contains("ichnois-transaction=aTransaction"))
    assertTrue(baggageHeaderValues[0].contains("ichnois-trace_id"))
  }

  @Test
  fun `when transaction is active and server is not listed in tracing origins, does not add ichnois trace header to the request`() {
    fixture
      .getSut(isTransactionActive = true, includeMockServerInTracingOrigins = false)
      .getForObject(fixture.url, String::class.java)
    val recordedRequest =
      fixture.mockServer.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertThat(recordedRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER]).isNull()
  }

  @Test
  fun `when transaction is active and server is listed in tracing origins, adds ichnois trace header to the request`() {
    fixture
      .getSut(isTransactionActive = true, includeMockServerInTracingOrigins = true)
      .getForObject(fixture.url, String::class.java)
    val recordedRequest =
      fixture.mockServer.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertThat(recordedRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER]).isNotNull()
  }

  @Test
  fun `when transaction is active and response code is not 2xx, creates span with error status around RestTemplate HTTP call`() {
    try {
      fixture
        .getSut(isTransactionActive = true, status = HttpStatus.INTERNAL_SERVER_ERROR)
        .getForObject(fixture.url, String::class.java)
    } catch (e: Throwable) {}
    assertThat(fixture.transaction.spans).hasSize(1)
    val span = fixture.transaction.spans.first()
    assertThat(span.operation).isEqualTo("http.client")
    assertThat(span.description).isEqualTo("GET ${fixture.url}")
    assertThat(span.status).isEqualTo(SpanStatus.INTERNAL_ERROR)
  }

  @Test
  fun `when transaction is active and throws IO exception, creates span with error status around RestTemplate HTTP call`() {
    try {
      fixture
        .getSut(isTransactionActive = true, socketPolicy = SocketPolicy.DISCONNECT_AT_START)
        .getForObject(fixture.url, String::class.java)
    } catch (e: Throwable) {}
    assertThat(fixture.transaction.spans).hasSize(1)
    val span = fixture.transaction.spans.first()
    assertThat(span.operation).isEqualTo("http.client")
    assertThat(span.description).isEqualTo("GET ${fixture.url}")
    assertThat(span.status).isEqualTo(SpanStatus.INTERNAL_ERROR)
  }

  @Test
  fun `when transaction is not active, does not create span around RestTemplate HTTP call`() {
    val result =
      fixture.getSut(isTransactionActive = false).getForObject(fixture.url, String::class.java)

    assertThat(result).isEqualTo("OK")
    assertThat(fixture.transaction.spans).isEmpty()
  }

  @Test
  fun `when transaction is not active, adds tracing headers from scope`() {
    val sut = fixture.getSut(isTransactionActive = false)
    val headers = HttpHeaders()
    headers.add("baggage", "thirdPartyBaggage=someValue")
    headers.add(
      "baggage",
      "secondThirdPartyBaggage=secondValue; property;propertyKey=propertyValue,anotherThirdPartyBaggage=anotherValue",
    )

    val requestEntity = HttpEntity<Unit>(headers)

    sut.exchange(fixture.url, HttpMethod.GET, requestEntity, String::class.java)

    val recorderRequest =
      fixture.mockServer.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
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
    assertTrue(baggageHeaderValues[0].contains("ichnois-trace_id"))
  }

  @Test
  fun `does not add ichnois-trace header when span origin is ignored`() {
    fixture.ichnoisOptions.setIgnoredSpanOrigins(listOf("auto.http.spring_jakarta.resttemplate"))
    val sut = fixture.getSut(isTransactionActive = false)
    val headers = HttpHeaders()
    val requestEntity = HttpEntity<Unit>(headers)

    sut.exchange(fixture.url, HttpMethod.GET, requestEntity, String::class.java)

    val recorderRequest =
      fixture.mockServer.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNull(recorderRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `avoids duplicate registration`() {
    val restTemplate = fixture.getSut(isTransactionActive = true)

    fixture.customizer.customize(restTemplate)
    assertThat(restTemplate.interceptors).hasSize(1)
    fixture.customizer.customize(restTemplate)
    assertThat(restTemplate.interceptors).hasSize(1)
  }

  @Test
  fun `when transaction is active adds breadcrumb when http calls succeeds`() {
    fixture
      .getSut(isTransactionActive = true)
      .postForObject(fixture.url, "content", String::class.java)
    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("http", it.type)
          assertEquals(fixture.url, it.data["url"])
          assertEquals("POST", it.data["method"])
          assertEquals(7, it.data["request_body_size"])
        },
        anyOrNull(),
      )
  }

  @SuppressWarnings("SwallowedException")
  @Test
  fun `when transaction is active adds breadcrumb when http calls results in exception`() {
    try {
      fixture
        .getSut(isTransactionActive = true, status = HttpStatus.INTERNAL_SERVER_ERROR)
        .getForObject(fixture.url, String::class.java)
    } catch (e: Throwable) {}
    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("http", it.type)
          assertEquals(fixture.url, it.data["url"])
          assertEquals("GET", it.data["method"])
        },
        anyOrNull(),
      )
  }

  @Test
  fun `when transaction is not active adds breadcrumb when http calls succeeds`() {
    fixture
      .getSut(isTransactionActive = false)
      .postForObject(fixture.url, "content", String::class.java)
    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("http", it.type)
          assertEquals(fixture.url, it.data["url"])
          assertEquals("POST", it.data["method"])
          assertEquals(7, it.data["request_body_size"])
        },
        anyOrNull(),
      )
  }

  @SuppressWarnings("SwallowedException")
  @Test
  fun `when transaction is not active adds breadcrumb when http calls results in exception`() {
    try {
      fixture
        .getSut(isTransactionActive = false, status = HttpStatus.INTERNAL_SERVER_ERROR)
        .getForObject(fixture.url, String::class.java)
    } catch (e: Throwable) {}
    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("http", it.type)
          assertEquals(fixture.url, it.data["url"])
          assertEquals("GET", it.data["method"])
        },
        anyOrNull(),
      )
  }

  @Test
  fun `adds W3C traceparent header when propagateTraceparent is enabled`() {
    fixture.ichnoisOptions.isPropagateTraceparent = true
    fixture
      .getSut(isTransactionActive = true, includeMockServerInTracingOrigins = true)
      .getForObject(fixture.url, String::class.java)
    val recordedRequest =
      fixture.mockServer.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recordedRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNotNull(recordedRequest.headers[W3CTraceparentHeader.TRACEPARENT_HEADER])
  }

  @Test
  fun `does not add W3C traceparent header when propagateTraceparent is disabled`() {
    fixture.ichnoisOptions.isPropagateTraceparent = false
    fixture
      .getSut(isTransactionActive = true, includeMockServerInTracingOrigins = true)
      .getForObject(fixture.url, String::class.java)
    val recordedRequest =
      fixture.mockServer.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recordedRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNull(recordedRequest.headers[W3CTraceparentHeader.TRACEPARENT_HEADER])
  }
}
