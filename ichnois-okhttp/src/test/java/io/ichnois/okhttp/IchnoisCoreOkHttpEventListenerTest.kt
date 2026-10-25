package io.ichnois.okhttp

import io.ichnois.BaggageHeader
import io.ichnois.IScopes
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreTraceHeader
import io.ichnois.IchnoisCoreTracer
import io.ichnois.SpanDataConvention
import io.ichnois.SpanStatus
import io.ichnois.TransactionContext
import io.ichnois.mockServerRequestTimeoutMillis
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IchnoisCoreOkHttpEventListenerTest {
  class Fixture {
    val scopes = mock<IScopes>()
    val server = MockWebServer()
    val mockEventListener = mock<EventListener>()
    val mockEventListenerFactory = mock<EventListener.Factory>()
    lateinit var ichnoisTracer: IchnoisCoreTracer
    lateinit var options: IchnoisCoreOptions
    lateinit var ichnoisOkHttpEventListener: IchnoisCoreOkHttpEventListener

    init {
      whenever(mockEventListenerFactory.create(any())).thenReturn(mockEventListener)
    }

    @SuppressWarnings("LongParameterList")
    fun getSut(
      isSpanActive: Boolean = true,
      useInterceptor: Boolean = false,
      httpStatusCode: Int = 201,
      sendDefaultPii: Boolean = false,
      configureOptions: (options: IchnoisCoreOptions) -> Unit = {},
      eventListener: EventListener? = null,
      eventListenerFactory: EventListener.Factory? = null,
    ): OkHttpClient {
      options =
        IchnoisCoreOptions().apply {
          dsn = "https://key@ichnois-h2jw.onrender.com/proj"
          isSendDefaultPii = sendDefaultPii
          configureOptions(this)
        }
      whenever(scopes.options).thenReturn(options)

      ichnoisTracer = IchnoisCoreTracer(TransactionContext("name", "op"), scopes)

      if (isSpanActive) {
        whenever(scopes.span).thenReturn(ichnoisTracer)
      }
      server.enqueue(
        MockResponse()
          .setBody("responseBody")
          .addHeader("myResponseHeader", "myValue")
          .setSocketPolicy(SocketPolicy.KEEP_OPEN)
          .setResponseCode(httpStatusCode)
      )

      val builder = OkHttpClient.Builder()
      if (useInterceptor) {
        builder.addInterceptor(IchnoisCoreOkHttpInterceptor(scopes))
      }
      ichnoisOkHttpEventListener =
        when {
          eventListenerFactory != null -> IchnoisCoreOkHttpEventListener(scopes, eventListenerFactory)
          eventListener != null -> IchnoisCoreOkHttpEventListener(scopes, eventListener)
          else -> IchnoisCoreOkHttpEventListener(scopes)
        }
      return builder.eventListener(ichnoisOkHttpEventListener).build()
    }
  }

  private val fixture = Fixture()

  private fun getRequest(url: String = "/hello"): Request =
    Request.Builder().addHeader("myHeader", "myValue").get().url(fixture.server.url(url)).build()

  private fun postRequest(url: String = "/hello", body: String): Request =
    Request.Builder()
      .addHeader("myHeader", "myValue")
      .post(body.toRequestBody())
      .url(fixture.server.url(url))
      .build()

  @Test
  fun `when there is an active span and the IchnoisCoreOkHttpInterceptor, adds ichnois trace headers to the request`() {
    val sut = fixture.getSut(useInterceptor = true)
    sut.newCall(getRequest()).execute()
    val recorderRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNotNull(recorderRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNotNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Suppress("MaxLineLength")
  @Test
  fun `when there is an active span but no IchnoisCoreOkHttpInterceptor, ichnois trace headers are not added to the request`() {
    val sut = fixture.getSut()
    sut.newCall(getRequest()).execute()
    val recorderRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    assertNull(recorderRequest.headers[IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER])
    assertNull(recorderRequest.headers[BaggageHeader.BAGGAGE_HEADER])
  }

  @Test
  fun `creates a span around the request`() {
    val sut = fixture.getSut()
    val request = getRequest()
    val call = sut.newCall(request)
    val response = call.execute()
    val okHttpEvent = IchnoisCoreOkHttpEventListener.eventMap[call]
    val callSpan = okHttpEvent?.callSpan
    response.close()
    assertNotNull(callSpan)
    assertEquals(callSpan, fixture.ichnoisTracer.children.first())
    assertEquals("http.client", callSpan.operation)
    assertEquals("GET ${request.url}", callSpan.description)
    assertEquals(SpanStatus.OK, callSpan.status)
    assertTrue(callSpan.isFinished)
  }

  @Test
  fun `adds a data for each event`() {
    val sut = fixture.getSut()
    val request = getRequest()
    val call = sut.newCall(request)
    val response = call.execute()
    val okHttpEvent = IchnoisCoreOkHttpEventListener.eventMap[call]
    val callSpan = okHttpEvent?.callSpan
    response.close()
    assertEquals(1, fixture.ichnoisTracer.children.size)
    assertNotNull(callSpan)
    assertNotNull(callSpan.getData("proxies"))
    assertNotNull(callSpan.getData("domain_name"))
    assertNotNull(callSpan.getData("dns_addresses"))
    assertEquals(201, callSpan.getData(SpanDataConvention.HTTP_STATUS_CODE_KEY))
    assertNotNull(callSpan.getData("http.client.proxy_select_ms"))
    assertNotNull(callSpan.getData("http.client.resolve_dns_ms"))
    assertNotNull(callSpan.getData("http.connect_ms"))
    assertNotNull(callSpan.getData("http.connection_ms"))
    assertNotNull(callSpan.getData("http.connection.request_headers_ms"))
    assertNotNull(callSpan.getData("http.connection.response_headers_ms"))
    assertNotNull(callSpan.getData("http.connection.response_body_ms"))
  }

  @Test
  fun `has requestBody data for requests with body`() {
    val sut = fixture.getSut()
    val requestBody = "request body sent in the request"
    val request = postRequest(body = requestBody)
    val call = sut.newCall(request)
    val response = call.execute()
    val okHttpEvent = IchnoisCoreOkHttpEventListener.eventMap[call]
    val callSpan = okHttpEvent?.callSpan
    response.close()
    assertNotNull(callSpan?.getData("http.connection.request_body_ms"))
    assertEquals(
      requestBody.toByteArray().size.toLong(),
      callSpan?.getData("http.request_content_length"),
    )
  }

  @Test
  fun `has response_body_size data if body is consumed`() {
    val sut = fixture.getSut()
    val requestBody = "request body sent in the request"
    val request = postRequest(body = requestBody)
    val call = sut.newCall(request)
    val response = call.execute()
    val okHttpEvent = IchnoisCoreOkHttpEventListener.eventMap[call]
    val callSpan = okHttpEvent?.callSpan
    assertNull(callSpan?.getData("http.connection.response_body_ms"))

    // Consume the response
    val responseBytes = response.body?.byteStream()?.readBytes()
    assertNotNull(responseBytes)
    assertNotNull(callSpan?.getData("http.connection.response_body_ms"))

    response.close()
    assertEquals(
      responseBytes.size.toLong(),
      callSpan?.getData(SpanDataConvention.HTTP_RESPONSE_CONTENT_LENGTH_KEY),
    )
  }

  @Test
  fun `call span status depends on http status code`() {
    val sut = fixture.getSut(httpStatusCode = 404)
    val request = getRequest()
    val call = sut.newCall(request)
    val response = call.execute()
    val okHttpEvent = IchnoisCoreOkHttpEventListener.eventMap[call]
    val callSpan = okHttpEvent?.callSpan
    response.close()
    assertNotNull(callSpan)
    assertEquals(SpanStatus.fromHttpStatusCode(404), callSpan.status)
  }

  @Test
  fun `propagate all calls to the event listener passed in the ctor`() {
    val sut = fixture.getSut(eventListener = fixture.mockEventListener)
    val listener = fixture.ichnoisOkHttpEventListener
    val request = postRequest(body = "requestBody")
    val call = sut.newCall(request)
    val response = mock<Response>()
    whenever(response.protocol).thenReturn(Protocol.HTTP_1_1)
    verifyDelegation(listener, fixture.mockEventListener, call, sut.newCall(request), response)
  }

  @Test
  fun `propagate all calls to the event listener factory passed in the ctor`() {
    val sut = fixture.getSut(eventListenerFactory = fixture.mockEventListenerFactory)
    val listener = fixture.ichnoisOkHttpEventListener
    val request = postRequest(body = "requestBody")
    val call = sut.newCall(request)
    val response = mock<Response>()
    whenever(response.protocol).thenReturn(Protocol.HTTP_1_1)
    verifyDelegation(listener, fixture.mockEventListener, call, sut.newCall(request), response)
  }

  @Test
  fun `propagate all calls to the IchnoisCoreOkHttpEventListener passed in the ctor`() {
    val originalListener = spy(IchnoisCoreOkHttpEventListener(fixture.scopes, fixture.mockEventListener))
    val sut = fixture.getSut(eventListener = originalListener)
    val listener = fixture.ichnoisOkHttpEventListener
    val request = postRequest(body = "requestBody")
    val call = sut.newCall(request)
    val response = mock<Response>()
    whenever(response.protocol).thenReturn(Protocol.HTTP_1_1)
    verifyDelegation(listener, originalListener, call, sut.newCall(request), response)
  }

  @Test
  fun `propagate all calls to the IchnoisCoreOkHttpEventListener factory passed in the ctor`() {
    val originalListener = spy(IchnoisCoreOkHttpEventListener(fixture.scopes, fixture.mockEventListener))
    val sut = fixture.getSut(eventListenerFactory = { originalListener })
    val listener = fixture.ichnoisOkHttpEventListener
    val request = postRequest(body = "requestBody")
    val call = sut.newCall(request)
    val response = mock<Response>()
    whenever(response.protocol).thenReturn(Protocol.HTTP_1_1)
    verifyDelegation(listener, originalListener, call, sut.newCall(request), response)
  }

  @Test
  fun `does not duplicated spans if an IchnoisCoreOkHttpEventListener is passed in the ctor`() {
    val originalListener = spy(IchnoisCoreOkHttpEventListener(fixture.scopes, fixture.mockEventListener))
    val sut = fixture.getSut(eventListener = originalListener)
    val request = postRequest(body = "requestBody")
    val call = sut.newCall(request)
    val response = call.execute()
    response.close()
    // Spans are created by the originalListener, so the listener doesn't create duplicates
    assertEquals(1, fixture.ichnoisTracer.children.size)
  }

  @Test
  fun `call span status is not overridden if not null`() {
    val mockListener = mock<EventListener>()
    lateinit var call: Call
    whenever(mockListener.connectStart(any(), anyOrNull(), anyOrNull())).then {
      val okHttpEvent = IchnoisCoreOkHttpEventListener.eventMap[call]
      val callSpan = okHttpEvent?.callSpan
      assertNotNull(callSpan)
      assertNull(callSpan.status)
      callSpan.status = SpanStatus.UNKNOWN
      it
    }
    val sut = fixture.getSut(eventListener = mockListener)
    val request = getRequest()
    call = sut.newCall(request)
    val response = call.execute()
    val okHttpEvent = IchnoisCoreOkHttpEventListener.eventMap[call]
    val callSpan = okHttpEvent?.callSpan
    assertNotNull(callSpan)
    response.close()
    assertEquals(SpanStatus.UNKNOWN, callSpan.status)
  }

  private fun verifyDelegation(
    listener: IchnoisCoreOkHttpEventListener,
    originalListener: EventListener,
    call: Call,
    failedCall: Call,
    response: Response,
  ) {
    listener.callStart(call)
    verify(originalListener).callStart(eq(call))
    listener.proxySelectStart(call, mock())
    verify(originalListener).proxySelectStart(eq(call), any())
    listener.proxySelectEnd(call, mock(), listOf(mock()))
    verify(originalListener).proxySelectEnd(eq(call), any(), any())
    listener.dnsStart(call, "domainName")
    verify(originalListener).dnsStart(eq(call), eq("domainName"))
    listener.dnsEnd(call, "domainName", listOf(mock()))
    verify(originalListener).dnsEnd(eq(call), eq("domainName"), any())
    listener.connectStart(call, mock(), mock())
    verify(originalListener).connectStart(eq(call), any(), any())
    listener.secureConnectStart(call)
    verify(originalListener).secureConnectStart(eq(call))
    listener.secureConnectEnd(call, mock())
    verify(originalListener).secureConnectEnd(eq(call), any())
    listener.connectEnd(call, mock(), mock(), mock())
    verify(originalListener).connectEnd(eq(call), any(), any(), any())
    listener.connectFailed(call, mock(), mock(), mock(), mock())
    verify(originalListener).connectFailed(eq(call), any(), any(), any(), any())
    listener.connectionAcquired(call, mock())
    verify(originalListener).connectionAcquired(eq(call), any())
    listener.connectionReleased(call, mock())
    verify(originalListener).connectionReleased(eq(call), any())
    listener.requestHeadersStart(call)
    verify(originalListener).requestHeadersStart(eq(call))
    listener.requestHeadersEnd(call, mock())
    verify(originalListener).requestHeadersEnd(eq(call), any())
    listener.requestBodyStart(call)
    verify(originalListener).requestBodyStart(eq(call))
    listener.requestBodyEnd(call, 10)
    verify(originalListener).requestBodyEnd(eq(call), eq(10))
    listener.requestFailed(call, mock())
    verify(originalListener).requestFailed(eq(call), any())
    listener.responseHeadersStart(call)
    verify(originalListener).responseHeadersStart(eq(call))
    listener.responseHeadersEnd(call, response)
    verify(originalListener).responseHeadersEnd(eq(call), any())
    listener.responseBodyStart(call)
    verify(originalListener).responseBodyStart(eq(call))
    listener.responseBodyEnd(call, 10)
    verify(originalListener).responseBodyEnd(eq(call), eq(10))
    listener.responseFailed(call, mock())
    verify(originalListener).responseFailed(eq(call), any())
    listener.callEnd(call)
    verify(originalListener).callEnd(eq(call))

    // callEnd and callFailed are both terminal, so a failing call is a separate one
    listener.callStart(failedCall)
    listener.callFailed(failedCall, mock())
    verify(originalListener).callFailed(eq(failedCall), any())
  }
}
