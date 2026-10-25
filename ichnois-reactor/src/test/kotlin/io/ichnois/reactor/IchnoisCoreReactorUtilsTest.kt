package io.ichnois.reactor

import io.ichnois.IScopes
import io.ichnois.NoOpScopes
import io.ichnois.IchnoisCore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Flux
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

class IchnoisCoreReactorUtilsTest {
  @BeforeTest
  fun setup() {
    Hooks.enableAutomaticContextPropagation()
    IchnoisCore.init("https://key@ichnois-h2jw.onrender.com/proj")
  }

  @AfterTest
  fun teardown() {
    IchnoisCore.setCurrentScopes(NoOpScopes.getInstance())
  }

  @Test
  fun `propagates scopes inside mono`() {
    val scopesToUse = mock<IScopes>()
    var scopesInside: IScopes? = null
    val mono =
      IchnoisCoreReactorUtils.withIchnoisCoreScopes(
        Mono.just("hello").publishOn(Schedulers.boundedElastic()).map { it ->
          scopesInside = IchnoisCore.getCurrentScopes()
          it
        },
        scopesToUse,
      )

    assertEquals("hello", mono.block())
    assertSame(scopesToUse, scopesInside)
  }

  @Test
  fun `propagates scopes inside flux`() {
    val scopesToUse = mock<IScopes>()
    var scopesInside: IScopes? = null
    val flux =
      IchnoisCoreReactorUtils.withIchnoisCoreScopes(
        Flux.just("hello").publishOn(Schedulers.boundedElastic()).map { it ->
          scopesInside = IchnoisCore.getCurrentScopes()
          it
        },
        scopesToUse,
      )

    assertEquals("hello", flux.blockFirst())
    assertSame(scopesToUse, scopesInside)
  }

  @Test
  fun `without reactive utils scopes is not propagated to mono`() {
    val scopesToUse = mock<IScopes>()
    var scopesInside: IScopes? = null
    val mono =
      Mono.just("hello").publishOn(Schedulers.boundedElastic()).map { it ->
        scopesInside = IchnoisCore.getCurrentScopes()
        it
      }

    assertEquals("hello", mono.block())
    assertNotSame(scopesToUse, scopesInside)
  }

  @Test
  fun `without reactive utils scopes is not propagated to flux`() {
    val scopesToUse = mock<IScopes>()
    var scopesInside: IScopes? = null
    val flux =
      Flux.just("hello").publishOn(Schedulers.boundedElastic()).map { it ->
        scopesInside = IchnoisCore.getCurrentScopes()
        it
      }

    assertEquals("hello", flux.blockFirst())
    assertNotSame(scopesToUse, scopesInside)
  }

  @Test
  fun `clones scopes for mono`() {
    val mockScopes = mock<IScopes>()
    whenever(mockScopes.forkedCurrentScope(any())).thenReturn(mock<IScopes>())
    IchnoisCore.setCurrentScopes(mockScopes)
    IchnoisCoreReactorUtils.withIchnoisCore(Mono.just("hello")).block()

    verify(mockScopes).forkedCurrentScope(any())
  }

  @Test
  fun `clones scopes for flux`() {
    val mockScopes = mock<IScopes>()
    whenever(mockScopes.forkedCurrentScope(any())).thenReturn(mock<IScopes>())
    IchnoisCore.setCurrentScopes(mockScopes)
    IchnoisCoreReactorUtils.withIchnoisCore(Flux.just("hello")).blockFirst()

    verify(mockScopes).forkedCurrentScope(any())
  }
}
