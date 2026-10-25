package io.ichnois

import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals

class IchnoisCoreAttributeTypeTest {

  @Test
  fun `inferFrom returns BOOLEAN for Boolean`() {
    assertEquals(IchnoisCoreAttributeType.BOOLEAN, IchnoisCoreAttributeType.inferFrom(true))
    assertEquals(IchnoisCoreAttributeType.BOOLEAN, IchnoisCoreAttributeType.inferFrom(false))
  }

  @Test
  fun `inferFrom returns INTEGER for Integer`() {
    assertEquals(IchnoisCoreAttributeType.INTEGER, IchnoisCoreAttributeType.inferFrom(42))
  }

  @Test
  fun `inferFrom returns INTEGER for Long`() {
    assertEquals(IchnoisCoreAttributeType.INTEGER, IchnoisCoreAttributeType.inferFrom(42L))
  }

  @Test
  fun `inferFrom returns INTEGER for Short`() {
    assertEquals(IchnoisCoreAttributeType.INTEGER, IchnoisCoreAttributeType.inferFrom(42.toShort()))
  }

  @Test
  fun `inferFrom returns INTEGER for Byte`() {
    assertEquals(IchnoisCoreAttributeType.INTEGER, IchnoisCoreAttributeType.inferFrom(42.toByte()))
  }

  @Test
  fun `inferFrom returns INTEGER for BigInteger`() {
    assertEquals(IchnoisCoreAttributeType.INTEGER, IchnoisCoreAttributeType.inferFrom(BigInteger.valueOf(42)))
  }

  @Test
  fun `inferFrom returns INTEGER for AtomicInteger`() {
    assertEquals(IchnoisCoreAttributeType.INTEGER, IchnoisCoreAttributeType.inferFrom(AtomicInteger(42)))
  }

  @Test
  fun `inferFrom returns INTEGER for AtomicLong`() {
    assertEquals(IchnoisCoreAttributeType.INTEGER, IchnoisCoreAttributeType.inferFrom(AtomicLong(42)))
  }

  @Test
  fun `inferFrom returns DOUBLE for Double`() {
    assertEquals(IchnoisCoreAttributeType.DOUBLE, IchnoisCoreAttributeType.inferFrom(3.14))
  }

  @Test
  fun `inferFrom returns DOUBLE for Float`() {
    assertEquals(IchnoisCoreAttributeType.DOUBLE, IchnoisCoreAttributeType.inferFrom(3.14f))
  }

  @Test
  fun `inferFrom returns DOUBLE for BigDecimal`() {
    assertEquals(
      IchnoisCoreAttributeType.DOUBLE,
      IchnoisCoreAttributeType.inferFrom(BigDecimal.valueOf(3.14)),
    )
  }

  @Test
  fun `inferFrom returns STRING for String`() {
    assertEquals(IchnoisCoreAttributeType.STRING, IchnoisCoreAttributeType.inferFrom("hello"))
  }

  @Test
  fun `inferFrom returns STRING for null`() {
    assertEquals(IchnoisCoreAttributeType.STRING, IchnoisCoreAttributeType.inferFrom(null))
  }

  @Test
  fun `inferFrom returns ARRAY for List of Strings`() {
    assertEquals(IchnoisCoreAttributeType.ARRAY, IchnoisCoreAttributeType.inferFrom(listOf("a", "b")))
  }

  @Test
  fun `inferFrom returns ARRAY for List of Integers`() {
    assertEquals(IchnoisCoreAttributeType.ARRAY, IchnoisCoreAttributeType.inferFrom(listOf(1, 2, 3)))
  }

  @Test
  fun `inferFrom returns ARRAY for Set of Booleans`() {
    assertEquals(IchnoisCoreAttributeType.ARRAY, IchnoisCoreAttributeType.inferFrom(setOf(true, false)))
  }

  @Test
  fun `inferFrom returns ARRAY for String array`() {
    assertEquals(IchnoisCoreAttributeType.ARRAY, IchnoisCoreAttributeType.inferFrom(arrayOf("a", "b")))
  }

  @Test
  fun `inferFrom returns ARRAY for int array`() {
    assertEquals(IchnoisCoreAttributeType.ARRAY, IchnoisCoreAttributeType.inferFrom(intArrayOf(1, 2)))
  }

  @Test
  fun `inferFrom returns ARRAY for empty list`() {
    assertEquals(IchnoisCoreAttributeType.ARRAY, IchnoisCoreAttributeType.inferFrom(emptyList<String>()))
  }

  @Test
  fun `inferFrom returns ARRAY for mixed-type list`() {
    assertEquals(IchnoisCoreAttributeType.ARRAY, IchnoisCoreAttributeType.inferFrom(listOf("a", 1, true)))
  }

  @Test
  fun `arrayAttribute factory accepts Object array`() {
    val attr = IchnoisCoreAttribute.arrayAttribute("key", arrayOf("a", "b"))
    assertEquals("key", attr.name)
    assertEquals(IchnoisCoreAttributeType.ARRAY, attr.type)
  }
}
