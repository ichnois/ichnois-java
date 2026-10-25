package io.ichnois.okhttp

import io.ichnois.Hint
import io.ichnois.IScopes
import io.ichnois.KeyValueCollectionBehavior
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreTracer
import io.ichnois.TransactionContext
import io.ichnois.TypeCheckHint
import io.ichnois.exception.IchnoisCoreHttpClientException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IchnoisCoreOkHttpUtilsTest {
  class Fixture {
    val scopes = mock<IScopes>()
    val server = MockWebServer()

    fun getSut(
      httpStatusCode: Int = 500,
      responseBody: String = "success",
      socketPolicy: SocketPolicy = SocketPolicy.KEEP_OPEN,
      sendDefaultPii: Boolean = false,
      configureOptions: IchnoisCoreOptions.() -> Unit = {},
    ): OkHttpClient {
      val options =
        IchnoisCoreOptions().apply {
          dsn = "https://key@ichnois-h2jw.onrender.com/proj"
          setTracePropagationTargets(listOf(server.hostName))
          isSendDefaultPii = sendDefaultPii
          configureOptions()
        }
      whenever(scopes.options).thenReturn(options)

      val ichnoisTracer = IchnoisCoreTracer(TransactionContext("name", "op"), scopes)

      whenever(scopes.span).thenReturn(ichnoisTracer)

      server.enqueue(
        MockResponse()
          .setBody(responseBody)
          .addHeader("myResponseHeader", "myValue")
          .addHeader("Set-Cookie", "theme=dark; Path=/")
          .setSocketPolicy(socketPolicy)
          .setResponseCode(httpStatusCode)
      )
      return OkHttpClient.Builder().build()
    }
  }

  private val fixture = Fixture()

  private fun getRequest(url: String = "/hello"): Request =
    Request.Builder()
      .addHeader("myHeader", "myValue")
      .addHeader("Cookie", "theme=dark; sessionId=secret")
      .get()
      .url(fixture.server.url(url))
      .build()

  @Test
  fun `captureClientError captures a client error`() {
    val sut = fixture.getSut()
    val request = getRequest()
    val response = sut.newCall(request).execute()

    IchnoisCoreOkHttpUtils.captureClientError(fixture.scopes, request, response)
    verify(fixture.scopes)
      .captureEvent(
        check {
          val req = it.request
          val resp = it.contexts.response
          assertIs<IchnoisCoreHttpClientException>(it.throwable)
          assertTrue(it.throwable!!.message!!.startsWith("HTTP Client Error with status code: "))

          assertEquals(req!!.method, request.method)

          assertEquals(resp!!.statusCode, response.code)
        },
        argThat<Hint> {
          get(TypeCheckHint.OKHTTP_REQUEST) != null && get(TypeCheckHint.OKHTTP_RESPONSE) != null
        },
      )
  }

  @Test
  fun `captureClientError with sendDefaultPii sends headers`() {
    val sut = fixture.getSut(sendDefaultPii = true)
    val request = getRequest()
    val response = sut.newCall(request).execute()

    IchnoisCoreOkHttpUtils.captureClientError(fixture.scopes, request, response)
    verify(fixture.scopes)
      .captureEvent(
        check {
          val req = it.request
          val resp = it.contexts.response

          assertIs<io.ichnois.protocol.Request>(req)
          assertTrue(req.headers!!.isNotEmpty())
          assertNotNull(req.cookies)

          assertIs<io.ichnois.protocol.Response>(resp)
          assertTrue(resp.headers!!.isNotEmpty())
          assertNotNull(resp.cookies)
        },
        any<Hint>(),
      )
  }

  @Test
  fun `data collection filters request cookies`() {
    val sut = fixture.getSut {
      dataCollection.cookies = KeyValueCollectionBehavior.denyList("theme")
    }
    val request = getRequest()
    val response = sut.newCall(request).execute()

    IchnoisCoreOkHttpUtils.captureClientError(fixture.scopes, request, response)

    verify(fixture.scopes)
      .captureEvent(
        check {
          assertEquals("theme=[Filtered]; sessionId=[Filtered]", it.request!!.cookies)
          assertEquals("theme=[Filtered]; Path=/", it.contexts.response!!.cookies)
        },
        any<Hint>(),
      )
  }

  @Test
  fun `data collection can disable cookies`() {
    val sut = fixture.getSut { dataCollection.cookies = KeyValueCollectionBehavior.off() }
    val request = getRequest()
    val response = sut.newCall(request).execute()

    IchnoisCoreOkHttpUtils.captureClientError(fixture.scopes, request, response)

    verify(fixture.scopes)
      .captureEvent(
        check {
          assertNull(it.request!!.cookies)
          assertNull(it.contexts.response!!.cookies)
        },
        any<Hint>(),
      )
  }

  @Test
  fun `data collection cookie defaults ignore sendDefaultPii`() {
    val sut = fixture.getSut(sendDefaultPii = false) { dataCollection.setUserInfo(false) }
    val request = getRequest()
    val response = sut.newCall(request).execute()

    IchnoisCoreOkHttpUtils.captureClientError(fixture.scopes, request, response)

    verify(fixture.scopes)
      .captureEvent(
        check {
          assertEquals("theme=dark; sessionId=[Filtered]", it.request!!.cookies)
          assertEquals("theme=dark; Path=/", it.contexts.response!!.cookies)
        },
        any<Hint>(),
      )
  }

  @Test
  fun `data collection filters request headers`() {
    val sut = fixture.getSut {
      dataCollection.httpHeaders.request = KeyValueCollectionBehavior.denyList("myheader")
    }
    val request = getRequest()
    val response = sut.newCall(request).execute()

    IchnoisCoreOkHttpUtils.captureClientError(fixture.scopes, request, response)

    verify(fixture.scopes)
      .captureEvent(
        check {
          assertEquals("[Filtered]", it.request!!.headers!!["myHeader"])
          assertEquals("[Filtered]", it.request!!.headers!!["Cookie"])
        },
        any<Hint>(),
      )
  }

  @Test
  fun `data collection can disable request headers`() {
    val sut = fixture.getSut {
      dataCollection.httpHeaders.request = KeyValueCollectionBehavior.off()
    }
    val request = getRequest()
    val response = sut.newCall(request).execute()

    IchnoisCoreOkHttpUtils.captureClientError(fixture.scopes, request, response)

    verify(fixture.scopes)
      .captureEvent(check { assertTrue(it.request!!.headers!!.isEmpty()) }, any<Hint>())
  }

  @Test
  fun `data collection filters response headers`() {
    val sut = fixture.getSut {
      dataCollection.httpHeaders.response = KeyValueCollectionBehavior.denyList("response")
    }
    val request = getRequest()
    val response = sut.newCall(request).execute()

    IchnoisCoreOkHttpUtils.captureClientError(fixture.scopes, request, response)

    verify(fixture.scopes)
      .captureEvent(
        check {
          assertEquals("[Filtered]", it.contexts.response!!.headers!!["myResponseHeader"])
          assertEquals("[Filtered]", it.contexts.response!!.headers!!["Set-Cookie"])
        },
        any<Hint>(),
      )
  }

  @Test
  fun `data collection can disable response headers`() {
    val sut = fixture.getSut {
      dataCollection.httpHeaders.response = KeyValueCollectionBehavior.off()
    }
    val request = getRequest()
    val response = sut.newCall(request).execute()

    IchnoisCoreOkHttpUtils.captureClientError(fixture.scopes, request, response)

    verify(fixture.scopes)
      .captureEvent(check { assertTrue(it.contexts.response!!.headers!!.isEmpty()) }, any<Hint>())
  }

  @Test
  fun `captureClientError without sendDefaultPii does not send headers`() {
    val sut = fixture.getSut(sendDefaultPii = false)
    val request = getRequest()
    val response = sut.newCall(request).execute()

    IchnoisCoreOkHttpUtils.captureClientError(fixture.scopes, request, response)
    verify(fixture.scopes)
      .captureEvent(
        check {
          val req = it.request
          val resp = it.contexts.response

          assertIs<io.ichnois.protocol.Request>(req)
          assertNull(req.headers)
          assertNull(req.cookies)

          assertIs<io.ichnois.protocol.Response>(resp)
          assertNull(resp.headers)
          assertNull(resp.cookies)
        },
        any<Hint>(),
      )
  }
}
