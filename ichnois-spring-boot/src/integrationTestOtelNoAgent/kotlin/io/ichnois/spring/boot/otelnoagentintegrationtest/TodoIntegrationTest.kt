package io.ichnois.spring.boot.otelnoagentintegrationtest

import com.google.common.truth.Truth.assertThat
import io.ichnois.protocol.IchnoisCoreTransaction
import io.ichnois.test.hasSpanWithOp
import io.ichnois.test.hasSpanWithOpAndDescription
import io.ichnois.test.transaction
import kotlin.test.Test

class TodoIntegrationTest : IntegrationTest() {
  @Test
  fun `RestTemplate request is traced inside OpenTelemetry and IchnoisCore spans`() {
    val transaction = fetchTodo("/todo/1", "GET /todo/{id}")
    assertThat(transaction.hasSpanWithOp("todoSpanOtelApi")).isTrue()
    assertThat(transaction.hasSpanWithOp("todoSpanIchnoisCoreApi")).isTrue()
  }

  @Test
  fun `WebClient request is traced`() {
    fetchTodo("/todo-webclient/1", "GET /todo-webclient/{id}")
  }

  /** Fetches a todo through [path] and returns the request's transaction, with its client span. */
  private fun fetchTodo(path: String, transactionName: String): IchnoisCoreTransaction {
    val response = restTemplate.getForEntity(path, Todo::class.java)
    assertThat(response.statusCode.value()).isEqualTo(200)
    assertThat(response.body!!.title).isEqualTo("delectus aut autem")

    val transaction =
      server.awaitEnvelope { it.transaction()?.transaction == transactionName }.transaction()!!
    assertThat(
        transaction.hasSpanWithOpAndDescription(
          "http.client",
          "GET ${restTemplate.rootUri}/stub/todos/1",
        )
      )
      .isTrue()
    return transaction
  }
}
