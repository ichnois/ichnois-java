package io.ichnois.protocol

import io.ichnois.IchnoisCoreUUID
import io.ichnois.util.StringUtils
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times

class IchnoisCoreIdTest {
  @Test
  fun `does not throw when instantiated with corrupted UUID`() {
    val id = IchnoisCoreId("0000-0000")
    assertEquals("00000000000000000000000000000000", id.toString())
  }

  @Test
  fun `dashes are stripped if initialized with 36char uuid string`() {
    val uuidString = UUID.randomUUID().toString()
    val id = IchnoisCoreId(uuidString)
    assertEquals(uuidString.replace("-", ""), id.toString())
  }

  @Test
  fun `UUID is not generated on initialization`() {
    val uuid = IchnoisCoreUUID.generateIchnoisCoreId()
    Mockito.mockStatic(IchnoisCoreUUID::class.java).use { utils ->
      utils.`when`<String> { IchnoisCoreUUID.generateIchnoisCoreId() }.thenReturn(uuid)
      val ignored = IchnoisCoreId()
      utils.verify({ IchnoisCoreUUID.generateIchnoisCoreId() }, never())
    }
  }

  @Test
  fun `UUID is generated only once`() {
    val uuid = IchnoisCoreUUID.generateIchnoisCoreId()
    Mockito.mockStatic(IchnoisCoreUUID::class.java).use { utils ->
      utils.`when`<String> { IchnoisCoreUUID.generateIchnoisCoreId() }.thenReturn(uuid)
      val ichnoisId = IchnoisCoreId()
      val uuid1 = ichnoisId.toString()
      val uuid2 = ichnoisId.toString()

      assertEquals(uuid1, uuid2)
      utils.verify({ IchnoisCoreUUID.generateIchnoisCoreId() }, times(1))
    }
  }

  @Test
  fun `normalizeUUID is never called when using empty constructor`() {
    Mockito.mockStatic(StringUtils::class.java).use { utils ->
      utils
        .`when`<Any> { StringUtils.normalizeUUID(any()) }
        .thenReturn("00000000000000000000000000000000")
      val ichnoisId = IchnoisCoreId()
      val uuid1 = ichnoisId.toString()
      val uuid2 = ichnoisId.toString()

      assertEquals(uuid1, uuid2)
      utils.verify({ StringUtils.normalizeUUID(any()) }, times(0))
    }
  }

  @Test
  fun `normalizeUUID is only called once when String is passed to constructor`() {
    Mockito.mockStatic(StringUtils::class.java).use { utils ->
      utils
        .`when`<Any> { StringUtils.normalizeUUID(any()) }
        .thenReturn("00000000000000000000000000000000")
      val ichnoisId = IchnoisCoreId("00000000000000000000000000000000")
      val uuid1 = ichnoisId.toString()
      val uuid2 = ichnoisId.toString()

      assertEquals(uuid1, uuid2)
      utils.verify({ StringUtils.normalizeUUID(any()) }, times(1))
    }
  }

  @Test
  fun `normalizeUUID is only called once when UUID is passed to constructor`() {
    Mockito.mockStatic(StringUtils::class.java).use { utils ->
      utils
        .`when`<Any> { StringUtils.normalizeUUID(any()) }
        .thenReturn("00000000000000000000000000000000")
      val ichnoisId = IchnoisCoreId(UUID.randomUUID())
      val uuid1 = ichnoisId.toString()
      val uuid2 = ichnoisId.toString()

      assertEquals(uuid1, uuid2)
      utils.verify({ StringUtils.normalizeUUID(any()) }, times(1))
    }
  }
}
