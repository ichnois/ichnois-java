package io.ichnois.spring.boot.webfluxintegrationtest

import io.ichnois.test.MockIchnoisCoreServer
import org.junit.Before
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit4.SpringRunner

/**
 * Drives [WebfluxIntegrationTestApplication], a reactive app, over HTTP and asserts what reaches
 * [server].
 *
 * Subclasses share one application context and one server, so the app starts once per suite.
 * Envelopes that arrive late can outlive [server]'s reset, so tests match on values only they
 * produce.
 */
@RunWith(SpringRunner::class)
@SpringBootTest(
  classes = [WebfluxIntegrationTestApplication::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties =
    [
      "ichnois.traces-sample-rate=1.0",
      "ichnois.logs.enabled=true",
      "ichnois.profile-session-sample-rate=1.0",
      "ichnois.profile-lifecycle=TRACE",
      "ichnois.profiling-traces-dir-path=\${java.io.tmpdir}/ichnois-integration-test/profiling-traces",
    ],
)
abstract class WebfluxIntegrationTest {
  @Value("\${local.server.port}") private var port = 0

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
