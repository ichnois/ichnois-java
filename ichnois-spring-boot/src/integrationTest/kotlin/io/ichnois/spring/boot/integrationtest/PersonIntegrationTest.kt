package io.ichnois.spring.boot.integrationtest

import com.google.common.truth.Truth.assertThat
import io.ichnois.IchnoisCore
import io.ichnois.protocol.FeatureFlag
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

class PersonIntegrationTest : IntegrationTest() {

  @Test
  fun `failed request reports errors, the transaction and logs`() {
    val response = restTemplate.getForEntity("/person/1", String::class.java)
    assertThat(response.statusCode.value()).isEqualTo(500)
    IchnoisCore.flush(5000)

    val logged =
      server.awaitEnvelope { it.event()?.message?.formatted == "Trying person with id=1" }.event()!!
    assertThat(logged.hasFlag("my-feature-flag", true)).isTrue()

    val thrown =
      server
        .awaitEnvelope {
          it.event()?.exceptions?.any { e -> e.value == "Something went wrong [id=1]" } == true
        }
        .event()!!
    assertThat(thrown.hasFlag("my-feature-flag", true)).isTrue()

    val transaction =
      server.awaitEnvelope { it.transaction()?.transaction == "GET /person/{id}" }.transaction()!!
    assertThat(
        transaction.hasOp(
          "http.server",
          FeatureFlag("flag.evaluation.transaction-feature-flag", true),
        )
      )
      .isTrue()
    assertThat(
        transaction.hasSpanWithOp(
          "spanCreatedThroughIchnoisCoreApi",
          FeatureFlag("flag.evaluation.my-feature-flag", true),
        )
      )
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
  fun `creating a person traces the service method and the query`() {
    val response =
      restTemplate.postForEntity("/person/", Person("firstA", "lastB"), Person::class.java)
    assertThat(response.statusCode.value()).isEqualTo(200)
    assertThat(response.body!!.firstName).isEqualTo("firstA")
    assertThat(response.body!!.lastName).isEqualTo("lastB")

    val transaction =
      server.awaitEnvelope { it.transaction()?.transaction == "POST /person/" }.transaction()!!
    assertThat(transaction.hasSpanWithOp("PersonService.create")).isTrue()
    assertThat(
        transaction.hasSpanWithOpAndDescription(
          "db.query",
          "insert into person (firstName, lastName) values (?, ?)",
        )
      )
      .isTrue()
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
}
