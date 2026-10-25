package io.ichnois

import junit.framework.TestCase.assertEquals
import kotlin.test.Test

class IchnoisCoreUUIDTest {
  @Test
  fun `generated IchnoisCoreID is 32 characters long`() {
    val ichnoisId = IchnoisCoreUUID.generateIchnoisCoreId()
    assertEquals(32, ichnoisId.length)
  }

  @Test
  fun `generated SpanID is 16 characters long`() {
    val ichnoisId = IchnoisCoreUUID.generateSpanId()
    assertEquals(16, ichnoisId.length)
  }
}
