package io.ichnois.spring.boot.jakarta.otelagentintegrationtest

import io.ichnois.test.ChildJvmApp
import io.ichnois.test.MockIchnoisCoreServer
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker

/**
 * The agent fixture app, running in a child JVM with the built OpenTelemetry agent attached, and
 * the server it reports to. The app takes seconds to start, so each test JVM starts it once and
 * stops it when the JVM exits.
 *
 * The test task decides what initializes IchnoisCore: the agent or, with `ICHNOIS_AUTO_INIT=false`, the
 * Spring Boot starter.
 */
object AgentApp {
  val autoInit = System.getProperty("ichnois.test.agentAutoInit").toBoolean()

  val server = MockIchnoisCoreServer()

  // An in-JVM KRaft broker with the fixture's KAFKA_TOPIC, so no Docker is needed.
  private val kafka = EmbeddedKafkaKraftBroker(1, 1, "ichnois-topic").apply { afterPropertiesSet() }

  private val app =
    ChildJvmApp.start(
      mainClass = "io.ichnois.spring.boot.jakarta.agentfixture.AgentFixtureApplicationKt",
      classpath = System.getProperty("ichnois.test.appClasspath"),
      jvmArgs = listOf("-javaagent:${System.getProperty("ichnois.test.agentJar")}"),
      // Environment variables, because the agent doesn't read Spring Boot's properties.
      env =
        mapOf(
          "ICHNOIS_AUTO_INIT" to autoInit.toString(),
          "ICHNOIS_DSN" to server.dsn,
          "ICHNOIS_TRACES_SAMPLE_RATE" to "1.0",
          "ICHNOIS_LOGS_ENABLED" to "true",
          "ICHNOIS_ENABLE_CACHE_TRACING" to "true",
          "ICHNOIS_ENABLE_QUEUE_TRACING" to "true",
          "ICHNOIS_PROFILE_SESSION_SAMPLE_RATE" to "1.0",
          "ICHNOIS_PROFILE_LIFECYCLE" to "TRACE",
          "ICHNOIS_PROFILING_TRACES_DIR_PATH" to
            "${System.getProperty("java.io.tmpdir")}/ichnois-integration-test/profiling-traces",
          "OTEL_TRACES_EXPORTER" to "none",
          "OTEL_METRICS_EXPORTER" to "none",
          "OTEL_LOGS_EXPORTER" to "none",
        ),
      args = listOf("--server.port=0", "--spring.kafka.bootstrap-servers=${kafka.brokersAsString}"),
    )

  init {
    Runtime.getRuntime()
      .addShutdownHook(
        Thread {
          app.close()
          kafka.destroy()
          server.close()
        }
      )
  }

  // The agent instruments classes as they load, which on a busy CI runner has taken over a minute.
  val restTemplate =
    TestRestTemplate(
      RestTemplateBuilder().rootUri("http://localhost:${app.awaitPort(timeoutSeconds = 180)}")
    )
}
