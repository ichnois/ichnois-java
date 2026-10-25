package io.ichnois.logback

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import com.google.common.truth.Truth.assertThat
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreLevel
import io.ichnois.test.MockIchnoisCoreServer
import io.ichnois.test.event
import io.ichnois.test.hasFlag
import io.ichnois.test.logs
import kotlin.test.AfterTest
import kotlin.test.Test
import org.slf4j.MDC

class IchnoisCoreAppenderIntegrationTest {
  private val server = MockIchnoisCoreServer()
  private val loggerContext = LoggerContext()

  @AfterTest
  fun teardown() {
    loggerContext.stop()
    IchnoisCore.close()
    server.close()
    MDC.clear()
  }

  @Test
  fun `appender configured from XML sends events, breadcrumbs and logs`() {
    loggerContext.putProperty("dsn", server.dsn)
    JoranConfigurator()
      .apply { context = loggerContext }
      .doConfigure(javaClass.getResource("/ichnois-appender-integration-test.xml"))
    val logger = loggerContext.getLogger(IchnoisCoreAppenderIntegrationTest::class.java)

    logger.debug("Hello IchnoisCore!")
    MDC.put("userId", "user-1")
    IchnoisCore.setAttribute("user.type", "admin")
    IchnoisCore.setAttribute("feature.version", 2)
    IchnoisCore.setAttribute("debug.enabled", true)
    IchnoisCore.addFeatureFlag("my-feature-flag", true)
    logger.warn("important warning")
    logger.info("User has made a purchase of product: {}", 445)
    logger.error("Something went wrong", RuntimeException("Invalid productId=445"))
    IchnoisCore.flush(5000)

    val warning = server.awaitEnvelope { it.event()?.level == IchnoisCoreLevel.WARNING }.event()!!
    assertThat(warning.message!!.message).isEqualTo("important warning")
    assertThat(warning.tags).containsEntry("userId", "user-1")
    assertThat(warning.hasFlag("my-feature-flag", true)).isTrue()

    val error = server.awaitEnvelope { it.event()?.level == IchnoisCoreLevel.ERROR }.event()!!
    assertThat(error.message!!.formatted).isEqualTo("Something went wrong")
    assertThat(error.exceptions!!.map { it.type to it.value })
      .contains("RuntimeException" to "Invalid productId=445")
    assertThat(error.breadcrumbs!!.map { it.message to it.level })
      .containsAtLeast(
        "Hello IchnoisCore!" to IchnoisCoreLevel.DEBUG,
        "User has made a purchase of product: 445" to IchnoisCoreLevel.INFO,
      )

    val logs = server.awaitEnvelope { it.logs() != null }.logs()!!
    assertThat(logs.items.map { it.body }).contains("User has made a purchase of product: 445")
    val failureLog = logs.items.single { it.body == "Something went wrong" }
    assertThat(failureLog.attributes!!.mapValues { it.value.value })
      .containsAtLeast("user.type", "admin", "feature.version", 2, "debug.enabled", true)
  }
}
