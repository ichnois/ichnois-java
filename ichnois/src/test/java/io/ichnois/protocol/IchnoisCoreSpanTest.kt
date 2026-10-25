package io.ichnois.protocol

import io.ichnois.IScopes
import io.ichnois.IchnoisCoreLongDate
import io.ichnois.IchnoisCoreTracer
import io.ichnois.Span
import io.ichnois.SpanOptions
import io.ichnois.TransactionContext
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test
import org.mockito.kotlin.mock

class IchnoisCoreSpanTest {
  @Test
  fun `end timestamps is kept null if not provided`() {
    // when a span with a start timestamp is generated
    val span =
      Span(
        TransactionContext("name", "op"),
        mock<IchnoisCoreTracer>(),
        mock<IScopes>(),
        SpanOptions().also { it.startTimestamp = IchnoisCoreLongDate(1000000) },
      )

    val ichnoisSpan = IchnoisCoreSpan(span)

    // then the start timestamp should be correctly set
    assertEquals(0.001, ichnoisSpan.startTimestamp)

    // but the end time should remain untouched
    assertNull(ichnoisSpan.timestamp)
  }
}
