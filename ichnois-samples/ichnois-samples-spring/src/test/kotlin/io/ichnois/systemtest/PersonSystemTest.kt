package io.ichnois.systemtest

import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.systemtest.util.TestHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Before

class PersonSystemTest {
  lateinit var testHelper: TestHelper

  @Before
  fun setup() {
    testHelper = TestHelper("http://localhost:8080/ichnois-samples-spring-0.0.1-SNAPSHOT")
    testHelper.reset()
  }

  @Test
  fun `get person fails`() {
    val restClient = testHelper.restClient
    restClient.getPerson(11L)
    assertEquals(500, restClient.lastKnownStatusCode)

    testHelper.ensureErrorReceived { event ->
      testHelper.doesEventHaveExceptionMessage(event, "Something went wrong [id=11]") &&
        testHelper.doesEventHaveFlag(event, "my-feature-flag", true)
    }

    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      testHelper.doesTransactionHaveOp(transaction, "http.server")
    }

    Thread.sleep(10000)

    testHelper.ensureLogsReceived { logs, envelopeHeader ->
      testHelper.doesContainLogWithBody(logs, "warn IchnoisCore logging") &&
        testHelper.doesContainLogWithBody(logs, "error IchnoisCore logging") &&
        testHelper.doesContainLogWithBody(logs, "hello there world!") &&
        testHelper.doesLogWithBodyHaveAttribute(
          logs,
          "warn IchnoisCore logging",
          "user.type",
          "admin",
        ) &&
        testHelper.doesLogWithBodyHaveAttribute(
          logs,
          "warn IchnoisCore logging",
          "feature.version",
          2,
        ) &&
        testHelper.doesLogWithBodyHaveAttribute(logs, "warn IchnoisCore logging", "debug.enabled", true)
    }
  }

  @Test
  fun `create person works`() {
    val restClient = testHelper.restClient
    val person = Person("firstA", "lastB")
    val returnedPerson = restClient.createPerson(person)
    assertEquals(200, restClient.lastKnownStatusCode)

    assertEquals(person.firstName, returnedPerson!!.firstName)
    assertEquals(person.lastName, returnedPerson!!.lastName)
  }

  @Test
  fun `create person starts a profile linked to the transaction`() {
    var profilerId: IchnoisCoreId? = null
    val restClient = testHelper.restClient
    val person = Person("firstA", "lastB")
    val returnedPerson = restClient.createPerson(person)
    assertEquals(200, restClient.lastKnownStatusCode)

    assertEquals(person.firstName, returnedPerson!!.firstName)
    assertEquals(person.lastName, returnedPerson!!.lastName)

    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      profilerId = transaction.contexts.profile?.profilerId
      transaction.transaction == "POST /person/"
    }
    testHelper.ensureProfileChunkReceived { profileChunk, envelopeHeader ->
      profileChunk.profilerId == profilerId
    }
  }
}
