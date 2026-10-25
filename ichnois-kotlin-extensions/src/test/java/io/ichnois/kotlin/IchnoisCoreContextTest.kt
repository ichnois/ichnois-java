package io.ichnois.kotlin

import io.ichnois.ScopeType
import io.ichnois.IchnoisCore
import io.ichnois.test.initForTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class IchnoisCoreContextTest {
  // IchnoisCoreContext makes its scopes current, which is honoured in global hub mode as well, so it
  // isolates the same way there. Only implicit forking stays suppressed, see the last two tests.

  private val dsn = "https://key@ichnois.io/123"

  @BeforeTest
  fun init() {
    initForTest(dsn)
  }

  @AfterTest
  fun close() {
    IchnoisCore.close()
  }

  @Test
  fun testContextIsNotPassedByDefaultBetweenCoroutines() = runBlocking {
    IchnoisCore.setTag("parent", "parentValue")
    val c1 =
      launch(IchnoisCoreContext()) {
        IchnoisCore.setTag("c1", "c1value")
        assertEquals("c1value", getTag("c1"))
        assertEquals("parentValue", getTag("parent"))
        assertNull(getTag("c2"))
      }
    val c2 =
      launch(IchnoisCoreContext()) {
        IchnoisCore.setTag("c2", "c2value")
        assertEquals("c2value", getTag("c2"))
        assertEquals("parentValue", getTag("parent"))
        assertNotNull(getTag("c1"))
      }
    listOf(c1, c2).joinAll()
    assertNotNull(getTag("parent"))
    assertNotNull(getTag("c1"))
    assertNotNull(getTag("c2"))
    return@runBlocking
  }

  @Test
  fun testContextIsNotPassedByDefaultBetweenCoroutinesCurrentScope() = runBlocking {
    IchnoisCore.configureScope(ScopeType.CURRENT) { scope -> scope.setTag("parent", "parentValue") }
    val c1 =
      launch(IchnoisCoreContext()) {
        IchnoisCore.configureScope(ScopeType.CURRENT) { scope -> scope.setTag("c1", "c1value") }
        assertEquals("c1value", getTag("c1", ScopeType.CURRENT))
        assertEquals("parentValue", getTag("parent", ScopeType.CURRENT))
        assertNull(getTag("c2", ScopeType.CURRENT))
      }
    val c2 =
      launch(IchnoisCoreContext()) {
        IchnoisCore.configureScope(ScopeType.CURRENT) { scope -> scope.setTag("c2", "c2value") }
        assertEquals("c2value", getTag("c2", ScopeType.CURRENT))
        assertEquals("parentValue", getTag("parent", ScopeType.CURRENT))
        assertNull(getTag("c1", ScopeType.CURRENT))
      }
    listOf(c1, c2).joinAll()
    assertNotNull(getTag("parent", ScopeType.CURRENT))
    assertNull(getTag("c1", ScopeType.CURRENT))
    assertNull(getTag("c2", ScopeType.CURRENT))
  }

  @Test
  fun testContextIsPassedToChildCoroutines() = runBlocking {
    val c1 =
      launch(IchnoisCoreContext()) {
        IchnoisCore.setTag("c1", "c1value")
        assertEquals("c1value", getTag("c1"))
        assertNull(getTag("c2"))
        launch {
            IchnoisCore.setTag("c2", "c2value")
            assertEquals("c2value", getTag("c2"))
            assertEquals("c1value", getTag("c1"))
          }
          .join()
      }
    c1.join()
  }

  @Test
  fun testContextPassedWhileOnMainThread() {
    IchnoisCore.setTag("myKey", "myValue")
    runBlocking { assertEquals("myValue", getTag("myKey")) }
  }

  @Test
  fun testContextCanBePassedWhileOnMainThread() {
    IchnoisCore.setTag("myKey", "myValue")
    runBlocking(IchnoisCoreContext()) { assertEquals("myValue", getTag("myKey")) }
  }

  @Test
  fun testContextMayBeEmpty() {
    runBlocking(IchnoisCoreContext()) { assertNull(getTag("myKey")) }
  }

  @Test
  fun testContextIsClonedWhenPassedToChildCurrentScope() = runBlocking {
    IchnoisCore.setTag("parent", "parentValue")
    launch(IchnoisCoreContext()) {
        IchnoisCore.setTag("c1", "c1value")
        assertEquals("c1value", getTag("c1"))
        assertEquals("parentValue", getTag("parent"))
        assertNull(getTag("c2"))

        val c2 = launch {
          IchnoisCore.setTag("c2", "c2value")
          assertEquals("c2value", getTag("c2"))
          assertEquals("parentValue", getTag("parent"))
          assertNotNull(getTag("c1"))
        }

        c2.join()

        assertNotNull(getTag("c1"))
        assertNotNull(getTag("c2"))
      }
      .join()
    assertNotNull(getTag("parent"))
    assertNotNull(getTag("c1"))
    assertNotNull(getTag("c2"))
    return@runBlocking
  }

  @Test
  fun testContextIsClonedWhenPassedToChild() = runBlocking {
    IchnoisCore.configureScope(ScopeType.CURRENT) { scope -> scope.setTag("parent", "parentValue") }
    launch(IchnoisCoreContext()) {
        IchnoisCore.configureScope(ScopeType.CURRENT) { scope -> scope.setTag("c1", "c1value") }
        assertEquals("c1value", getTag("c1", ScopeType.CURRENT))
        assertEquals("parentValue", getTag("parent", ScopeType.CURRENT))
        assertNull(getTag("c2", ScopeType.CURRENT))

        val c2 = launch {
          IchnoisCore.configureScope(ScopeType.CURRENT) { scope -> scope.setTag("c2", "c2value") }
          assertEquals("c2value", getTag("c2", ScopeType.CURRENT))
          assertEquals("parentValue", getTag("parent", ScopeType.CURRENT))
          assertNotNull(getTag("c1", ScopeType.CURRENT))
        }

        c2.join()

        assertNotNull(getTag("c1", ScopeType.CURRENT))
        assertNull(getTag("c2", ScopeType.CURRENT))
      }
      .join()
    assertNotNull(getTag("parent", ScopeType.CURRENT))
    assertNull(getTag("c1", ScopeType.CURRENT))
    assertNull(getTag("c2", ScopeType.CURRENT))
  }

  @Test
  fun testExplicitlyPassedContextOverridesPropagatedContext() = runBlocking {
    IchnoisCore.setTag("parent", "parentValue")
    launch(IchnoisCoreContext()) {
        IchnoisCore.setTag("c1", "c1value")
        assertEquals("c1value", getTag("c1"))
        assertEquals("parentValue", getTag("parent"))
        assertNull(getTag("c2"))

        val c2 =
          launch(
            IchnoisCoreContext(
              IchnoisCore.getCurrentScopes().forkedScopes("test").also {
                it.setTag("cloned", "clonedValue")
              }
            )
          ) {
            IchnoisCore.setTag("c2", "c2value")
            assertEquals("c2value", getTag("c2"))
            assertEquals("parentValue", getTag("parent"))
            assertNotNull(getTag("c1"))
            assertNotNull(getTag("cloned"))
          }

        c2.join()

        assertNotNull(getTag("c1"))
        assertNull(getTag("c2"))
        assertNull(getTag("cloned"))
      }
      .join()

    assertNotNull(getTag("c1"))
    assertNull(getTag("c2"))
    assertNull(getTag("cloned"))
    return@runBlocking
  }

  @Test
  fun testExplicitlyPassedContextOverridesPropagatedContextCurrentScope() = runBlocking {
    IchnoisCore.configureScope(ScopeType.CURRENT) { scope -> scope.setTag("parent", "parentValue") }
    launch(IchnoisCoreContext()) {
      IchnoisCore.configureScope(ScopeType.CURRENT) { scope -> scope.setTag("c1", "c1value") }
      assertEquals("c1value", getTag("c1", ScopeType.CURRENT))
      assertEquals("parentValue", getTag("parent", ScopeType.CURRENT))
      assertNull(getTag("c2", ScopeType.CURRENT))

      val c2 =
        launch(
          IchnoisCoreContext(
            IchnoisCore.getCurrentScopes().forkedCurrentScope("test").also {
              it.configureScope(ScopeType.CURRENT) { scope ->
                scope.setTag("cloned", "clonedValue")
              }
            }
          )
        ) {
          IchnoisCore.configureScope(ScopeType.CURRENT) { scope -> scope.setTag("c2", "c2value") }
          assertEquals("c2value", getTag("c2", ScopeType.CURRENT))
          assertEquals("parentValue", getTag("parent", ScopeType.CURRENT))
          assertNotNull(getTag("c1", ScopeType.CURRENT))
          assertNotNull(getTag("cloned", ScopeType.CURRENT))
        }

      c2.join()

      assertNotNull(getTag("c1", ScopeType.CURRENT))
      assertNull(getTag("c2", ScopeType.CURRENT))
      assertNull(getTag("cloned", ScopeType.CURRENT))
    }
    assertNull(getTag("c1", ScopeType.CURRENT))
    assertNull(getTag("c2", ScopeType.CURRENT))
    assertNull(getTag("cloned", ScopeType.CURRENT))
  }

  @Test
  fun `mergeForChild returns copy of initial context if Key not present`() {
    val initialContextElement =
      IchnoisCoreContext(
        IchnoisCore.getCurrentScopes().forkedScopes("test").also { it.setTag("cloned", "clonedValue") }
      )
    val mergedContextElement = initialContextElement.mergeForChild(CoroutineName("test"))

    assertNotEquals(initialContextElement, mergedContextElement)
    assertNotNull((mergedContextElement)[initialContextElement.key])
  }

  @Test
  fun `mergeForChild returns passed context`() {
    val initialContextElement =
      IchnoisCoreContext(
        IchnoisCore.getCurrentScopes().forkedScopes("test").also { it.setTag("cloned", "clonedValue") }
      )
    val mergedContextElement = IchnoisCoreContext().mergeForChild(initialContextElement)

    assertEquals(initialContextElement, mergedContextElement)
  }

  @Test
  fun `current scope is isolated between coroutines in global hub mode`() = runBlocking {
    initForTest({ it.dsn = dsn }, true)
    IchnoisCore.configureScope(ScopeType.CURRENT) { scope -> scope.setTag("parent", "parentValue") }

    val c1 =
      launch(IchnoisCoreContext()) {
        IchnoisCore.configureScope(ScopeType.CURRENT) { scope -> scope.setTag("c1", "c1value") }
        assertEquals("c1value", getTag("c1", ScopeType.CURRENT))
        assertEquals("parentValue", getTag("parent", ScopeType.CURRENT))
        assertNull(getTag("c2", ScopeType.CURRENT))
      }
    val c2 =
      launch(IchnoisCoreContext()) {
        IchnoisCore.configureScope(ScopeType.CURRENT) { scope -> scope.setTag("c2", "c2value") }
        assertEquals("c2value", getTag("c2", ScopeType.CURRENT))
        assertEquals("parentValue", getTag("parent", ScopeType.CURRENT))
        assertNull(getTag("c1", ScopeType.CURRENT))
      }
    listOf(c1, c2).joinAll()

    assertNotNull(getTag("parent", ScopeType.CURRENT))
    assertNull(getTag("c1", ScopeType.CURRENT))
    assertNull(getTag("c2", ScopeType.CURRENT))
  }

  @Test
  fun `coroutines without IchnoisCoreContext share the root scopes in global hub mode`() = runBlocking {
    initForTest({ it.dsn = dsn }, true)
    IchnoisCore.configureScope(ScopeType.CURRENT) { scope -> scope.setTag("parent", "parentValue") }

    launch(Dispatchers.Default) {
        assertEquals("parentValue", getTag("parent", ScopeType.CURRENT))
        IchnoisCore.configureScope(ScopeType.CURRENT) { scope -> scope.setTag("child", "childValue") }
      }
      .join()

    assertEquals("childValue", getTag("child", ScopeType.CURRENT))
  }

  private fun getTag(tag: String, scopeType: ScopeType = ScopeType.ISOLATION): String? {
    var value: String? = null
    IchnoisCore.configureScope(scopeType) { value = it.tags[tag] }
    return value
  }
}
