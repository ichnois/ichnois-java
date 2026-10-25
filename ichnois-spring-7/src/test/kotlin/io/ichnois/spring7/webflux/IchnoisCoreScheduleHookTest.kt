package io.ichnois.spring7.webflux

import io.ichnois.IchnoisCore
import io.ichnois.test.initForTest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IchnoisCoreScheduleHookTest {
  private val dsn = "http://key@localhost/proj"
  private lateinit var executor: ExecutorService

  @BeforeTest
  fun beforeTest() {
    executor = Executors.newSingleThreadExecutor()
  }

  @AfterTest
  fun afterTest() {
    IchnoisCore.close()
    executor.shutdown()
  }

  @Test
  fun `scopes is reset to its state within the thread after hook is done`() {
    initForTest { it.dsn = dsn }

    val sut = IchnoisCoreScheduleHook()

    val mainScopes = IchnoisCore.getCurrentScopes()
    val threadedScopes = IchnoisCore.getCurrentScopes().forkedCurrentScope("test")

    executor.submit { IchnoisCore.setCurrentScopes(threadedScopes) }.get()

    assertEquals(mainScopes, IchnoisCore.getCurrentScopes())

    val callableFuture =
      executor.submit(
        sut.apply {
          assertNotEquals(mainScopes, IchnoisCore.getCurrentScopes())
          assertNotEquals(threadedScopes, IchnoisCore.getCurrentScopes())
        }
      )

    callableFuture.get()

    executor
      .submit {
        assertNotEquals(mainScopes, IchnoisCore.getCurrentScopes())
        assertEquals(threadedScopes, IchnoisCore.getCurrentScopes())
      }
      .get()
  }
}
