package io.ichnois.spring.boot.webfluxintegrationtest

import com.google.common.truth.Truth.assertThat
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreMetricsEvents
import io.ichnois.test.hasMetric
import io.ichnois.test.hasMetricWithAttribute
import io.ichnois.test.metrics
import kotlin.test.Test

class MetricsWebfluxIntegrationTest : WebfluxIntegrationTest() {

  @Test
  fun `count metric carries the scope's attributes`() {
    assertThat(restTemplate.getForObject("/metric/count", String::class.java))
      .isEqualTo("count metric increased")

    val metrics = awaitMetric("countMetric")
    assertThat(metrics.hasMetric("countMetric", "counter", 1.0)).isTrue()
    assertThat(metrics.hasMetricWithAttribute("countMetric", "user.type", "admin")).isTrue()
    assertThat(metrics.hasMetricWithAttribute("countMetric", "feature.version", 2)).isTrue()
  }

  @Test
  fun `gauge metric`() {
    assertThat(restTemplate.getForObject("/metric/gauge/14", String::class.java))
      .isEqualTo("gauge metric tracked")

    assertThat(awaitMetric("memory.free").hasMetric("memory.free", "gauge", 14.0)).isTrue()
  }

  @Test
  fun `distribution metric`() {
    assertThat(restTemplate.getForObject("/metric/distribution/23", String::class.java))
      .isEqualTo("distribution metric tracked")

    assertThat(
        awaitMetric("distributionMetric").hasMetric("distributionMetric", "distribution", 23.0)
      )
      .isTrue()
  }

  /** Flushes first because metrics are batched before they're sent. */
  private fun awaitMetric(name: String): IchnoisCoreMetricsEvents {
    IchnoisCore.flush(5000)
    return server
      .awaitEnvelope { it.metrics()?.items?.any { metric -> metric.name == name } == true }
      .metrics()!!
  }
}
