package io.ichnois.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

class IchnoisCoreRuntimeTest {
  @Test
  fun `copying IchnoisCore runtime wont have the same references`() {
    val runtime = IchnoisCoreRuntime()
    val unknown = mapOf(Pair("unknown", "unknown"))
    runtime.setUnknown(unknown)

    val clone = IchnoisCoreRuntime(runtime)

    assertNotNull(clone)
    assertNotSame(runtime, clone)

    assertNotSame(runtime.unknown, clone.unknown)
  }

  @Test
  fun `copying IchnoisCore runtime system will have the same values`() {
    val runtime = IchnoisCoreRuntime()
    runtime.name = "name"
    runtime.version = "version"
    runtime.rawDescription = "raw description"
    val unknown = mapOf(Pair("unknown", "unknown"))
    runtime.setUnknown(unknown)

    val clone = IchnoisCoreRuntime(runtime)

    assertEquals("name", clone.name)
    assertEquals("version", clone.version)
    assertEquals("raw description", clone.rawDescription)
    assertNotNull(clone.unknown) { assertEquals("unknown", it["unknown"]) }
  }
}
