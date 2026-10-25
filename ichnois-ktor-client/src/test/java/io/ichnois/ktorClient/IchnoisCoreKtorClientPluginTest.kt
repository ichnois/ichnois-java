package io.ichnois.ktorClient

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.java.Java
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ichnois.BaggageHeader
import io.ichnois.Breadcrumb
import io.ichnois.DataCategory
import io.ichnois.Hint
import io.ichnois.HttpStatusCodeRange
import io.ichnois.ILogger
import io.ichnois.IScope
import io.ichnois.IScopes
import io.ichnois.KeyValueCollectionBehavior
import io.ichnois.Scope
import io.ichnois.ScopeCallback
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreEvent
import io.ichnois.IchnoisCoreLevel
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreTraceHeader
import io.ichnois.IchnoisCoreTracer
import io.ichnois.SpanDataConvention
import io.ichnois.SpanStatus
import io.ichnois.TransactionContext
import io.ichnois.W3CTraceparentHeader
import io.ichnois.clientreport.DiscardReason
import io.ichnois.exception.IchnoisCoreHttpClientException
import io.ichnois.mockServerRequestTimeoutMillis
import java.util.concurrent.TimeUnit
import kotlin.Unit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class IchnoisCoreKtorClientPluginTest {
  class Fixture {
    val scopes = mock<IScopes>()
    val server = MockWebServer()
    lateinit var ichnoisTracer: IchnoisCoreTracer
    lateinit var options: IchnoisCoreOptions
    lateinit var scope: IScope

    @SuppressWarnings("LongParameterList")
    fun getSut(
      isSpanActive: Boolean = true,
      httpStatusCode: Int = 201,
      responseBody: String = "success",
      socketPolicy: SocketPolicy = SocketPolicy.KEEP_OPEN,
      beforeSpan: IchnoisCoreKtorClientPluginConfig.BeforeSpanCallback? = null,
      includeMockServerInTracePropagationTargets: Boolean = true,
      keepDefaultTracePropagationTargets: Boolean = false,
      captureFailedRequests: Boolean = false,
      failedRequestTargets: List<String> = listOf(".*"),
      failedRequestStatusCodes: List<HttpStatusCodeRange> =
        listOf(
          HttpStatusCodeRange(HttpStatusCodeRange.DEFAULT_MIN, HttpStatusCodeRange.DEFAULT_MAX)
        ),
      sendDefaultPii: Boolean = false,
      optionsConfiguration: IchnoisCore.OptionsConfiguration<IchnoisCoreOptions>? = null,
      httpClientEngine: HttpClientEngine = Java.create(),
    ): HttpClient {
      options =
        IchnoisCoreOptions().also {
          optionsConfiguration?.configure(it)
          it.dsn = "https://key@ichnois-h2jw.onrender.com/proj"
          if (includeMockServerInTracePropagationTargets) {
            it.setTracePropagationTargets(listOf(server.hostName))
          } else if (!keepDefaultTracePropagationTargets) {
            it.setTracePropagationTargets(listOf("other-api"))
          }
          it.isSendDefaultPii = sendDefaultPii
        }
      scope = Scope(options)
      whenever(scopes.options).thenReturn(options)
      doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }
        .whenever(scopes)
        .configureScope(any())

      ichnoisTracer = IchnoisCoreTracer(TransactionContext("name", "op"), scopes)

      if (isSpanActive) {
        whenever(scopes.span).thenReturn(ichnoisTracer)
      }

      whenever(scopes.forkedCurrentScope(any())).thenReturn(scopes)

      server.enqueue(
        MockResponse()
          .setBody(responseBody)
          .addHeader("myResponseHeader", "myValue")
          .addHeader("Set-Cookie", "theme=dark; Path=/")
          .setSocketPolicy(socketPolicy)
          .setResponseCode(httpStatusCode)
      )

      return HttpClient(httpClientEngine) {
        install(IchnoisCoreKtorClientPlugin) {
          this.scopes = this@Fixture.scopes
          this.beforeSpan = beforeSpan
          this.captureFailedRequests = captureFailedRequests
          this.failedRequestTargets = failedRequestTargets
          this.failedRequestStatusCodes = failedRequestStatusCodes
          this.forceScopes = true
        }
      }
    }
  }

  private val fixture = Fixture()

  @Test
  fun `adds breadcrumb when http call succeeds`(): Unit = runBlocking {
    val sut = fixture.getSut(responseBody = "response body")
    val response = sut.get(fixture.server.url("/hello").toString())

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("http", it.type)
          assertEquals("GET", it.getData("method"))
          assertEquals(201, it.getData("status_code"))
          assertEquals(fixture.server.url("/hello").toString(), it.getData("url"))
          assertEquals(13L, it.getData(SpanDataConvention.HTTP_RESPONSE_CONTENT_LENGTH_KEY))
          assertNotNull(it.getData(SpanDataConvention.HTTP_START_TIMESTAMP))
        },
        anyOrNull(),
      )
  }

  @Test
  fun `adds breadcrumb when http call fails`(): Unit = runBlocking {
    val sut = fixture.getSut(httpStatusCode = 500, responseBody = "error")
    val response = sut.get(fixture.server.url("/hello").toString())

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("http", it.type)
          assertEquals("GET", it.getData("method"))
          assertEquals(500, it.getData("status_code"))
          assertEquals(fixture.server.url("/hello").toString(), it.getData("url"))
        },
        anyOrNull(),
      )
  }

  @Test
  fun `captures an event with request and response contexts if captureFailedRequests is enabled and status code is within the range`():
    Unit = runBlocking {
    val statusCode = 500
    val responseBody = "failure"
    val sut =
      fixture.getSut(
        captureFailedRequests = true,
        httpStatusCode = statusCode,
        responseBody = responseBody,
        sendDefaultPii = true,
      )

    val requestBody = "test"
    sut.post(fixture.server.url("/hello?myQuery=myValue#myFragment").toString()) {
      contentType(ContentType.Text.Plain)
      setBody(requestBody)
    }

    verify(fixture.scopes)
      .captureEvent(
        check<IchnoisCoreEvent> {
          val ichnoisRequest = it.request!!
          assertEquals("http://localhost:${fixture.server.port}/hello", ichnoisRequest.url)
          assertEquals("myQuery=myValue", ichnoisRequest.queryString)
          assertEquals("myFragment", ichnoisRequest.fragment)
          assertEquals("POST", ichnoisRequest.method)
          assertEquals(requestBody.length.toLong(), ichnoisRequest.bodySize)
          assertNotNull(ichnoisRequest.headers)

          val ichnoisResponse = it.contexts.response!!
          assertEquals(statusCode, ichnoisResponse.statusCode)
          assertEquals(responseBody.length.toLong(), ichnoisResponse.bodySize)
          assertNotNull(ichnoisResponse.headers)

          assertTrue(it.throwable is IchnoisCoreHttpClientException)
        },
        any<Hint>(),
      )
  }

  @Test
  fun `does not capture an event if captureFailedRequests is disabled`(): Unit = runBlocking {
    val sut = fixture.getSut(httpStatusCode = 500)
    sut.get(fixture.server.url("/hello").toString())

    verify(fixture.scopes, never()).captureEvent(any(), any<Hint>())
  }

  @Test
  fun `does not capture an event if captureFailedRequests is enabled but status code is not within the range`():
    Unit = runBlocking {
    val sut = fixture.getSut(captureFailedRequests = true)
    sut.get(fixture.server.url("/hello").toString())

    verify(fixture.scopes, never()).captureEvent(any(), any<Hint>())
  }

  @Test
  fun `does not capture an event if captureFailedRequests is enabled and domain is not within the targets`():
    Unit = runBlocking {
    val sut =
      fixture.getSut(
        captureFailedRequests = true,
        httpStatusCode = 500,
        failedRequestTargets = listOf("myapi.com"),
      )
    sut.get(fixture.server.url("/hello").toString())

    verify(fixture.scopes, never()).captureEvent(any(), any<Hint>())
  }

  @Test
  fun `captures failed requests with custom status code ranges`(): Unit = runBlocking {
    val sut =
      fixture.getSut(
        captureFailedRequests = true,
        httpStatusCode = 404,
        failedRequestStatusCodes = listOf(HttpStatusCodeRange(400, 499)),
      )
    sut.get(fixture.server.url("/hello").toString())

    verify(fixture.scopes).captureEvent(any(), any<Hint>())
  }

  @Test
  fun `does not capture failed requests outside custom status code ranges`(): Unit = runBlocking {
    val sut =
      fixture.getSut(
        captureFailedRequests = true,
        httpStatusCode = 500,
        failedRequestStatusCodes = listOf(HttpStatusCodeRange(400, 499)),
      )
    sut.get(fixture.server.url("/hello").toString())

    verify(fixture.scopes, never()).captureEvent(any(), any<Hint>())
  }

  @Test
  fun `data collection filters cookies`(): Unit = runBlocking {
    val sut =
      fixture.getSut(
        captureFailedRequests = true,
        httpStatusCode = 500,
        optionsConfiguration =
          IchnoisCore.OptionsConfiguration {
            it.dataCollection.cookies = KeyValueCollectionBehavior.denyList("theme")
          },
      )

    sut.get(fixture.server.url("/hello").toString()) {
      headers["Cookie"] = "language=en; theme=dark; sessionId=secret"
    }

    verify(fixture.scopes)
      .captureEvent(
        check<IchnoisCoreEvent> {
          assertEquals(
            "language=en; theme=[Filtered]; sessionId=[Filtered]",
            it.request!!.cookies,
          )
          assertEquals("theme=[Filtered]; Path=/", it.contexts.response!!.cookies)
        },
        any<Hint>(),
      )
  }

  @Test
  fun `data collection can disable cookies`(): Unit = runBlocking {
    val sut =
      fixture.getSut(
        captureFailedRequests = true,
        httpStatusCode = 500,
        sendDefaultPii = true,
        optionsConfiguration =
          IchnoisCore.OptionsConfiguration {
            it.dataCollection.cookies = KeyValueCollectionBehavior.off()
          },
      )

    sut.get(fixture.server.url("/hello").toString()) { headers["Cookie"] = "theme=dark" }

    verify(fixture.scopes)
      .captureEvent(
        check<IchnoisCoreEvent> {
          assertNull(it.request!!.cookies)
          assertNull(it.contexts.response!!.cookies)
        },
        any<Hint>(),
      )
  }

  @Test
  fun `data collection filters request headers`(): Unit = runBlocking {
    val sut =
      fixture.getSut(
        captureFailedRequests = true,
        httpStatusCode = 500,
        optionsConfiguration =
          IchnoisCore.OptionsConfiguration {
            it.dataCollection.httpHeaders.request = KeyValueCollectionBehavior.denyList("customer")
          },
      )

    sut.get(fixture.server.url("/hello").toString()) {
      headers["content-type"] = "application/json"
      headers["authorization"] = "Bearer token"
      headers["x-customer"] = "customer value"
    }

    verify(fixture.scopes)
      .captureEvent(
        check<IchnoisCoreEvent> {
          assertEquals("application/json", it.request!!.headers!!["content-type"])
          assertEquals("[Filtered]", it.request!!.headers!!["authorization"])
          assertEquals("[Filtered]", it.request!!.headers!!["x-customer"])
        },
        any<Hint>(),
      )
  }

  @Test
  fun `data collection can disable request headers`(): Unit = runBlocking {
    val sut =
      fixture.getSut(
        captureFailedRequests = true,
        httpStatusCode = 500,
        optionsConfiguration =
          IchnoisCore.OptionsConfiguration {
            it.dataCollection.httpHeaders.request = KeyValueCollectionBehavior.off()
          },
      )

    sut.get(fixture.server.url("/hello").toString()) { headers["myHeader"] = "myValue" }

    verify(fixture.scopes)
      .captureEvent(
        check<IchnoisCoreEvent> { assertTrue(it.request!!.headers!!.isEmpty()) },
        any<Hint>(),
      )
  }

  @Test
  fun `data collection filters response headers`(): Unit = runBlocking {
    val sut =
      fixture.getSut(
        captureFailedRequests = true,
        httpStatusCode = 500,
        optionsConfiguration =
          IchnoisCore.OptionsConfiguration {
            it.dataCollection.httpHeaders.response = KeyValueCollectionBehavior.denyList("response")
          },
      )

    sut.get(fixture.server.url("/hello").toString())

    verify(fixture.scopes)
      .captureEvent(
        check<IchnoisCoreEvent> {
          assertEquals(
            "[Filtered]",
            it.contexts.response!!
              .headers!!
              .entries
              .firstOrNull { header ->
                header.key.equals("myResponseHeader", ignoreCase = true)
              }
              ?.value,
          )
        },
        any<Hint>(),
      )
  }

  @Test
  fun `data collection can disable response headers`(): Unit = runBlocking {
    val sut =
      fixture.getSut(
        captureFailedRequests = true,
        httpStatusCode = 500,
        optionsConfiguration =
          IchnoisCore.OptionsConfiguration {
            it.dataCollection.httpHeaders.response = KeyValueCollectionBehavior.off()
          },
      )

    sut.get(fixture.server.url("/hello").toString())

    verify(fixture.scopes)
      .captureEvent(
        check<IchnoisCoreEvent> { assertTrue(it.contexts.response!!.headers!!.isEmpty()) },
        any<Hint>(),
      )
  }

  @Test
  fun `does not capture headers when sendDefaultPii is disabled`(): Unit = runBlocking {
    val sut =
      fixture.getSut(captureFailedRequests = true, httpStatusCode = 500, sendDefaultPii = false)

    sut.get(fixture.server.url("/hello").toString()) { headers["myHeader"] = "myValue" }

    verify(fixture.scopes)
      .captureEvent(
        check<IchnoisCoreEvent> {
          val ichnoisRequest = it.request!!
          assertEquals(null, ichnoisRequest.headers)

          val ichnoisResponse = it.contexts.response!!
          assertEquals(null, ichnoisResponse.headers)
        },
        any<Hint>(),
      )
  }

  @Test
  fun `reports callback errors only for sampled spans`(): Unit = runBlocking {
    for (sampled in listOf(true, false, null)) {
      val fixture = Fixture()
      val onDiscard = mock<IchnoisCoreOptions.OnDiscardCallback>()
      val sut =
        fixture.getSut(
          beforeSpan = { span, _ ->
            span.spanContext.sampled = false
            error("callback failed")
          }
        )
      fixture.ichnoisTracer.spanContext.sampled = sampled
      fixture.options.onDiscard = onDiscard

      sut.use { sut.get(fixture.server.url("/hello").toString()) }
      fixture.ichnoisTracer.finish()

      if (sampled == true) {
        verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Span, 1)
      }
      verifyNoMoreInteractions(onDiscard)
    }
  }

  @Test
  fun `when beforeSpan throws, drops span and preserves response`(): Unit = runBlocking {
    val failure = AssertionError("callback failed")
    val logger = mock<ILogger>()
    val sut =
      fixture.getSut(
        beforeSpan = { span, _ ->
          span.description = "partially modified"
          throw failure
        }
      )
    fixture.options.isDebug = true
    fixture.options.setLogger(logger)
    sut.use {
      val response = sut.get(fixture.server.url("/hello").toString())
      assertThat(response.status.value).isEqualTo(201)
      assertThat(response.bodyAsText()).isEqualTo("success")
    }
    val span = fixture.ichnoisTracer.children.single()
    assertThat(span.isSampled).isFalse()
    assertThat(span.isFinished).isTrue()
    assertThat(span.status).isEqualTo(SpanStatus.OK)
    verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    verify(logger)
      .log(
        IchnoisCoreLevel.ERROR,
        "The beforeSpan callback threw an exception in IchnoisCoreKtorClientPlugin. Dropping span.",
        failure,
      )
  }

  @Test
  fun `fatal error from beforeSpan is not swallowed`(): Unit = runBlocking {
    val failure = OutOfMemoryError("callback failed")
    val sut = fixture.getSut(beforeSpan = { _, _ -> throw failure })

    val thrown =
      assertFailsWith<OutOfMemoryError> {
        sut.use { sut.get(fixture.server.url("/hello").toString()) }
      }

    assertThat(generateSequence<Throwable>(thrown) { it.cause }.last()).isSameInstanceAs(failure)
  }

  @Test
  fun `beforeSpan can drop span`(): Unit = runBlocking {
    val sut = fixture.getSut(beforeSpan = { _, _ -> null })
    val onDiscard = mock<IchnoisCoreOptions.OnDiscardCallback>()
    fixture.options.onDiscard = onDiscard
    fixture.ichnoisTracer.spanContext.sampled = true
    sut.use { sut.get(fixture.server.url("/hello").toString()) }
    verifyNoMoreInteractions(onDiscard)
    val span = fixture.ichnoisTracer.children.single()
    assertThat(span.isSampled).isFalse()
    assertThat(span.isFinished).isTrue()
  }

  @Test
  fun `beforeSpan can modify span`(): Unit = runBlocking {
    val sut =
      fixture.getSut(
        beforeSpan = { span, _ ->
          span.description = "changed"
          span
        }
      )
    sut.use { sut.get(fixture.server.url("/hello").toString()) }
    val span = fixture.ichnoisTracer.children.single()
    assertThat(span.description).isEqualTo("changed")
    assertThat(span.isFinished).isTrue()
  }

  @Test
  fun `creates a span around the request`(): Unit = runBlocking {
    val sut = fixture.getSut()
    val url = fixture.server.url("/hello").toString()
    sut.get(url)

    assertEquals(1, fixture.ichnoisTracer.children.size)
    val httpClientSpan = fixture.ichnoisTracer.children.first()
    assertEquals("http.client", httpClientSpan.operation)
    assertEquals("GET $url", httpClientSpan.description)
    assertEquals("auto.http.ktor-client", httpClientSpan.spanContext.origin)
    assertEquals(SpanStatus.OK, httpClientSpan.status)
    assertTrue(httpClientSpan.isFinished)
  }

  @Test
  fun `span description excludes query parameters and fragment`(): Unit = runBlocking {
    val sut = fixture.getSut()
    sut.get(fixture.server.url("/hello?token=secret&page=1#results").toString())

    val httpClientSpan = fixture.ichnoisTracer.children.first()
    assertEquals("GET ${fixture.server.url("/hello")}", httpClientSpan.description)
    assertNull(httpClientSpan.data[SpanDataConvention.HTTP_QUERY_KEY])
  }

  @Test
  fun `finishes span setting throwable and status when request throws`(): Unit = runBlocking {
    val sut = fixture.getSut(socketPolicy = SocketPolicy.DISCONNECT_DURING_REQUEST_BODY)

    var exception: Exception? = null
    try {
      sut.post(fixture.server.url("/hello?myQuery=myValue#myFragment").toString()) {
        contentType(ContentType.Text.Plain)
        setBody("hello hello")
      }
    } catch (e: Exception) {
      exception = e
    }

    val httpClientSpan = fixture.ichnoisTracer.children.first()
    assertTrue(httpClientSpan.isFinished)
    assertEquals(SpanStatus.INTERNAL_ERROR, httpClientSpan.status)
    assertEquals(
      httpClientSpan.throwable.toString(),
      exception.toString(),
    ) // stack trace will differ
  }

  @Test
  fun `when there is an active span and server is listed in tracing origins, adds ichnois trace headers to the request`():
    Unit = runBlocking {
    val sut = fixture.getSut()
    sut.get(fixture.server.url("/hello").toString())

    val recordedRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recordedRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNotNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is an active span and tracing origins contains default regex, adds ichnois trace headers to the request`():
    Unit = runBlocking {
    val sut = fixture.getSut(keepDefaultTracePropagationTargets = true)
    sut.get(fixture.server.url("/hello").toString())

    val recordedRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recordedRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNotNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is an active span and server is not listed in propagation targets, does not add ichnois trace headers to the request`():
    Unit = runBlocking {
    val sut =
      fixture.getSut(
        includeMockServerInTracePropagationTargets = false,
        keepDefaultTracePropagationTargets = false,
      )
    fixture.options.setTracePropagationTargets(emptyList())
    sut.get(fixture.server.url("/hello").toString())

    val recordedRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNull(recordedRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is no active span, adds ichnois trace header to the request from scope`(): Unit =
    runBlocking {
      val sut = fixture.getSut(isSpanActive = false)
      sut.get(fixture.server.url("/hello").toString())

      val recordedRequest =
        fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
      assertNotNull(recordedRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
      assertNotNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
    }

  @Test
  fun `does not add ichnois-trace header when span origin is ignored`(): Unit = runBlocking {
    val sut =
      fixture.getSut(
        isSpanActive = false,
        optionsConfiguration = { options ->
          options.setIgnoredSpanOrigins(listOf("auto.http.ktor-client"))
        },
      )
    sut.get(fixture.server.url("/hello").toString())

    val recordedRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNull(recordedRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is no active span and host is not allowed, does not add ichnois trace header to the request`():
    Unit = runBlocking {
    val sut = fixture.getSut(isSpanActive = false)
    fixture.options.setTracePropagationTargets(listOf("some-host-that-does-not-exist"))
    sut.get(fixture.server.url("/hello").toString())

    val recordedRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNull(recordedRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is an active span, existing baggage headers are merged with ichnois baggage into single header`():
    Unit = runBlocking {
    val sut = fixture.getSut()
    sut.get(fixture.server.url("/hello").toString()) {
      headers["baggage"] = "thirdPartyBaggage=someValue"
      headers.append(
        "baggage",
        "secondThirdPartyBaggage=secondValue; property;propertyKey=propertyValue,anotherThirdPartyBaggage=anotherValue",
      )
    }

    val recordedRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recordedRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNotNull(recordedRequest.headers[BaggageHeader.BAGGAGE_HEADER])

    val baggageHeaderValues = recordedRequest.headers.values(BaggageHeader.BAGGAGE_HEADER)
    assertEquals(1, baggageHeaderValues.size)
    assertTrue(
      baggageHeaderValues[0].startsWith(
        "thirdPartyBaggage=someValue," +
          "secondThirdPartyBaggage=secondValue; " +
          "property;propertyKey=propertyValue," +
          "anotherThirdPartyBaggage=anotherValue"
      )
    )
    assertTrue(baggageHeaderValues[0].contains("ichnois-public_key=key"))
    assertTrue(baggageHeaderValues[0].contains("ichnois-transaction=name"))
    assertTrue(baggageHeaderValues[0].contains("ichnois-trace_id"))
  }

  @Test
  fun `adds W3C traceparent header when propagateTraceparent is enabled`(): Unit = runBlocking {
    val sut =
      fixture.getSut(optionsConfiguration = { options -> options.isPropagateTraceparent = true })
    sut.get(fixture.server.url("/hello").toString())

    val recordedRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recordedRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNotNull(recordedRequest.headers[W3CTraceparentHeader.TRACEPARENT_HEADER])
  }

  @Test
  fun `does not add W3C traceparent header when propagateTraceparent is disabled`(): Unit =
    runBlocking {
      val sut =
        fixture.getSut(optionsConfiguration = { options -> options.isPropagateTraceparent = false })
      sut.get(fixture.server.url("/hello").toString())

      val recordedRequest =
        fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
      assertNotNull(recordedRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
      assertNull(recordedRequest.headers[W3CTraceparentHeader.TRACEPARENT_HEADER])
    }
}
