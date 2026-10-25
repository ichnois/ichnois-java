package io.ichnois.systemtest.util

import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import io.ichnois.JsonSerializer
import io.ichnois.ProfileChunk
import io.ichnois.IchnoisCoreEnvelope
import io.ichnois.IchnoisCoreEnvelopeHeader
import io.ichnois.IchnoisCoreEvent
import io.ichnois.IchnoisCoreLogEvents
import io.ichnois.IchnoisCoreMetricsEvents
import io.ichnois.IchnoisCoreOptions
import io.ichnois.protocol.FeatureFlag
import io.ichnois.protocol.IchnoisCoreSpan
import io.ichnois.protocol.IchnoisCoreTransaction
import io.ichnois.systemtest.graphql.GraphqlTestClient
import io.ichnois.test.event
import io.ichnois.test.hasExceptionMessage
import io.ichnois.test.hasFlag
import io.ichnois.test.hasLogWithAttribute
import io.ichnois.test.hasLogWithBody
import io.ichnois.test.hasMetric
import io.ichnois.test.hasMetricWithAttribute
import io.ichnois.test.hasOp
import io.ichnois.test.hasSpanWithOp
import io.ichnois.test.hasSpanWithOpAndDescription
import io.ichnois.test.hasTraceId
import io.ichnois.test.logs
import io.ichnois.test.metrics
import io.ichnois.test.profileChunk
import io.ichnois.test.transaction
import java.io.File
import java.io.PrintWriter
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestHelper(backendUrl: String) {
  val restClient: RestTestClient
  val graphqlClient: GraphqlTestClient
  val ichnoisClient: IchnoisCoreMockServerClient
  val jsonSerializer: JsonSerializer
  val dsn = "http://test-public-key@localhost:8000/0"

  var envelopeCounts: EnvelopeCounts? = null

  init {
    restClient = RestTestClient(backendUrl)
    ichnoisClient = IchnoisCoreMockServerClient("http://localhost:8000")
    graphqlClient = GraphqlTestClient(backendUrl)
    jsonSerializer = JsonSerializer(IchnoisCoreOptions.empty())
  }

  fun snapshotEnvelopeCount() {
    envelopeCounts = ichnoisClient.getEnvelopeCount()
  }

  fun ensureEnvelopeCountIncreased() {
    Thread.sleep(1000)
    val envelopeCountsAfter = ichnoisClient.getEnvelopeCount()
    assertTrue(envelopeCountsAfter!!.envelopes!! > envelopeCounts!!.envelopes!!)
  }

  fun ensureEnvelopeReceived(retryCount: Int = 1, callback: ((String) -> Boolean)) {
    val envelopes = ichnoisClient.getEnvelopes()
    assertNotNull(envelopes.envelopes)
    envelopes.envelopes.forEach { envelopeString ->
      val didMatch = callback(envelopeString)
      if (didMatch) {
        return
      }
    }
    if (retryCount <= 0) {
      throw RuntimeException("Unable to find matching envelope received by relay")
    } else {
      Thread.sleep(10000)
      ensureEnvelopeReceived(retryCount - 1, callback)
    }
  }

  fun ensureNoEnvelopeReceived(callback: ((String) -> Boolean)) {
    Thread.sleep(10000)
    val envelopes = ichnoisClient.getEnvelopes()

    if (envelopes.envelopes.isNullOrEmpty()) {
      return
    }

    envelopes.envelopes.forEach { envelopeString ->
      val didMatch = callback(envelopeString)
      if (didMatch) {
        throw RuntimeException("Found unexpected matching envelope received by relay")
      }
    }
  }

  fun ensureTransactionReceived(callback: ((IchnoisCoreTransaction, IchnoisCoreEnvelopeHeader) -> Boolean)) {
    ensureEnvelopeReceived { envelopeString -> checkIfTransactionMatches(envelopeString, callback) }
  }

  fun ensureProfileChunkReceived(callback: ((ProfileChunk, IchnoisCoreEnvelopeHeader) -> Boolean)) {
    ensureEnvelopeReceived { envelopeString -> checkIfProfileMatches(envelopeString, callback) }
  }

  fun ensureNoTransactionReceived(
    callback: ((IchnoisCoreTransaction, IchnoisCoreEnvelopeHeader) -> Boolean)
  ) {
    ensureNoEnvelopeReceived { envelopeString ->
      checkIfTransactionMatches(envelopeString, callback)
    }
  }

  fun ensureLogsReceived(callback: ((IchnoisCoreLogEvents, IchnoisCoreEnvelopeHeader) -> Boolean)) {
    ensureEnvelopeReceived { envelopeString -> checkIfLogsMatch(envelopeString, callback) }
  }

  private fun checkIfLogsMatch(
    envelopeString: String,
    callback: ((IchnoisCoreLogEvents, IchnoisCoreEnvelopeHeader) -> Boolean),
  ): Boolean {
    val envelope = deserialize(envelopeString) ?: return false
    val logs = envelope.logs() ?: return false
    return callback(logs, envelope.header)
  }

  fun doesContainMetric(
    metrics: IchnoisCoreMetricsEvents,
    name: String,
    type: String,
    value: Double,
    unit: String? = null,
  ): Boolean = metrics.hasMetric(name, type, value, unit)

  fun ensureMetricsReceived(callback: ((IchnoisCoreMetricsEvents, IchnoisCoreEnvelopeHeader) -> Boolean)) {
    ensureEnvelopeReceived { envelopeString -> checkIfMetricsMatch(envelopeString, callback) }
  }

  private fun checkIfMetricsMatch(
    envelopeString: String,
    callback: ((IchnoisCoreMetricsEvents, IchnoisCoreEnvelopeHeader) -> Boolean),
  ): Boolean {
    val envelope = deserialize(envelopeString) ?: return false
    val metrics = envelope.metrics() ?: return false
    return callback(metrics, envelope.header)
  }

  fun doesContainLogWithBody(logs: IchnoisCoreLogEvents, body: String): Boolean =
    logs.hasLogWithBody(body)

  fun doesLogWithBodyHaveAttribute(
    logs: IchnoisCoreLogEvents,
    body: String,
    attributeKey: String,
    attributeValue: Any?,
  ): Boolean = logs.hasLogWithAttribute(body, attributeKey, attributeValue)

  fun doesMetricHaveAttribute(
    metrics: IchnoisCoreMetricsEvents,
    metricName: String,
    attributeKey: String,
    attributeValue: Any?,
  ): Boolean = metrics.hasMetricWithAttribute(metricName, attributeKey, attributeValue)

  private fun checkIfTransactionMatches(
    envelopeString: String,
    callback: ((IchnoisCoreTransaction, IchnoisCoreEnvelopeHeader) -> Boolean),
  ): Boolean {
    val envelope = deserialize(envelopeString) ?: return false
    val transaction = envelope.transaction() ?: return false
    return callback(transaction, envelope.header)
  }

  private fun checkIfProfileMatches(
    envelopeString: String,
    callback: ((ProfileChunk, IchnoisCoreEnvelopeHeader) -> Boolean),
  ): Boolean {
    val envelope = deserialize(envelopeString) ?: return false
    val chunk = envelope.profileChunk() ?: return false
    return callback(chunk, envelope.header)
  }

  fun ensureErrorReceived(callback: ((IchnoisCoreEvent) -> Boolean)) {
    ensureEnvelopeReceived(retryCount = 3) { envelopeString ->
      val error = deserialize(envelopeString)?.event() ?: return@ensureEnvelopeReceived false
      val callbackResult = callback(error)
      if (!callbackResult) {
        println("found an error event but it did not match:")
        logObject(error)
      }
      callbackResult
    }
  }

  fun ensureTransactionWithSpanReceived(callback: ((IchnoisCoreSpan) -> Boolean)) {
    ensureTransactionReceived { transaction, envelopeHeader ->
      transaction.spans.forEach { span ->
        val callbackResult = callback(span)
        if (callbackResult) {
          return@ensureTransactionReceived true
        }
      }
      false
    }
  }

  fun reset() {
    ichnoisClient.reset()
  }

  fun logObject(obj: Any?) {
    obj ?: return
    // Flush rather than close: closing the writer would close System.out.
    PrintWriter(System.out).apply {
      jsonSerializer.serialize(obj, this)
      flush()
    }
    println()
  }

  private fun deserialize(envelopeString: String): IchnoisCoreEnvelope? =
    jsonSerializer.deserializeEnvelope(envelopeString.byteInputStream())

  fun <T : Operation.Data> ensureNoErrors(response: ApolloResponse<T>?) {
    response ?: throw RuntimeException("no response")
    assertFalse(response.hasErrors())
  }

  fun <T : Operation.Data> ensureErrorCount(response: ApolloResponse<T>?, errorCount: Int) {
    response ?: throw RuntimeException("no response")
    assertEquals(errorCount, response.errors?.size)
  }

  fun doesTransactionContainSpanWithOp(transaction: IchnoisCoreTransaction, op: String): Boolean =
    transaction.hasSpanWithOp(op)

  fun doesTransactionContainSpanWithOpAndDescription(
    transaction: IchnoisCoreTransaction,
    op: String,
    description: String,
  ): Boolean = transaction.hasSpanWithOpAndDescription(op, description)

  fun doesTransactionHaveTraceId(transaction: IchnoisCoreTransaction, traceId: String): Boolean =
    transaction.hasTraceId(traceId)

  fun doesTransactionHaveOp(transaction: IchnoisCoreTransaction, op: String): Boolean =
    transaction.hasOp(op)

  fun doesTransactionHave(
    transaction: IchnoisCoreTransaction,
    op: String,
    featureFlag: FeatureFlag? = null,
  ): Boolean = transaction.hasOp(op, featureFlag)

  fun doesTransactionHaveSpanWith(
    transaction: IchnoisCoreTransaction,
    op: String,
    featureFlag: FeatureFlag? = null,
    noFeatureFlags: Boolean = false,
  ): Boolean = transaction.hasSpanWithOp(op, featureFlag, noFeatureFlags)

  fun doesEventHaveExceptionMessage(event: IchnoisCoreEvent, expectedMessage: String): Boolean =
    event.hasExceptionMessage(expectedMessage)

  fun doesEventHaveFlag(event: IchnoisCoreEvent, flag: String, result: Boolean): Boolean =
    event.hasFlag(flag, result)

  fun findJar(prefix: String, inDir: String = "build/libs"): File {
    val buildDir = File(inDir)
    val jarFiles =
      buildDir.listFiles { _, name -> name.startsWith(prefix) && name.endsWith(".jar") }?.toList()
        ?: emptyList()

    if (jarFiles.isEmpty()) {
      throw AssertionError("No JAR found in ${buildDir.absolutePath}")
    }

    return jarFiles.maxOf { it }
  }

  fun launch(jar: File, env: Map<String, String>, enableOtelAutoConfig: Boolean = false): Process {
    val processBuilderList = mutableListOf("java", "--add-opens", "java.base/java.lang=ALL-UNNAMED")

    if (enableOtelAutoConfig) {
      processBuilderList.add("-Dotel.java.global-autoconfigure.enabled=true")
    }

    processBuilderList.add("-jar")
    processBuilderList.add(jar.absolutePath)

    val processBuilder =
      ProcessBuilder(processBuilderList).inheritIO() // forward i/o to current process

    processBuilder.environment().putAll(env)

    return processBuilder.start()
  }
}
