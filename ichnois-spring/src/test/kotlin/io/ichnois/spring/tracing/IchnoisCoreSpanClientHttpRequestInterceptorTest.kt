package io.ichnois.spring.tracing

import io.ichnois.IScopes
import io.ichnois.Scope
import io.ichnois.ScopeCallback
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreTraceHeader
import io.ichnois.W3CTraceparentHeader
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpResponse
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
class IchnoisCoreSpanClientHttpRequestInterceptorTest {

  class Fixture {
    val request = MockClientHttpRequest(HttpMethod.GET, URI.create("https://example.com/users/123"))

    val options =
      IchnoisCoreOptions().apply {
        dsn = "https://key@ichnois-h2jw.onrender.com/proj"
        tracesSampleRate = 1.0
      }
    val scope = Scope(options)

    val scopes = mock<IScopes>()
    val requestExecution = mock<ClientHttpRequestExecution>()
    val body = "data".toByteArray()

    init {
      whenever(scopes.options).thenReturn(options)
      doAnswer { (it.arguments[0] as ScopeCallback).run(scope) }
        .whenever(scopes)
        .configureScope(any())

      whenever(requestExecution.execute(any(), any())).thenReturn(mock<ClientHttpResponse>())
    }

    fun create(
      config: IchnoisCore.OptionsConfiguration<IchnoisCoreOptions>
    ): IchnoisCoreSpanClientHttpRequestInterceptor {
      config.configure(options)
      return IchnoisCoreSpanClientHttpRequestInterceptor(scopes)
    }
  }

  val fixture = Fixture()

  @Test
  fun `attaches w3c trace parent header when enabled`() {
    val sut = fixture.create { options -> options.isPropagateTraceparent = true }
    sut.intercept(fixture.request, fixture.body, fixture.requestExecution)

    assertNotNull(fixture.request.headers.get(IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER))
    assertNotNull(fixture.request.headers.get(W3CTraceparentHeader.TRACEPARENT_HEADER))
  }

  @Test
  fun `does not attach w3c trace parent header when disabled`() {
    val sut = fixture.create { options -> options.isPropagateTraceparent = false }
    sut.intercept(fixture.request, fixture.body, fixture.requestExecution)

    assertNotNull(fixture.request.headers.get(IchnoisCoreTraceHeader.ICHNOIS_TRACE_HEADER))
    assertNull(fixture.request.headers.get(W3CTraceparentHeader.TRACEPARENT_HEADER))
  }
}
