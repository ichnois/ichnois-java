package io.ichnois.spring.boot.otelnoagentintegrationtest

import com.google.common.truth.Truth.assertThat
import io.ichnois.IchnoisCoreEnvelope
import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.test.transaction
import kotlin.random.Random
import kotlin.test.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod

/**
 * Sends requests that continue an incoming trace, and checks that both the request's transaction
 * and the one for the app's own outgoing request carry the trace.
 */
class DistributedTracingIntegrationTest : IntegrationTest() {

  @Test
  fun `sampled trace continues through the outgoing request`() {
    val traceId = IchnoisCoreId()
    val status =
      get(
        "/tracing/1",
        ichnoisTrace = "$traceId-424cffc8f94feeee-1",
        baggage =
          "ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=$traceId,ichnois-transaction=HTTP%20GET",
      )
    assertThat(status).isEqualTo(500)

    val incoming = awaitTransaction("GET /tracing/{id}", traceId).transaction()!!
    assertThat(incoming.request?.url).endsWith("/tracing/1")
    awaitTransaction("GET /person/{id}", traceId)
  }

  @Test
  fun `unsampled trace sends no transactions`() {
    val traceId = IchnoisCoreId()
    // An id only this request uses, because a mishandled header could start a trace of its own.
    val id = Random.nextLong(2, Long.MAX_VALUE)
    val status =
      get(
        "/tracing/$id",
        ichnoisTrace = "$traceId-424cffc8f94feeee-0",
        baggage =
          "ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=false,ichnois-trace_id=$traceId,ichnois-transaction=HTTP%20GET",
      )
    assertThat(status).isEqualTo(500)

    server.assertNoEnvelope { isForRequest(it, id) }
  }

  @Test
  fun `sample_rand generated for baggage without one is propagated`() {
    val traceId = IchnoisCoreId()
    val status =
      get(
        "/tracing/1",
        ichnoisTrace = "$traceId-424cffc8f94feeee-1",
        baggage =
          "ichnois-public_key=test-public-key,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=$traceId,ichnois-transaction=HTTP%20GET",
      )
    assertThat(status).isEqualTo(500)

    val incoming = awaitTransaction("GET /tracing/{id}", traceId).header.traceContext!!
    val outgoing = awaitTransaction("GET /person/{id}", traceId).header.traceContext!!
    assertThat(incoming.traceId).isEqualTo(traceId)
    assertThat(outgoing.traceId).isEqualTo(traceId)
    assertThat(incoming.sampleRand).isNotNull()
    assertThat(outgoing.sampleRand).isEqualTo(incoming.sampleRand)
  }

  @Test
  fun `deferred sampling decision updates the propagated sample_rate`() {
    val traceId = IchnoisCoreId()
    val status =
      get(
        "/tracing/1",
        ichnoisTrace = "$traceId-424cffc8f94feeee",
        baggage =
          "ichnois-public_key=test-public-key,ichnois-sample_rate=0.5,ichnois-trace_id=$traceId,ichnois-transaction=HTTP%20GET",
      )
    assertThat(status).isEqualTo(500)

    val incoming = awaitTransaction("GET /tracing/{id}", traceId).header.traceContext!!
    val outgoing = awaitTransaction("GET /person/{id}", traceId).header.traceContext!!
    assertThat(incoming.traceId).isEqualTo(traceId)
    assertThat(outgoing.traceId).isEqualTo(traceId)
    assertThat(incoming.sampleRate).isNotEqualTo("0.5")
    assertThat(outgoing.sampleRate).isEqualTo(incoming.sampleRate)
  }

  @Test
  fun `sampled trace continues through an outgoing POST`() {
    val traceId = IchnoisCoreId()
    val headers =
      traceHeaders(
        ichnoisTrace = "$traceId-424cffc8f94feeee-1",
        baggage =
          "ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=$traceId,ichnois-transaction=HTTP%20GET",
      )
    val response =
      restTemplate.postForEntity(
        "/tracing/",
        HttpEntity(Person("firstA", "lastB"), headers),
        Person::class.java,
      )
    assertThat(response.statusCode.value()).isEqualTo(200)
    assertThat(response.body!!.firstName).isEqualTo("firstA")
    assertThat(response.body!!.lastName).isEqualTo("lastB")

    awaitTransaction("POST /tracing/", traceId)
    awaitTransaction("POST /person/", traceId)
  }

  private fun get(path: String, ichnoisTrace: String, baggage: String): Int =
    restTemplate
      .exchange(
        path,
        HttpMethod.GET,
        HttpEntity<Void>(traceHeaders(ichnoisTrace, baggage)),
        String::class.java,
      )
      .statusCode
      .value()

  private fun traceHeaders(ichnoisTrace: String, baggage: String): HttpHeaders =
    HttpHeaders().apply {
      set("ichnois-trace", ichnoisTrace)
      set("baggage", baggage)
    }

  private fun awaitTransaction(name: String, traceId: IchnoisCoreId): IchnoisCoreEnvelope =
    server.awaitEnvelope {
      isTransaction(it, name, traceId)
    }

  /** Whether [envelope] is a transaction for `/tracing/[id]` or the `/person/[id]` it calls. */
  private fun isForRequest(envelope: IchnoisCoreEnvelope, id: Long): Boolean {
    val url = envelope.transaction()?.request?.url ?: return false
    return url.endsWith("/tracing/$id") || url.endsWith("/person/$id")
  }

  private fun isTransaction(envelope: IchnoisCoreEnvelope, name: String, traceId: IchnoisCoreId): Boolean {
    val transaction = envelope.transaction() ?: return false
    return transaction.transaction == name && transaction.contexts.trace?.traceId == traceId
  }
}
