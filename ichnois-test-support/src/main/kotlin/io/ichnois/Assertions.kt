package io.ichnois

import io.ichnois.protocol.IchnoisCoreTransaction
import org.mockito.Mockito
import org.mockito.kotlin.internal.createInstance

/** Verifies is [IchnoisCoreEnvelope] contains first event matching a predicate. */
fun checkEvent(predicate: (IchnoisCoreEvent) -> Unit): IchnoisCoreEnvelope = check {
  val event: IchnoisCoreEvent? = it.items.first().getEvent(JsonSerializer(IchnoisCoreOptions.empty()))
  if (event != null) {
    predicate(event)
  } else {
    throw SkipError("event is null")
  }
}

/** Verifies is [IchnoisCoreEnvelope] contains log events matching a predicate. */
fun checkLogs(predicate: (IchnoisCoreLogEvents) -> Unit): IchnoisCoreEnvelope {
  return check {
    val events: IchnoisCoreLogEvents? = it.items.first().getLogs(JsonSerializer(IchnoisCoreOptions.empty()))
    if (events != null) {
      predicate(events)
    } else {
      throw SkipError("event is null")
    }
  }
}

fun checkTransaction(predicate: (IchnoisCoreTransaction) -> Unit): IchnoisCoreEnvelope = check {
  val transaction = it.items.first().getTransaction(JsonSerializer(IchnoisCoreOptions.empty()))
  if (transaction != null) {
    predicate(transaction)
  } else {
    throw SkipError("transaction is null")
  }
}

/**
 * Asserts an envelope item of [T] exists in [items] and returns the first one. Otherwise it throws
 * an [AssertionError]. The item must have [requiredType] (if not null) and will be used as
 * parameter in [predicate].
 */
inline fun <reified T> assertEnvelopeItem(
  items: List<IchnoisCoreEnvelopeItem>,
  requiredType: IchnoisCoreItemType? = null,
  logger: ILogger = NoOpLogger.getInstance(),
  predicate: (index: Int, item: T) -> Unit = { _, _ -> },
): T {
  val item =
    items
      .mapIndexedNotNull { index, it ->
        // We check for type, so we don't risk to deserialize items with wrong deserializers.
        // E.g. a profile has different measurements than a transaction, which caused an OOM.
        if (requiredType != null && it.header.type != requiredType) {
          return@mapIndexedNotNull null
        }
        val deserialized =
          JsonSerializer(
              IchnoisCoreOptions().apply {
                isDebug = true
                setLogger(logger)
              }
            )
            .deserialize(it.data.inputStream().reader(), T::class.java)
        deserialized?.let { Pair(index, it) }
      }
      .firstOrNull() ?: throw AssertionError("No item found of type: ${T::class.java.name}")
  predicate(item.first, item.second)
  return item.second
}

/**
 * Asserts a transaction exists in [items] and returns the first one. Otherwise it throws an
 * [AssertionError].
 */
inline fun assertEnvelopeEvent(
  items: List<IchnoisCoreEnvelopeItem>,
  logger: ILogger = NoOpLogger.getInstance(),
  predicate: (index: Int, item: IchnoisCoreEvent) -> Unit = { _, _ -> },
): IchnoisCoreEvent = assertEnvelopeItem(items, IchnoisCoreItemType.Event, logger, predicate)

/**
 * Asserts a transaction exists in [items] and returns the first one. Otherwise it throws an
 * [AssertionError].
 */
inline fun assertEnvelopeTransaction(
  items: List<IchnoisCoreEnvelopeItem>,
  logger: ILogger = NoOpLogger.getInstance(),
  predicate: (index: Int, item: IchnoisCoreTransaction) -> Unit = { _, _ -> },
): IchnoisCoreTransaction = assertEnvelopeItem(items, IchnoisCoreItemType.Transaction, logger, predicate)

/**
 * Asserts a profile exists in [items] and returns the first one. Otherwise it throws an
 * [AssertionError].
 */
inline fun assertEnvelopeProfile(
  items: List<IchnoisCoreEnvelopeItem>,
  logger: ILogger = NoOpLogger.getInstance(),
  predicate: (index: Int, item: ProfilingTraceData) -> Unit = { _, _ -> },
): ProfilingTraceData = assertEnvelopeItem(items, IchnoisCoreItemType.Profile, logger, predicate)

/**
 * Asserts a feedback exists in [items] and returns the first one. Otherwise it throws an
 * [AssertionError].
 */
inline fun assertEnvelopeFeedback(
  items: List<IchnoisCoreEnvelopeItem>,
  logger: ILogger = NoOpLogger.getInstance(),
  predicate: (index: Int, item: IchnoisCoreEvent) -> Unit = { _, _ -> },
): IchnoisCoreEvent = assertEnvelopeItem(items, IchnoisCoreItemType.Feedback, logger, predicate)

/**
 * Modified version of check from mockito-kotlin Verification.kt, that does not print errors of type
 * `SkipError`.
 */
private inline fun <reified T : Any> check(noinline predicate: (T) -> Unit): T =
  Mockito.argThat { arg: T? ->
    if (arg == null) {
      error(
        """
                        The argument passed to the predicate was null.

        If you are trying to verify an argument to be null, use `isNull()`.
        If you are using `check` as part of a stubbing, use `argThat` or `argForWhich` instead.
        """
          .trimIndent()
      )
    }

    try {
      predicate(arg)
      true
    } catch (e: SkipError) {
      false
    } catch (e: Error) {
      e.printStackTrace()
      false
    }
  } ?: createInstance(T::class)

class SkipError(message: String) : Error(message)

val mockServerRequestTimeoutMillis = 5000L
