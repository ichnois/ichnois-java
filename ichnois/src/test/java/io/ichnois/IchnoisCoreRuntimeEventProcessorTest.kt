package io.ichnois

import io.ichnois.protocol.IchnoisCoreRuntime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IchnoisCoreRuntimeEventProcessorTest {
  private val eventProcessor = IchnoisCoreRuntimeEventProcessor("16", "OpenJDK")

  @Test
  fun `when event does not have a runtime, sets runtime`() {
    val event = eventProcessor.process(IchnoisCoreEvent(), null)
    assertNotNull(event) {
      assertNotNull(it.contexts.runtime) { runtime ->
        assertEquals("OpenJDK", runtime.name)
        assertEquals("16", runtime.version)
      }
    }
  }

  @Test
  fun `when event has runtime with null name and null version, sets runtime`() {
    val event = IchnoisCoreEvent()
    event.contexts.setRuntime(IchnoisCoreRuntime())
    val result = eventProcessor.process(event, null)
    assertNotNull(result) {
      assertNotNull(it.contexts.runtime) { runtime ->
        assertEquals("OpenJDK", runtime.name)
        assertEquals("16", runtime.version)
      }
    }
  }

  @Test
  fun `when event has runtime with null name and version set, does not change runtime`() {
    val event = IchnoisCoreEvent()
    val runtime = IchnoisCoreRuntime()
    runtime.version = "1.1"
    event.contexts.setRuntime(runtime)
    val result = eventProcessor.process(event, null)
    assertNotNull(result) {
      assertNotNull(it.contexts.runtime) { runtime ->
        assertNull(runtime.name)
        assertEquals("1.1", runtime.version)
      }
    }
  }

  @Test
  fun `when event has runtime with null version and name set, does not change runtime`() {
    val event = IchnoisCoreEvent()
    val runtime = IchnoisCoreRuntime()
    runtime.name = "Java"
    event.contexts.setRuntime(runtime)
    val result = eventProcessor.process(event, null)
    assertNotNull(result) {
      assertNotNull(it.contexts.runtime) { runtime ->
        assertNull(runtime.version)
        assertEquals("Java", runtime.name)
      }
    }
  }
}
