package io.ichnois

import io.ichnois.protocol.IchnoisCoreId
import kotlin.test.Test
import kotlin.test.assertEquals

class W3CTraceparentHeaderTest {

  @Test
  fun `creates header with sampled true`() {
    val traceId = IchnoisCoreId("12345678123456781234567812345678")
    val spanId = SpanId("1234567812345678")
    val header = W3CTraceparentHeader(traceId, spanId, true)

    assertEquals("traceparent", header.name)
    assertEquals("00-12345678123456781234567812345678-1234567812345678-01", header.value)
  }

  @Test
  fun `creates header with sampled false`() {
    val traceId = IchnoisCoreId("12345678123456781234567812345678")
    val spanId = SpanId("1234567812345678")
    val header = W3CTraceparentHeader(traceId, spanId, false)

    assertEquals("traceparent", header.name)
    assertEquals("00-12345678123456781234567812345678-1234567812345678-00", header.value)
  }

  @Test
  fun `creates header with sampled null`() {
    val traceId = IchnoisCoreId("12345678123456781234567812345678")
    val spanId = SpanId("1234567812345678")
    val header = W3CTraceparentHeader(traceId, spanId, null)

    assertEquals("traceparent", header.name)
    assertEquals("00-12345678123456781234567812345678-1234567812345678-00", header.value)
  }

  @Test
  fun `value follows W3C format`() {
    val traceId = IchnoisCoreId("abcdefabcdefabcdabcdefabcdefabcd")
    val spanId = SpanId("abcdefabcdefabcd")
    val header = W3CTraceparentHeader(traceId, spanId, true)

    val value = header.value
    val parts = value.split("-")

    assertEquals(4, parts.size)
    assertEquals("00", parts[0]) // Version
    assertEquals("abcdefabcdefabcdabcdefabcdefabcd", parts[1]) // Trace ID (32 hex chars)
    assertEquals("abcdefabcdefabcd", parts[2]) // Span ID (16 hex chars)
    assertEquals("01", parts[3]) // Sampled flag
  }
}
