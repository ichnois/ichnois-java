package io.ichnois.systemtest

import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.systemtest.util.TestHelper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.Before

class DistributedTracingSystemTest {
  lateinit var testHelper: TestHelper

  @Before
  fun setup() {
    testHelper = TestHelper("http://localhost:8080")
    testHelper.reset()
  }

  @Test
  fun `get person distributed tracing`() {
    val traceId = IchnoisCoreId()
    val restClient = testHelper.restClient
    restClient.getPersonDistributedTracing(
      1L,
      mapOf(
        "ichnois-trace" to "$traceId-424cffc8f94feeee-1",
        "baggage" to
          "ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=$traceId,ichnois-transaction=HTTP%20GET",
      ),
    )
    assertEquals(500, restClient.lastKnownStatusCode)

    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      transaction.transaction == "GET /tracing/{id}" &&
        testHelper.doesTransactionHaveTraceId(transaction, traceId.toString())
    }

    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      transaction.transaction == "GET /person/{id}" &&
        testHelper.doesTransactionHaveTraceId(transaction, traceId.toString())
    }
  }

  @Test
  fun `get person distributed tracing with sampled false`() {
    val traceId = IchnoisCoreId()
    val restClient = testHelper.restClient
    restClient.getPersonDistributedTracing(
      1L,
      mapOf(
        "ichnois-trace" to "$traceId-424cffc8f94feeee-0",
        "baggage" to
          "ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=false,ichnois-trace_id=$traceId,ichnois-transaction=HTTP%20GET",
      ),
    )
    assertEquals(500, restClient.lastKnownStatusCode)

    testHelper.ensureNoTransactionReceived { transaction, envelopeHeader ->
      transaction.transaction == "GET /tracing/{id}"
    }

    testHelper.ensureNoTransactionReceived { transaction, envelopeHeader ->
      transaction.transaction == "GET /person/{id}"
    }
  }

  @Test
  fun `get person distributed tracing without sample_rand`() {
    val traceId = IchnoisCoreId()
    val restClient = testHelper.restClient
    restClient.getPersonDistributedTracing(
      1L,
      mapOf(
        "ichnois-trace" to "$traceId-424cffc8f94feeee-1",
        "baggage" to
          "ichnois-public_key=test-public-key,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=$traceId,ichnois-transaction=HTTP%20GET",
      ),
    )
    assertEquals(500, restClient.lastKnownStatusCode)

    var sampleRand1: String? = null
    var sampleRand2: String? = null

    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      val matches =
        transaction.transaction == "GET /tracing/{id}" &&
          envelopeHeader.traceContext!!.traceId == traceId &&
          testHelper.doesTransactionHaveTraceId(transaction, traceId.toString())

      if (matches) {
        testHelper.logObject(envelopeHeader)
        testHelper.logObject(transaction)
        sampleRand1 = envelopeHeader.traceContext?.sampleRand
      }

      matches
    }

    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      val matches =
        transaction.transaction == "GET /person/{id}" &&
          envelopeHeader.traceContext!!.traceId == traceId &&
          testHelper.doesTransactionHaveTraceId(transaction, traceId.toString())

      if (matches) {
        testHelper.logObject(envelopeHeader)
        testHelper.logObject(transaction)
        sampleRand2 = envelopeHeader.traceContext?.sampleRand
      }

      matches
    }

    assertEquals(sampleRand1, sampleRand2)
  }

  @Test
  fun `get person distributed tracing updates sample_rate on deferred decision`() {
    val traceId = IchnoisCoreId()
    val restClient = testHelper.restClient
    restClient.getPersonDistributedTracing(
      1L,
      mapOf(
        "ichnois-trace" to "$traceId-424cffc8f94feeee",
        "baggage" to
          "ichnois-public_key=test-public-key,ichnois-sample_rate=0.5,ichnois-trace_id=$traceId,ichnois-transaction=HTTP%20GET",
      ),
    )
    assertEquals(500, restClient.lastKnownStatusCode)

    var sampleRate1: String? = null
    var sampleRate2: String? = null

    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      val matches =
        transaction.transaction == "GET /tracing/{id}" &&
          envelopeHeader.traceContext!!.traceId == traceId &&
          testHelper.doesTransactionHaveTraceId(transaction, traceId.toString())

      if (matches) {
        testHelper.logObject(envelopeHeader)
        testHelper.logObject(transaction)
        sampleRate1 = envelopeHeader.traceContext?.sampleRate
      }

      matches
    }

    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      val matches =
        transaction.transaction == "GET /person/{id}" &&
          envelopeHeader.traceContext!!.traceId == traceId &&
          testHelper.doesTransactionHaveTraceId(transaction, traceId.toString())

      if (matches) {
        testHelper.logObject(envelopeHeader)
        testHelper.logObject(transaction)
        sampleRate2 = envelopeHeader.traceContext?.sampleRate
      }

      matches
    }

    assertEquals(sampleRate1, sampleRate2)
    assertNotEquals(sampleRate1, "0.5")
  }

  @Test
  fun `create person distributed tracing`() {
    val traceId = IchnoisCoreId()
    val restClient = testHelper.restClient
    val person = Person("firstA", "lastB")
    val returnedPerson =
      restClient.createPersonDistributedTracing(
        person,
        mapOf(
          "ichnois-trace" to "$traceId-424cffc8f94feeee-1",
          "baggage" to
            "ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=$traceId,ichnois-transaction=HTTP%20GET",
        ),
      )
    assertEquals(200, restClient.lastKnownStatusCode)

    assertEquals(person.firstName, returnedPerson!!.firstName)
    assertEquals(person.lastName, returnedPerson!!.lastName)

    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      transaction.transaction == "POST /tracing/" &&
        testHelper.doesTransactionHaveTraceId(transaction, traceId.toString())
    }

    testHelper.ensureTransactionReceived { transaction, envelopeHeader ->
      transaction.transaction == "POST /person/" &&
        testHelper.doesTransactionHaveTraceId(transaction, traceId.toString())
    }
  }
}
