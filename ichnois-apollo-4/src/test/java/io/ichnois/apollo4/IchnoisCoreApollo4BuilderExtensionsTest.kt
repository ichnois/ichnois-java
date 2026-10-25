package io.ichnois.apollo4

import com.apollographql.apollo.ApolloCall
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.exception.ApolloException
import io.ichnois.Breadcrumb
import io.ichnois.IScopes
import io.ichnois.ITransaction
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreTracer
import io.ichnois.SpanStatus
import io.ichnois.TraceContext
import io.ichnois.TracesSamplingDecision
import io.ichnois.TransactionContext
import io.ichnois.apollo4.IchnoisCoreApollo4HttpInterceptor.BeforeSpanCallback
import io.ichnois.apollo4.generated.LaunchDetailsQuery
import io.ichnois.mockServerRequestTimeoutMillis
import io.ichnois.protocol.IchnoisCoreTransaction
import java.util.concurrent.TimeUnit
import kotlin.reflect.KSuspendFunction1
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IchnoisCoreApollo4BuilderExtensionsTestWithV4Implementation :
  IchnoisCoreApollo4BuilderExtensionsTest(ApolloCall<*>::execute)

class IchnoisCoreApollo4BuilderExtensionsTestWithV3Implementation :
  IchnoisCoreApollo4BuilderExtensionsTest(ApolloCall<*>::executeV3)

abstract class IchnoisCoreApollo4BuilderExtensionsTest(
  private val executeQueryImplementation:
    KSuspendFunction1<ApolloCall<*>, ApolloResponse<out Operation.Data>>
) {
  class Fixture {
    val server = MockWebServer()
    val scopes = mock<IScopes>()

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
      beforeSpan: BeforeSpanCallback? = null,
      options: IchnoisCoreOptions = IchnoisCoreOptions().apply { dsn = "http://key@localhost/proj" },
    ): ApolloClient {
      whenever(scopes.options).thenReturn(options)

      server.enqueue(
        MockResponse()
          .setBody(responseBody)
          .setSocketPolicy(socketPolicy)
          .setResponseCode(httpStatusCode)
      )

      return ApolloClient.Builder()
        .serverUrl(server.url("/").toString())
        .ichnoisTracing(scopes = scopes, beforeSpan = beforeSpan, captureFailedRequests = false)
        .build()
    }
  }

  private val fixture = Fixture()

  @Test
  fun `creates span around successful request`() {
    executeQuery()

    verify(fixture.scopes)
      .captureTransaction(
        check {
          assertTransactionDetails(it)
          assertEquals(SpanStatus.OK, it.spans.first().status)
        },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `does not attach GraphQL variables when data collection disables them`() {
    val options =
      IchnoisCoreOptions().apply {
        dsn = "http://key@localhost/proj"
        dataCollection.graphql.setVariables(false)
      }

    executeQuery(fixture.getSut(options = options))

    verify(fixture.scopes)
      .captureTransaction(
        check {
          assertNull(it.spans.first().data?.get("variables"))
          assertNotNull(it.spans.first().data?.get("operationId"))
        },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `creates span around failed request`() {
    executeQuery(fixture.getSut(httpStatusCode = 403))

    verify(fixture.scopes)
      .captureTransaction(
        check {
          assertTransactionDetails(it)
          assertEquals(SpanStatus.PERMISSION_DENIED, it.spans.first().status)
        },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `creates span around request failing with network error`() {
    executeQuery(fixture.getSut(socketPolicy = SocketPolicy.DISCONNECT_DURING_REQUEST_BODY))

    verify(fixture.scopes)
      .captureTransaction(
        check {
          assertTransactionDetails(it)
          assertEquals(SpanStatus.INTERNAL_ERROR, it.spans.first().status)
        },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `adds breadcrumb when http call succeeds`() {
    executeQuery(fixture.getSut())

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("http", it.type)
          assertEquals(200, it.data["status_code"])
          // response_body_size is added but mock webserver returns 0 always
          assertEquals(0L, it.data["response_body_size"])
          assertEquals(193L, it.data["request_body_size"])
          assertEquals("query", it.data["operation_type"])
        },
        anyOrNull(),
      )
  }

  @Test
  fun `adds breadcrumb when http call fails`() {
    executeQuery(fixture.getSut(socketPolicy = SocketPolicy.DISCONNECT_DURING_REQUEST_BODY))

    verify(fixture.scopes)
      .addBreadcrumb(
        check<Breadcrumb> {
          assertEquals("http", it.type)
          assertEquals(193L, it.data["request_body_size"])
          assertEquals("query", it.data["operation_type"])
        },
        anyOrNull(),
      )
  }

  @Test
  fun `handles non-ascii header values correctly`() {
    executeQuery(id = "á")

    verify(fixture.scopes)
      .captureTransaction(
        check {
          assertTransactionDetails(it)
          assertEquals(SpanStatus.OK, it.spans.first().status)
        },
        anyOrNull<TraceContext>(),
        anyOrNull(),
        anyOrNull(),
      )
  }

  @Test
  fun `does not send internal headers over the wire`() {
    executeQuery(fixture.getSut())
    val recordedRequest =
      fixture.server.takeRequest(mockServerRequestTimeoutMillis, TimeUnit.MILLISECONDS)!!
    for (ichnoisHeader in INTERNAL_HEADER_NAMES) {
      assertTrue(recordedRequest.headers.none { header -> header.first.equals(ichnoisHeader, true) })
    }
  }

  private fun assertTransactionDetails(it: IchnoisCoreTransaction) {
    assertEquals(1, it.spans.size)
    val httpClientSpan = it.spans.first()
    assertEquals("http.graphql.query", httpClientSpan.op)
    assertEquals("query LaunchDetails", httpClientSpan.description)
    assertEquals("auto.graphql.apollo4", httpClientSpan.origin)
    assertNotNull(httpClientSpan.data) {
      assertNotNull(it["operationId"])
      assertNotNull(it["variables"])
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
      whenever(fixture.scopes.span).thenReturn(tx)
    }

    val coroutine = launch {
      try {
        executeQueryImplementation(sut.query(LaunchDetailsQuery(id)))
      } catch (e: ApolloException) {
        return@launch
      }
    }

    coroutine.join()
    tx?.finish()
  }
}
