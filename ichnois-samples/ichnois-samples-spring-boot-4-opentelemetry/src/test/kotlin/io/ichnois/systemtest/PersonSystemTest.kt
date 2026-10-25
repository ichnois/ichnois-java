package io.ichnois.systemtest

import io.ichnois.protocol.FeatureFlag
import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.systemtest.util.TestHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Before

class PersonSystemTest {
  lateinit var testHelper: TestHelper

  @Before
  fun setup() {
    testHelper = TestHelper("http://localhost:8080")
    testHelper.reset()
  }

  @Test
  fun `get person fails`() {
    val restClient = testHelper.restClient
    restClient.getPerson(1L)
    assertEquals(500, restClient.lastKnownStatusCode)

    testHelper.ensureErrorReceived { event ->
      event.message?.formatted == "Trying person with id=1" &&
        testHelper.doesEventHaveFlag(event, "my-feature-flag", true)
    }

    testHelper.ensureErrorReceived { event ->
      testHelper.doesEventHaveExceptionMessage(event, "Something went wrong [id=1]") &&
        testHelper.doesEventHaveFlag(event, "my-feature-flag", true)
    }

    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      testHelper.doesTransactionHave(
        transaction,
        op = "http.server",
        featureFlag = FeatureFlag("flag.evaluation.transaction-feature-flag", true),
      ) &&
        testHelper.doesTransactionHaveSpanWith(
          transaction,
          op = "spanCreatedThroughOtelApi",
          featureFlag = FeatureFlag("flag.evaluation.my-feature-flag", true),
        ) &&
        testHelper.doesTransactionHaveSpanWith(transaction, op = "spanCreatedThroughIchnoisCoreApi")
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

    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      testHelper.doesTransactionContainSpanWithOp(transaction, "spanCreatedThroughOtelApi") &&
        testHelper.doesTransactionContainSpanWithOp(transaction, "spanCreatedThroughIchnoisCoreApi") &&
        testHelper.doesTransactionContainSpanWithOpAndDescription(
          transaction,
          "db",
          "insert into person (firstName, lastName) values (?, ?)",
        )
    }
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

  @Test
  fun `create person creates transaction if no sampled flag in ichnois-trace header`() {
    val restClient = testHelper.restClient
    val person = Person("firstA", "lastB")
    val returnedPerson =
      restClient.createPerson(
        person,
        mapOf(
          "ichnois-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee",
          "baggage" to
            "ichnois-public_key=test-public-key,ichnois-sample_rate=1,ichnois-trace_id=f9118105af4a2d42b4124532cd1065ff,ichnois-transaction=HTTP%20GET",
        ),
      )
    assertEquals(200, restClient.lastKnownStatusCode)

    assertEquals(person.firstName, returnedPerson!!.firstName)
    assertEquals(person.lastName, returnedPerson!!.lastName)

    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      testHelper.doesTransactionContainSpanWithOp(transaction, "spanCreatedThroughOtelApi") &&
        testHelper.doesTransactionContainSpanWithOp(transaction, "spanCreatedThroughIchnoisCoreApi") &&
        testHelper.doesTransactionContainSpanWithOpAndDescription(
          transaction,
          "db",
          "insert into person (firstName, lastName) values (?, ?)",
        )
    }
  }

  @Test
  fun `create person creates transaction if sampled true in ichnois-trace header`() {
    val restClient = testHelper.restClient
    val person = Person("firstA", "lastB")
    val returnedPerson =
      restClient.createPerson(
        person,
        mapOf(
          "ichnois-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1",
          "baggage" to
            "ichnois-public_key=test-public-key,ichnois-sample_rate=1,ichnois-trace_id=f9118105af4a2d42b4124532cd1065ff,ichnois-transaction=HTTP%20GET",
        ),
      )
    assertEquals(200, restClient.lastKnownStatusCode)

    assertEquals(person.firstName, returnedPerson!!.firstName)
    assertEquals(person.lastName, returnedPerson!!.lastName)

    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      testHelper.doesTransactionContainSpanWithOp(transaction, "spanCreatedThroughOtelApi") &&
        testHelper.doesTransactionContainSpanWithOp(transaction, "spanCreatedThroughIchnoisCoreApi") &&
        testHelper.doesTransactionContainSpanWithOpAndDescription(
          transaction,
          "db",
          "insert into person (firstName, lastName) values (?, ?)",
        )
    }
  }
}
