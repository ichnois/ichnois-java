package io.ichnois

import kotlin.test.Test
import kotlin.test.assertEquals

class IchnoisCoreBaseEventTypeTest {
  @Test
  fun `Session enum type has a session type string`() {
    assertEquals("session", IchnoisCoreItemType.Session.itemType)
  }

  @Test
  fun `Event enum type has a event type string`() {
    assertEquals("event", IchnoisCoreItemType.Event.itemType)
  }
}
