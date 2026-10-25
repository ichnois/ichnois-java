package io.ichnois

import io.ichnois.util.UUIDStringUtils
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class UUIDStringUtilsTest {
  @Test
  fun `UUID toString matches UUIDStringUtils to String`() {
    val uuid = UUID.randomUUID()
    val ichnoisIdString = uuid.toString().replace("-", "")
    assertEquals(ichnoisIdString, UUIDStringUtils.toIchnoisCoreIdString(uuid))
  }

  @Test
  fun `UUID toString matches UUIDStringUtils to String for SpanId`() {
    val uuid = UUID.randomUUID()
    val ichnoisIdString = uuid.toString().replace("-", "").substring(0, 16)
    assertEquals(ichnoisIdString, UUIDStringUtils.toIchnoisCoreSpanIdString(uuid))
  }
}
