package io.ichnois.spring.boot4.integrationtest

import io.ichnois.test.MockIchnoisCoreServer
import org.junit.Before
import org.junit.runner.RunWith
import org.springframework.boot.restclient.RestTemplateBuilder
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit4.SpringRunner

/**
 * Drives [IntegrationTestApplication] over HTTP and asserts what reaches [server].
 *
 * Subclasses share one application context, one server and one Kafka broker, so each starts once
 * per suite. Envelopes that arrive late can outlive [server]'s reset, so tests match on values only
 * they produce.
 */
@RunWith(SpringRunner::class)
// An in-JVM KRaft broker, so no Docker is needed. As part of the context, it starts before the
// app's Kafka listener connects and stops after it.
@EmbeddedKafka(topics = [KAFKA_TOPIC], bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@SpringBootTest(
  classes = [IntegrationTestApplication::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties =
    [
      "ichnois.traces-sample-rate=1.0",
      "ichnois.clients.ok-http-enabled=true",
      "ichnois.enable-cache-tracing=true",
      "ichnois.enable-queue-tracing=true",
      "ichnois.logs.enabled=true",
      "ichnois.profile-session-sample-rate=1.0",
      "ichnois.profile-lifecycle=TRACE",
      "ichnois.profiling-traces-dir-path=\${java.io.tmpdir}/ichnois-integration-test/profiling-traces",
      "spring.datasource.url=jdbc:p6spy:hsqldb:mem:testdb",
      "spring.datasource.driver-class-name=com.p6spy.engine.spy.P6SpyDriver",
      "spring.kafka.consumer.auto-offset-reset=earliest",
    ],
)
abstract class IntegrationTest {
  @LocalServerPort private var port = 0

  // The context's TestRestTemplate comes from the RestTemplateBuilder that IchnoisCore customizes, so
  // it would add trace headers of its own to the requests tests send. Tests use a plain one.
  val restTemplate: TestRestTemplate by lazy {
    TestRestTemplate(RestTemplateBuilder().rootUri("http://localhost:$port"))
  }

  @Before
  fun resetServer() {
    server.reset()
  }

  companion object {
    val server = MockIchnoisCoreServer()

    @JvmStatic
    @DynamicPropertySource
    fun ichnoisDsn(registry: DynamicPropertyRegistry) {
      registry.add("ichnois.dsn") { server.dsn }
    }
  }
}
