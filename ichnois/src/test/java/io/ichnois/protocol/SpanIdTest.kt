package io.ichnois.protocol

import io.ichnois.IchnoisCoreUUID
import io.ichnois.SpanId
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.Mockito
import org.mockito.kotlin.never
import org.mockito.kotlin.times

class SpanIdTest {
  @Test
  fun `ID is not generated on initialization`() {
    val uuid = IchnoisCoreUUID.generateSpanId()
    Mockito.mockStatic(IchnoisCoreUUID::class.java).use { utils ->
      utils.`when`<String> { IchnoisCoreUUID.generateSpanId() }.thenReturn(uuid)
      val ignored = SpanId()
      utils.verify({ IchnoisCoreUUID.generateSpanId() }, never())
    }
  }

  @Test
  fun `ID is generated only once`() {
    val uuid = IchnoisCoreUUID.generateSpanId()
    Mockito.mockStatic(IchnoisCoreUUID::class.java).use { utils ->
      utils.`when`<String> { IchnoisCoreUUID.generateSpanId() }.thenReturn(uuid)
      val spanId = SpanId()
      val uuid1 = spanId.toString()
      val uuid2 = spanId.toString()

      assertEquals(uuid1, uuid2)
      utils.verify({ IchnoisCoreUUID.generateSpanId() }, times(1))
    }
  }
}
