package io.ichnois

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IchnoisCoreValuesTest {
  @Test
  fun `constructor called with values`() {
    val values = listOf(1, 2)
    val sut = IchnoisCoreValues<Int>(values)

    assertEquals(values, sut.values)
  }

  @Test
  fun `constructor called with null`() {
    val sut = IchnoisCoreValues<Any>(null)
    assertTrue(sut.values.isEmpty())
  }

  @Test
  fun `when constructor receives immutable list as an argument, its still possible to add more fingerprints to the event`() {
    val values = listOf(1, 2)
    val sut = IchnoisCoreValues(values)
    sut.values += 3
    assertEquals(listOf(1, 2, 3), sut.values)
  }
}
