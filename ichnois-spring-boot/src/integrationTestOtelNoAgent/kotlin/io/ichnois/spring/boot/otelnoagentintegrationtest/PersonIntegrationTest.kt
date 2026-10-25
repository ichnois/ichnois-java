package io.ichnois.spring.boot.otelnoagentintegrationtest

import com.google.common.truth.Truth.assertThat
import io.ichnois.IchnoisCore
import io.ichnois.protocol.FeatureFlag
import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.protocol.IchnoisCoreTransaction
import io.ichnois.test.event
import io.ichnois.test.hasFlag
import io.ichnois.test.hasLogWithAttribute
import io.ichnois.test.hasLogWithBody
import io.ichnois.test.hasOp
import io.ichnois.test.hasSpanWithOp
import io.ichnois.test.hasSpanWithOpAndDescription
import io.ichnois.test.logs
import io.ichnois.test.profileChunk
import io.ichnois.test.transaction
import kotlin.test.Test
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders

class PersonIntegrationTest : IntegrationTest() {

  @Test
  fun `failed request reports errors, nested spans with their flags, and logs`() {
    val response = restTemplate.getForEntity("/person/1", String::class.java)
    assertThat(response.statusCode.value()).isEqualTo(500)
    IchnoisCore.flush(5000)

    val logged =
      server.awaitEnvelope { it.event()?.message?.formatted == "Trying person with id=1" }.event()!!
    assertThat(logged.hasFlag("inner-feature-flag", true)).isTrue()

    val thrown =
      server
        .awaitEnvelope {
          it.event()?.exceptions?.any { e -> e.value == "Something went wrong [id=1]" } == true
        }
        .event()!!
    assertThat(thrown.hasFlag("inner-feature-flag", true)).isTrue()

    val transaction =
      server.awaitEnvelope { it.transaction()?.transaction == "GET /person/{id}" }.transaction()!!
    assertThat(transaction.hasOp("http.server")).isTrue()
    assertThat(
        transaction.hasSpanWithOp(
          "personSpanThroughOtelAnnotation",
          FeatureFlag("flag.evaluation.outer-feature-flag", true),
        )
      )
      .isTrue()
    assertThat(
        transaction.hasSpanWithOp(
          "spanCreatedThroughOtelApi",
          FeatureFlag("flag.evaluation.inner-feature-flag", true),
        )
      )
      .isTrue()
    assertThat(transaction.hasSpanWithOp("spanCreatedThroughIchnoisCoreApi", noFeatureFlags = true))
      .isTrue()

    val logs =
      server
        .awaitEnvelope {
          it.logs()?.items?.any { log -> log.body == "warn IchnoisCore logging" } == true
        }
        .logs()!!
    assertThat(logs.hasLogWithBody("error IchnoisCore logging")).isTrue()
    assertThat(logs.hasLogWithBody("hello there world!")).isTrue()
    assertThat(logs.hasLogWithAttribute("warn IchnoisCore logging", "user.type", "admin")).isTrue()
    assertThat(logs.hasLogWithAttribute("warn IchnoisCore logging", "feature.version", 2)).isTrue()
    assertThat(logs.hasLogWithAttribute("warn IchnoisCore logging", "debug.enabled", true)).isTrue()
  }

  @Test
  fun `creating a person traces the OpenTelemetry, IchnoisCore and database spans`() {
    assertCreatingPersonIsTraced(HttpHeaders())
  }

  @Test
  fun `creating a person starts a profile linked to the transaction`() {
    val response =
      restTemplate.postForEntity("/person/", Person("firstA", "lastB"), Person::class.java)
    assertThat(response.statusCode.value()).isEqualTo(200)

    val transaction =
      server.awaitEnvelope { it.transaction()?.transaction == "POST /person/" }.transaction()!!
    val profilerId = transaction.contexts.profile?.profilerId
    assertThat(profilerId).isNotNull()
    server.awaitEnvelope(timeoutMillis = 30_000) { it.profileChunk()?.profilerId == profilerId }
  }

  @Test
  fun `incoming trace without a sampled flag is traced`() {
    val traceId = IchnoisCoreId()
    assertCreatingPersonIsTraced(traceHeaders("$traceId-424cffc8f94feeee", traceId), traceId)
  }

  @Test
  fun `incoming sampled trace is traced`() {
    val traceId = IchnoisCoreId()
    assertCreatingPersonIsTraced(traceHeaders("$traceId-424cffc8f94feeee-1", traceId), traceId)
  }

  private fun assertCreatingPersonIsTraced(headers: HttpHeaders, traceId: IchnoisCoreId? = null) {
    val response =
      restTemplate.postForEntity(
        "/person/",
        HttpEntity(Person("firstA", "lastB"), headers),
        Person::class.java,
      )
    assertThat(response.statusCode.value()).isEqualTo(200)
    assertThat(response.body!!.firstName).isEqualTo("firstA")
    assertThat(response.body!!.lastName).isEqualTo("lastB")

    val transaction = awaitCreateTransaction(traceId)
    assertThat(transaction.hasSpanWithOp("spanCreatedThroughOtelApi")).isTrue()
    assertThat(transaction.hasSpanWithOp("spanCreatedThroughIchnoisCoreApi")).isTrue()
    assertThat(
        transaction.hasSpanWithOpAndDescription(
          "db",
          "insert into person (firstName, lastName) values (?, ?)",
        )
      )
      .isTrue()
  }

  private fun awaitCreateTransaction(traceId: IchnoisCoreId?): IchnoisCoreTransaction =
    server
      .awaitEnvelope {
        it.transaction()?.let { t ->
          t.transaction == "POST /person/" &&
            (traceId == null || t.contexts.trace?.traceId == traceId)
        } == true
      }
      .transaction()!!

  private fun traceHeaders(ichnoisTrace: String, traceId: IchnoisCoreId): HttpHeaders =
    HttpHeaders().apply {
      set("ichnois-trace", ichnoisTrace)
      set(
        "baggage",
        "ichnois-public_key=test-public-key,ichnois-sample_rate=1,ichnois-trace_id=$traceId,ichnois-transaction=HTTP%20GET",
      )
    }
}
