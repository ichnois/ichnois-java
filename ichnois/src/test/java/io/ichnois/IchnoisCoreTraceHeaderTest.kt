package io.ichnois

import io.ichnois.exception.InvalidIchnoisCoreTraceHeaderException
import io.ichnois.protocol.IchnoisCoreId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.text.substring

class IchnoisCoreTraceHeaderTest {
  @Test
  fun `when ichnois-trace header is incorrect throws exception`() {
    val ichnoisId = IchnoisCoreId()
    val ex = assertFailsWith<InvalidIchnoisCoreTraceHeaderException> { IchnoisCoreTraceHeader("$ichnoisId") }
    assertEquals("ichnois-trace header does not conform to expected format: $ichnoisId", ex.message)
  }

  @Test
  fun `when there is a trailing dash without sampling decision throws exception`() {
    val ichnoisId = IchnoisCoreId()
    val spanId = SpanId()
    val ex =
      assertFailsWith<InvalidIchnoisCoreTraceHeaderException> { IchnoisCoreTraceHeader("$ichnoisId-$spanId-") }
    assertEquals(
      "ichnois-trace header does not conform to expected format: $ichnoisId-$spanId-",
      ex.message,
    )
  }

  @Test
  fun `when trace-id has less than 32 characters throws exception`() {
    val ichnoisId = IchnoisCoreId().toString().substring(0, 8)
    val spanId = SpanId()
    val ex =
      assertFailsWith<InvalidIchnoisCoreTraceHeaderException> { IchnoisCoreTraceHeader("$ichnoisId-$spanId") }
    assertEquals(
      "ichnois-trace header does not conform to expected format: $ichnoisId-$spanId",
      ex.message,
    )
  }

  @Test
  fun `when trace-id has more than 32 characters throws exception`() {
    val ichnoisId = IchnoisCoreId().toString() + "abc"
    val spanId = SpanId()
    val ex =
      assertFailsWith<InvalidIchnoisCoreTraceHeaderException> { IchnoisCoreTraceHeader("$ichnoisId-$spanId") }
    assertEquals(
      "ichnois-trace header does not conform to expected format: $ichnoisId-$spanId",
      ex.message,
    )
  }

  @Test
  fun `when trace-id contains invalid characters throws exception`() {
    var ichnoisId = IchnoisCoreId().toString()
    ichnoisId = ichnoisId.substring(0, 8) + "g" + ichnoisId.substring(8)
    val spanId = SpanId()
    val ex =
      assertFailsWith<InvalidIchnoisCoreTraceHeaderException> { IchnoisCoreTraceHeader("$ichnoisId-$spanId") }
    assertEquals(
      "ichnois-trace header does not conform to expected format: $ichnoisId-$spanId",
      ex.message,
    )
  }

  @Test
  fun `when span-id has less than 16 characters throws exception`() {
    val ichnoisId = IchnoisCoreId()
    val spanId = SpanId().toString().substring(0, 8)
    val ex =
      assertFailsWith<InvalidIchnoisCoreTraceHeaderException> { IchnoisCoreTraceHeader("$ichnoisId-$spanId") }
    assertEquals(
      "ichnois-trace header does not conform to expected format: $ichnoisId-$spanId",
      ex.message,
    )
  }

  @Test
  fun `when span-id has more than 32 characters throws exception`() {
    val ichnoisId = IchnoisCoreId()
    val spanId = SpanId().toString() + "abc"
    val ex =
      assertFailsWith<InvalidIchnoisCoreTraceHeaderException> { IchnoisCoreTraceHeader("$ichnoisId-$spanId") }
    assertEquals(
      "ichnois-trace header does not conform to expected format: $ichnoisId-$spanId",
      ex.message,
    )
  }

  @Test
  fun `when span-id contains invalid characters throws exception`() {
    val ichnoisId = IchnoisCoreId()
    var spanId = SpanId().toString()
    spanId = spanId.substring(0, 8) + "g" + spanId.substring(8)
    val ex =
      assertFailsWith<InvalidIchnoisCoreTraceHeaderException> { IchnoisCoreTraceHeader("$ichnoisId-$spanId") }
    assertEquals(
      "ichnois-trace header does not conform to expected format: $ichnoisId-$spanId",
      ex.message,
    )
  }

  @Test
  fun `handles header with positive sampling decision`() {
    val ichnoisId = IchnoisCoreId()
    val spanId = SpanId()
    val header = IchnoisCoreTraceHeader("$ichnoisId-$spanId-1")
    assertEquals(ichnoisId, header.traceId)
    assertEquals(spanId, header.spanId)
    assertEquals(true, header.isSampled)
  }

  @Test
  fun `handles header with negative sampling decision`() {
    val ichnoisId = IchnoisCoreId()
    val spanId = SpanId()
    val header = IchnoisCoreTraceHeader("$ichnoisId-$spanId-0")
    assertEquals(ichnoisId, header.traceId)
    assertEquals(spanId, header.spanId)
    assertEquals(false, header.isSampled)
  }

  @Test
  fun `handles header without sampling decision`() {
    val ichnoisId = IchnoisCoreId()
    val spanId = SpanId()
    val header = IchnoisCoreTraceHeader("$ichnoisId-$spanId")
    assertEquals(ichnoisId, header.traceId)
    assertEquals(spanId, header.spanId)
    assertNull(header.isSampled)
  }

  @Test
  fun `handles header without sampling decision and leading whitespace`() {
    val ichnoisId = IchnoisCoreId()
    val spanId = SpanId()
    val header = IchnoisCoreTraceHeader(" \t $ichnoisId-$spanId")
    assertEquals(ichnoisId, header.traceId)
    assertEquals(spanId, header.spanId)
    assertNull(header.isSampled)
  }

  @Test
  fun `handles header without sampling decision and trailing whitespace`() {
    val ichnoisId = IchnoisCoreId()
    val spanId = SpanId()
    val header = IchnoisCoreTraceHeader("$ichnoisId-$spanId \t ")
    assertEquals(ichnoisId, header.traceId)
    assertEquals(spanId, header.spanId)
    assertNull(header.isSampled)
  }

  @Test
  fun `when sampling decision is not made, getValue returns header with traceId and spanId`() {
    val ichnoisId = IchnoisCoreId()
    val spanId = SpanId()
    val header = IchnoisCoreTraceHeader("$ichnoisId-$spanId")
    assertEquals("$ichnoisId-$spanId", header.value)
  }

  @Test
  fun `when sampling decision positive, getValue returns header with traceId and spanId and sampling`() {
    val ichnoisId = IchnoisCoreId()
    val spanId = SpanId()
    val header = IchnoisCoreTraceHeader("$ichnoisId-$spanId-1")
    assertEquals("$ichnoisId-$spanId-1", header.value)
  }

  @Test
  fun `when sampling decision negative, getValue returns header with traceId and spanId and sampling`() {
    val ichnoisId = IchnoisCoreId()
    val spanId = SpanId()
    val header = IchnoisCoreTraceHeader("$ichnoisId-$spanId-0")
    assertEquals("$ichnoisId-$spanId-0", header.value)
  }

  @Test
  fun `throws InvalidIchnoisCoreTraceHeaderException when traceId has invalid value`() {
    assertFailsWith<InvalidIchnoisCoreTraceHeaderException> { IchnoisCoreTraceHeader("xxx-${SpanId()}-0") }
  }
}
