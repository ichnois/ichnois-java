package io.ichnois.systemtest

import io.ichnois.IchnoisCoreLevel
import io.ichnois.systemtest.util.TestHelper
import java.util.concurrent.TimeUnit
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class ConsoleApplicationSystemTest {
  lateinit var testHelper: TestHelper

  @Before
  fun setup() {
    testHelper = TestHelper("http://localhost:8000")
    testHelper.reset()
  }

  @Test
  fun `log4j2 application sends expected events when run as JAR`() {
    val jarFile = testHelper.findJar("ichnois-samples-log4j2")
    val process =
      testHelper.launch(
        jarFile,
        mapOf(
          "ICHNOIS_DSN" to testHelper.dsn,
          "ICHNOIS_TRACES_SAMPLE_RATE" to "1.0",
          "ICHNOIS_ENABLE_PRETTY_SERIALIZATION_OUTPUT" to "false",
          "ICHNOIS_DEBUG" to "true",
        ),
      )

    process.waitFor(30, TimeUnit.SECONDS)
    Assert.assertEquals(0, process.exitValue())

    // Verify that we received the expected events
    verifyExpectedEvents()
  }

  private fun verifyExpectedEvents() {
    // Verify we received the RuntimeException
    testHelper.ensureErrorReceived { event ->
      event.exceptions?.any { ex ->
        ex.type == "RuntimeException" && ex.value == "Invalid productId=445"
      } == true &&
        event.message?.formatted == "Something went wrong" &&
        event.level?.name == "ERROR"
    }

    testHelper.ensureErrorReceived { event ->
      event.message?.message == "Important warning" &&
        testHelper.doesEventHaveFlag(event, "my-feature-flag", true)
    }

    testHelper.ensureErrorReceived { event ->
      event.breadcrumbs?.firstOrNull {
        it.message == "Hello IchnoisCore!" && it.level == IchnoisCoreLevel.DEBUG
      } != null
    }

    testHelper.ensureErrorReceived { event ->
      event.breadcrumbs?.firstOrNull {
        it.message == "User has made a purchase of product: 445" && it.level == IchnoisCoreLevel.INFO
      } != null
    }

    testHelper.ensureLogsReceived { logs, _ ->
      testHelper.doesContainLogWithBody(logs, "User has made a purchase of product: 445") &&
        testHelper.doesContainLogWithBody(logs, "Something went wrong") &&
        testHelper.doesLogWithBodyHaveAttribute(
          logs,
          "Something went wrong",
          "user.type",
          "admin",
        ) &&
        testHelper.doesLogWithBodyHaveAttribute(
          logs,
          "Something went wrong",
          "feature.version",
          2,
        ) &&
        testHelper.doesLogWithBodyHaveAttribute(logs, "Something went wrong", "debug.enabled", true)
    }
  }
}
