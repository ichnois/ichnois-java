package io.ichnois

import kotlin.test.Test
import kotlin.test.assertEquals

class InstrumenterTest {
  @Test
  fun `can create from otel string`() {
    val instrumenter = Instrumenter.valueOf("OTEL")
    assertEquals(instrumenter, Instrumenter.OTEL)
  }

  @Test
  fun `can create from ichnois string`() {
    val instrumenter = Instrumenter.valueOf("ICHNOIS")
    assertEquals(instrumenter, Instrumenter.ICHNOIS)
  }
}
