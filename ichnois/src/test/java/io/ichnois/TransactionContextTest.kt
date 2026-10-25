package io.ichnois

import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.protocol.TransactionNameSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.mock

class TransactionContextTest {

  @Test
  fun `when created using primary constructor, sampling decision and parent sampling are not set`() {
    val context = TransactionContext("name", "op")
    assertNull(context.sampled)
    assertNull(context.profileSampled)
    assertNull(context.parentSampled)
    assertEquals("name", context.name)
    assertEquals("op", context.op)
    assertFalse(context.isForNextAppStart)
  }

  @Test
  fun `when context is created from propagation context, parent sampling decision of false is set from trace header`() {
    val logger = mock<ILogger>()
    val propagationContext =
      PropagationContext.fromHeaders(
        logger,
        IchnoisCoreTraceHeader(IchnoisCoreId(), SpanId(), false).value,
        "ichnois-trace_id=a,ichnois-transaction=ichnoisTransaction,ichnois-sample_rate=0.3",
        null,
      )
    val context = TransactionContext.fromPropagationContext(propagationContext)
    assertNull(context.sampled)
    assertNull(context.profileSampled)
    assertFalse(context.parentSampled!!)
    assertEquals(0.3, context.parentSamplingDecision!!.sampleRate)
    assertFalse(context.isForNextAppStart)
  }

  @Test
  fun `when context is created from propagation context, parent sampling decision of false is set from trace header if no sample rate is available`() {
    val logger = mock<ILogger>()
    val propagationContext =
      PropagationContext.fromHeaders(
        logger,
        IchnoisCoreTraceHeader(IchnoisCoreId(), SpanId(), false).value,
        "ichnois-trace_id=a,ichnois-transaction=ichnoisTransaction",
        null,
      )
    val context = TransactionContext.fromPropagationContext(propagationContext)
    assertNull(context.sampled)
    assertNull(context.profileSampled)
    assertFalse(context.parentSampled!!)
    assertNull(context.parentSamplingDecision!!.sampleRate)
    assertFalse(context.isForNextAppStart)
  }

  @Test
  fun `when context is created from propagation context, parent sampling decision of true is set from trace header`() {
    val logger = mock<ILogger>()
    val propagationContext =
      PropagationContext.fromHeaders(
        logger,
        IchnoisCoreTraceHeader(IchnoisCoreId(), SpanId(), true).value,
        "ichnois-trace_id=a,ichnois-transaction=ichnoisTransaction,ichnois-sample_rate=0.3",
        null,
      )
    val context = TransactionContext.fromPropagationContext(propagationContext)
    assertNull(context.sampled)
    assertNull(context.profileSampled)
    assertTrue(context.parentSampled!!)
    assertEquals(0.3, context.parentSamplingDecision!!.sampleRate)
    assertFalse(context.isForNextAppStart)
  }

  @Test
  fun `when context is created from propagation context, parent sampling decision of true is set from trace header if no sample rate is available`() {
    val logger = mock<ILogger>()
    val propagationContext =
      PropagationContext.fromHeaders(
        logger,
        IchnoisCoreTraceHeader(IchnoisCoreId(), SpanId(), true).value,
        "ichnois-trace_id=a,ichnois-transaction=ichnoisTransaction",
        null,
      )
    val context = TransactionContext.fromPropagationContext(propagationContext)
    assertNull(context.sampled)
    assertNull(context.profileSampled)
    assertTrue(context.parentSampled!!)
    assertNull(context.parentSamplingDecision!!.sampleRate)
    assertFalse(context.isForNextAppStart)
  }

  @Test
  fun `setForNextAppStart sets the isForNextAppStart flag`() {
    val context = TransactionContext("name", "op")
    context.isForNextAppStart = true
    assertTrue(context.isForNextAppStart)
  }

  @Test
  fun `when passing null baggage creates a new one`() {
    val context = TransactionContext(IchnoisCoreId(), SpanId(), null, null, null)
    assertNotNull(context.baggage)
    assertNotNull(context.baggage?.sampleRand)
  }

  @Test
  fun `when passing null baggage creates a new one and uses parent sampling decision`() {
    val context =
      TransactionContext(IchnoisCoreId(), SpanId(), null, TracesSamplingDecision(true, 0.1, 0.2), null)
    assertNotNull(context.baggage)
    assertEquals(0.2, context.baggage?.sampleRand!!, 0.0001)
  }

  @Test
  fun `when using few param ctor creates a new baggage`() {
    val context = TransactionContext("name", "op")
    assertNotNull(context.baggage)
    assertNotNull(context.baggage?.sampleRand)
  }

  @Test
  fun `when using few param ctor creates a new baggage and uses sampling decision`() {
    val context =
      TransactionContext(
        "name",
        TransactionNameSource.CUSTOM,
        "op",
        TracesSamplingDecision(true, 0.1, 0.2),
      )
    assertNotNull(context.baggage)
    assertEquals(0.2, context.baggage?.sampleRand!!, 0.0001)
  }
}
