package io.ichnois.apollo3

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.http.HttpRequest
import com.apollographql.apollo3.api.http.HttpResponse
import com.apollographql.apollo3.exception.ApolloException
import com.apollographql.apollo3.exception.ApolloHttpException
import com.apollographql.apollo3.network.http.HttpInterceptor
import com.apollographql.apollo3.network.http.HttpInterceptorChain
import com.google.common.truth.Truth.assertThat
import io.ichnois.BaggageHeader
import io.ichnois.Breadcrumb
import io.ichnois.DataCategory
import io.ichnois.ILogger
import io.ichnois.IScopes
import io.ichnois.ITransaction
import io.ichnois.Scope
import io.ichnois.ScopeCallback
import io.ichnois.IchnoisCoreLevel
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreOptions.DEFAULT_PROPAGATION_TARGETS
import io.ichnois.IchnoisCoreTraceHeader
import io.ichnois.IchnoisCoreTracer
import io.ichnois.SpanDataConvention
import io.ichnois.SpanDataConvention.HTTP_METHOD_KEY
import io.ichnois.SpanStatus
import io.ichnois.TraceContext
import io.ichnois.TracesSamplingDecision
import io.ichnois.TransactionContext
import io.ichnois.W3CTraceparentHeader
import io.ichnois.apollo3.IchnoisCoreApollo3HttpInterceptor.BeforeSpanCallback
import io.ichnois.clientreport.DiscardReason
import io.ichnois.mockServerRequestTimeoutMillis
import io.ichnois.protocol.SdkVersion
import io.ichnois.protocol.IchnoisCoreTransaction
import io.ichnois.util.Apollo3PlatformTestManipulator
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Before
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class IchnoisCoreApollo3InterceptorTest {
  class Fixture {
    val server = MockWebServer()
    val options =
      IchnoisCoreOptions().apply {
        dsn = "https://key@ichnois-h2jw.onrender.com/proj"
        setTracePropagationTargets(listOf(DEFAULT_PROPAGATION_TARGETS))
        sdkVersion = SdkVersion("test", "1.2.3")
      }
    val scope = Scope(options)
    val scopes =
      mock<IScopes>().also {
        whenever(it.options).thenReturn(options)
        doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }
          .whenever(it)
          .configureScope(any())
      }
    private var httpInterceptor =
      IchnoisCoreApollo3HttpInterceptor(scopes, captureFailedRequests = false)

    @SuppressWarnings("LongParameterList")
    fun getSut(
      httpStatusCode: Int = 200,
      responseBody: String =
        """{
  "data": {
    "launch": {
      "__typename": "Launch",
      "id": "83",
      "site": "CCAFS SLC 40",
      "mission": {
        "__typename": "Mission",
        "name": "Amos-17",
        "missionPatch": "https://images2.imgbox.com/a0/ab/XUoByiuR_o.png"
      }
    }
  }
}""",
      socketPolicy: SocketPolicy = SocketPolicy.KEEP_OPEN,
      interceptor: HttpInterceptor? = null,
      addThirdPartyBaggageHeader: Boolean = false,
      beforeSpan: BeforeSpanCallback? = null,
    ): ApolloClient {
      server.enqueue(
        MockResponse()
          .setBody(responseBody)
          .setSocketPolicy(socketPolicy)
          .setResponseCode(httpStatusCode)
      )

      if (beforeSpan != null) {
        httpInterceptor =
          IchnoisCoreApollo3HttpInterceptor(scopes, beforeSpan, captureFailedRequests = false)
      }

      val builder =
        ApolloClient.Builder()
          .serverUrl(server.url("/").toString())
          .addHttpInterceptor(httpInterceptor)

      interceptor?.let { builder.addHttpInterceptor(interceptor) }

      if (addThirdPartyBaggageHeader) {
        builder
          .addHttpHeader("baggage", "thirdPartyBaggage=someValue")
          .addHttpHeader(
            "baggage",
            "secondThirdPartyBaggage=secondValue; property;propertyKey=propertyValue,anotherThirdPartyBaggage=anotherValue",
          )
      }

      return builder.build()
    }
  }

  private val fixture = Fixture()

  @Before
  fun setup() {
    Apollo3PlatformTestManipulator.pretendIsAndroid(false)
  }

  @Test
  fun `creates a span around the successful request`() {
    executeQuery()

    verify(fixture.scopes)
      .captureTransaction(
        check {
          assertTransactionDetails(it, httpStatusCode = 200)
          assertEquals(SpanStatus.OK, it.spans.first().status)
        },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `creates a span around the failed request`() {
    executeQuery(fixture.getSut(httpStatusCode = 403))

    verify(fixture.scopes)
      .captureTransaction(
        check {
          assertTransactionDetails(it, httpStatusCode = 403)
          assertEquals(SpanStatus.PERMISSION_DENIED, it.spans.first().status)
        },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `get http status from ApolloHttpException in failed request`() {
    val failingInterceptor =
      object : HttpInterceptor {
        override suspend fun intercept(
          request: HttpRequest,
          chain: HttpInterceptorChain,
        ): HttpResponse = throw ApolloHttpException(404, mock(), mock(), "")
      }
    executeQuery(fixture.getSut(interceptor = failingInterceptor))

    verify(fixture.scopes)
      .captureTransaction(
        check {
          assertTransactionDetails(it, httpStatusCode = 404, contentLength = null)
          assertEquals("POST", it.spans.first().data?.get(SpanDataConvention.HTTP_METHOD_KEY))
          assertEquals(404, it.spans.first().data?.get(SpanDataConvention.HTTP_STATUS_CODE_KEY))
          assertEquals(SpanStatus.NOT_FOUND, it.spans.first().status)
        },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `creates a span around the request failing with network error`() {
    executeQuery(fixture.getSut(socketPolicy = SocketPolicy.DISCONNECT_DURING_REQUEST_BODY))

    verify(fixture.scopes)
      .captureTransaction(
        check {
          assertTransactionDetails(it, httpStatusCode = null, contentLength = null)
          assertEquals(SpanStatus.INTERNAL_ERROR, it.spans.first().status)
        },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `does not add ichnois trace header to the request if host is disallowed`() {
    fixture.options.setTracePropagationTargets(listOf("some-host-that-does-not-exist"))
    executeQuery(isSpanActive = false)

    val recorderRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNull(recorderRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is no active span, does not add ichnois trace header to the request`() {
    executeQuery(isSpanActive = false)

    val recorderRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recorderRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNotNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `does not add ichnois-trace header when span origin is ignored`() {
    fixture.options.setIgnoredSpanOrigins(listOf("auto.graphql.apollo3"))
    executeQuery(isSpanActive = false)

    val recorderRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNull(recorderRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is an active span, adds ichnois trace headers to the request`() {
    executeQuery()
    val recorderRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recorderRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNotNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `when there is an active span, existing baggage headers are merged with ichnois baggage into single header`() {
    executeQuery(sut = fixture.getSut(addThirdPartyBaggageHeader = true))
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
    assertTrue(baggageHeaderValues[0].contains("ichnois-transaction=op"))
    assertTrue(baggageHeaderValues[0].contains("ichnois-trace_id"))
  }

  @Test
  fun `customizer modifies span`() {
    executeQuery(
      fixture.getSut(
        beforeSpan = { span, request, response ->
          span.description = "overwritten description"
          span
        }
      )
    )

    verify(fixture.scopes)
      .captureTransaction(
        check {
          assertEquals(1, it.spans.size)
          val httpClientSpan = it.spans.first()
          assertEquals("overwritten description", httpClientSpan.description)
        },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `returning null in beforeSpan callback drops span`() {
    val onDiscard = mock<IchnoisCoreOptions.OnDiscardCallback>()
    fixture.options.onDiscard = onDiscard
    executeQuery(fixture.getSut(beforeSpan = { _, _, _ -> null }))
    verifyNoMoreInteractions(onDiscard)

    verify(fixture.scopes)
      .captureTransaction(
        check { assertEquals(0, it.spans.size) },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `reports callback errors only for sampled spans`(): Unit = runBlocking {
    for (sampled in listOf(true, false, null)) {
      val onDiscard = mock<IchnoisCoreOptions.OnDiscardCallback>()
      fixture.options.onDiscard = onDiscard
      val tx = IchnoisCoreTracer(TransactionContext("op", "desc"), fixture.scopes)
      tx.spanContext.sampled = sampled
      whenever(fixture.scopes.span).thenReturn(tx)
      val sut =
        fixture.getSut(
          beforeSpan = { span, _, _ ->
            span.spanContext.sampled = false
            error("callback failed")
          }
        )

      assertThat(sut.query(LaunchDetailsQuery("83")).execute().data).isNotNull()
      tx.finish()

      if (sampled == true) {
        verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Span, 1)
      }
      verifyNoMoreInteractions(onDiscard)
    }
  }

  @Test
  fun `when beforeSpan throws, drops span and preserves response`(): Unit = runBlocking {
    val failure = IllegalStateException("callback failed")
    val logger = mock<ILogger>()
    fixture.options.isDebug = true
    fixture.options.setLogger(logger)
    val tx =
      IchnoisCoreTracer(TransactionContext("op", "desc", TracesSamplingDecision(true)), fixture.scopes)
    whenever(fixture.scopes.span).thenReturn(tx)
    val sut =
      fixture.getSut(
        beforeSpan = { span, _, _ ->
          span.description = "partially modified"
          throw failure
        }
      )

    val response = sut.query(LaunchDetailsQuery("83")).execute()
    assertThat(response.data).isNotNull()
    val span = tx.children.single()
    assertThat(span.isSampled).isFalse()
    assertThat(span.isFinished).isTrue()
    tx.finish()
    verify(fixture.scopes)
      .captureTransaction(
        check { assertThat(it.spans).isEmpty() },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
    verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
    verify(logger)
      .log(
        IchnoisCoreLevel.ERROR,
        "An error occurred while executing beforeSpan on ApolloInterceptor",
        failure,
      )
  }

  @Test
  fun `adds breadcrumb when http calls succeeds`() {
    executeQuery(fixture.getSut())
    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("http", it.type)
          // response_body_size is added but mock webserver returns 0 always
          assertEquals(0L, it.data["response_body_size"])
          assertEquals(193L, it.data["request_body_size"])
          assertEquals("LaunchDetails", it.data["operation_name"])
          assertNotNull(it.data["operation_id"])
        },
        anyOrNull(),
      )
  }

  @Test
  fun `sets SDKVersion Info`() {
    assertNotNull(fixture.scopes.options.sdkVersion)
    assert(fixture.scopes.options.sdkVersion!!.integrationSet.contains("Apollo3"))
  }

  @Test
  fun `attaches to root transaction on Android`() {
    Apollo3PlatformTestManipulator.pretendIsAndroid(true)
    executeQuery(fixture.getSut())
    verify(fixture.scopes).transaction
  }

  @Test
  fun `attaches to child span on non-Android`() {
    Apollo3PlatformTestManipulator.pretendIsAndroid(false)
    executeQuery(fixture.getSut())
    verify(fixture.scopes).span
  }

  @Test
  fun `adds W3C traceparent header when propagateTraceparent is enabled`() {
    fixture.options.isPropagateTraceparent = true
    executeQuery()
    val recorderRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recorderRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNotNull(recorderRequest.headers[W3CTraceparentHeader.TRACEPARENT_HEADER])
  }

  @Test
  fun `does not add W3C traceparent header when propagateTraceparent is disabled`() {
    fixture.options.isPropagateTraceparent = false
    executeQuery()
    val recorderRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recorderRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNull(recorderRequest.headers[W3CTraceparentHeader.TRACEPARENT_HEADER])
  }

  private fun assertTransactionDetails(
    it: IchnoisCoreTransaction,
    httpStatusCode: Int? = 200,
    contentLength: Long? = 0L,
  ) {
    assertEquals(1, it.spans.size)
    val httpClientSpan = it.spans.first()
    assertEquals("http.graphql", httpClientSpan.op)
    assertTrue { httpClientSpan.description?.startsWith("Post LaunchDetails") == true }
    assertNotNull(httpClientSpan.data) {
      assertNotNull(it["operationId"])
      assertEquals("POST", it[HTTP_METHOD_KEY])
      httpStatusCode?.let { code ->
        assertEquals(code, it[SpanDataConvention.HTTP_STATUS_CODE_KEY])
      }
      contentLength?.let { contentLength ->
        assertEquals(contentLength, it[SpanDataConvention.HTTP_RESPONSE_CONTENT_LENGTH_KEY])
      }
    }
  }

  private fun executeQuery(
    sut: ApolloClient = fixture.getSut(),
    isSpanActive: Boolean = true,
    id: String = "83",
  ) = runBlocking {
    var tx: ITransaction? = null
    if (isSpanActive) {
      tx =
        IchnoisCoreTracer(TransactionContext("op", "desc", TracesSamplingDecision(true)), fixture.scopes)
      whenever(fixture.scopes.transaction).thenReturn(tx)
      whenever(fixture.scopes.span).thenReturn(tx)
    }

    val coroutine = launch {
      try {
        sut.query(LaunchDetailsQuery(id)).execute()
      } catch (e: ApolloException) {
        return@launch
      }
    }

    coroutine.join()
    tx?.finish()
  }
}
