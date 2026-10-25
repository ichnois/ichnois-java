package io.ichnois.compose.navigation3

import com.google.common.truth.Truth.assertThat
import java.lang.reflect.Modifier
import org.junit.Test

class IchnoisCoreBackStackEntryTest {

  @Test
  fun `equals follows value semantics`() {
    val entry = IchnoisCoreBackStackEntry("profile", mapOf("userId" to "123"))
    val structurallyEqual = IchnoisCoreBackStackEntry("profile", mapOf("userId" to "123"))
    val structurallyDifferent = IchnoisCoreBackStackEntry("settings")

    assertThat(entry).isEqualTo(entry)
    assertThat(entry).isEqualTo(structurallyEqual)
    assertThat(entry).isNotEqualTo(structurallyDifferent)
    assertThat(entry).isNotEqualTo(null)
    assertThat(entry).isNotEqualTo("profile")
  }

  @Test
  fun `equals includes every property`() {
    val base = IchnoisCoreBackStackEntry("profile", mapOf("userId" to "123"))
    val instanceFields =
      IchnoisCoreBackStackEntry::class
        .java
        .declaredFields
        .filterNot { Modifier.isStatic(it.modifiers) }
        .map { it.name }

    assertThat(propertyMutators.keys).containsExactlyElementsIn(instanceFields)

    propertyMutators.forEach { (propertyName, mutate) ->
      val changed = mutate(base)

      assertThat(changed).isNotEqualTo(base)
      assertThat(propertyName).isIn(instanceFields)
    }
  }

  @Test
  fun `equal instances share the same hash code`() {
    val first = IchnoisCoreBackStackEntry("profile", mapOf("userId" to "123"))
    val second = IchnoisCoreBackStackEntry("profile", mapOf("userId" to "123"))

    assertThat(first).isEqualTo(second)
    assertThat(first.hashCode()).isEqualTo(second.hashCode())
  }

  @Test
  fun `hash code includes every property`() {
    val base = IchnoisCoreBackStackEntry("profile", mapOf("userId" to "123"))
    val instanceFields =
      IchnoisCoreBackStackEntry::class
        .java
        .declaredFields
        .filterNot { Modifier.isStatic(it.modifiers) }
        .map { it.name }

    assertThat(propertyMutators.keys).containsExactlyElementsIn(instanceFields)

    propertyMutators.forEach { (propertyName, mutate) ->
      val changed = mutate(base)

      assertThat(changed.hashCode()).isNotEqualTo(base.hashCode())
      assertThat(propertyName).isIn(instanceFields)
    }
  }

  @Test
  fun `toString includes the name but redacts arguments`() {
    val info = IchnoisCoreBackStackEntry("profile", mapOf("userId" to "123"))

    assertThat(info.toString()).isEqualTo("IchnoisCoreBackStackEntry(name=profile)")
    assertThat(info.toString()).doesNotContain("userId")
    assertThat(info.toString()).doesNotContain("123")
  }

  private companion object {
    val propertyMutators =
      mapOf<String, (IchnoisCoreBackStackEntry) -> IchnoisCoreBackStackEntry>(
        "name" to { entry -> IchnoisCoreBackStackEntry("settings", entry.arguments) },
        "arguments" to
          { entry ->
            IchnoisCoreBackStackEntry(entry.name, mapOf("userId" to "456"))
          },
      )
  }
}
