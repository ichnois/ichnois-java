package io.ichnois.test

import com.google.common.truth.Truth.assertThat
import io.ichnois.IchnoisCore
import io.ichnois.protocol.FeatureFlag
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class EnvelopeMatchersTest {
  private val server = MockIchnoisCoreServer()

  @BeforeTest
  fun setup() {
    initForTest {
      it.dsn = server.dsn
      it.tracesSampleRate = 1.0
      it.logs.isEnabled = true
    }
  }

  @AfterTest
  fun teardown() {
    IchnoisCore.close()
    server.close()
  }

  @Test
  fun `matches a transaction by op, trace ID and span`() {
    val transaction = IchnoisCore.startTransaction("name", "http.server")
    transaction.startChild("db.query", "select 1").apply {
      setData("flag.evaluation.my-flag", true)
      finish()
    }
    transaction.finish()
    IchnoisCore.flush(5000)

    val received = server.awaitEnvelope { it.transaction() != null }.transaction()!!

    assertThat(received.hasOp("http.server")).isTrue()
    assertThat(received.hasOp("other")).isFalse()
    assertThat(received.hasTraceId(transaction.spanContext.traceId.toString())).isTrue()
    assertThat(received.hasSpanWithOpAndDescription("db.query", "select 1")).isTrue()
    assertThat(received.hasSpanWithOp("db.query", FeatureFlag("flag.evaluation.my-flag", true)))
      .isTrue()
    assertThat(received.hasSpanWithOp("db.query", FeatureFlag("flag.evaluation.my-flag", false)))
      .isFalse()
    assertThat(received.hasSpanWithOp("db.query", noFeatureFlags = true)).isFalse()
  }

  @Test
  fun `matches an event by exception message and feature flag`() {
    IchnoisCore.addFeatureFlag("my-flag", true)
    IchnoisCore.captureException(RuntimeException("boom"))
    IchnoisCore.flush(5000)

    val event = server.awaitEnvelope { it.event() != null }.event()!!

    assertThat(event.hasExceptionMessage("boom")).isTrue()
    assertThat(event.hasExceptionMessage("other")).isFalse()
    assertThat(event.hasFlag("my-flag", true)).isTrue()
    assertThat(event.hasFlag("my-flag", false)).isFalse()
  }

  @Test
  fun `matches logs by body and attribute`() {
    IchnoisCore.setAttribute("user.type", "admin")
    IchnoisCore.logger().info("hello")
    IchnoisCore.flush(5000)

    val logs = server.awaitEnvelope { it.logs() != null }.logs()!!

    assertThat(logs.hasLogWithBody("hello")).isTrue()
    assertThat(logs.hasLogWithBody("other")).isFalse()
    assertThat(logs.hasLogWithAttribute("hello", "user.type", "admin")).isTrue()
    assertThat(logs.hasLogWithAttribute("hello", "user.type", "guest")).isFalse()
  }

  @Test
  fun `matches metrics by value and attribute`() {
    IchnoisCore.setAttribute("user.type", "admin")
    IchnoisCore.metrics().gauge("my.gauge", 5.0)
    IchnoisCore.flush(5000)

    val metrics = server.awaitEnvelope { it.metrics() != null }.metrics()!!

    assertThat(metrics.hasMetric("my.gauge", "gauge", 5.0)).isTrue()
    assertThat(metrics.hasMetric("my.gauge", "gauge", 6.0)).isFalse()
    assertThat(metrics.hasMetricWithAttribute("my.gauge", "user.type", "admin")).isTrue()
    assertThat(metrics.hasMetricWithAttribute("my.gauge", "user.type", "guest")).isFalse()
  }
}
