package io.ichnois

import io.ichnois.test.initForTest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IchnoisCoreWrapperTest {
  private val dsn = "http://key@localhost/proj"
  private lateinit var executor: ExecutorService

  @BeforeTest
  fun beforeTest() {
    executor = Executors.newSingleThreadExecutor()
  }

  @AfterTest
  fun afterTest() {
    executor.shutdown()
    IchnoisCore.close()
    IchnoisCoreCrashLastRunState.getInstance().reset()
  }

  @Test
  fun `scopes is reset to state within the thread after isolated supply is done`() {
    initForTest {
      it.dsn = dsn
      it.beforeSend = IchnoisCoreOptions.BeforeSendCallback { event, hint -> event }
    }

    val mainScopes = IchnoisCore.getCurrentScopes()
    val threadedScopes = IchnoisCore.getCurrentScopes().forkedCurrentScope("test")

    executor.submit { IchnoisCore.setCurrentScopes(threadedScopes) }.get()

    assertEquals(mainScopes, IchnoisCore.getCurrentScopes())

    val callableFuture =
      CompletableFuture.supplyAsync(
        IchnoisCoreWrapper.wrapSupplier {
          assertNotEquals(mainScopes, IchnoisCore.getCurrentScopes())
          assertNotEquals(threadedScopes, IchnoisCore.getCurrentScopes())
          "Result 1"
        },
        executor,
      )

    callableFuture.join()

    executor
      .submit {
        assertNotEquals(mainScopes, IchnoisCore.getCurrentScopes())
        assertEquals(threadedScopes, IchnoisCore.getCurrentScopes())
      }
      .get()
  }

  @Test
  fun `wrapped supply async isolates Scopes`() {
    val capturedEvents = mutableListOf<IchnoisCoreEvent>()

    initForTest {
      it.dsn = dsn
      it.beforeSend = IchnoisCoreOptions.BeforeSendCallback { event, hint ->
        capturedEvents.add(event)
        event
      }
    }

    IchnoisCore.addBreadcrumb("MyOriginalBreadcrumbBefore")
    IchnoisCore.captureMessage("OriginalMessageBefore")

    val callableFuture =
      CompletableFuture.supplyAsync(
        IchnoisCoreWrapper.wrapSupplier {
          Thread.sleep(20)
          IchnoisCore.addBreadcrumb("MyClonedBreadcrumb")
          IchnoisCore.captureMessage("ClonedMessage")
          "Result 1"
        },
        executor,
      )

    val callableFuture2 =
      CompletableFuture.supplyAsync(
        IchnoisCoreWrapper.wrapSupplier {
          Thread.sleep(10)
          IchnoisCore.addBreadcrumb("MyClonedBreadcrumb2")
          IchnoisCore.captureMessage("ClonedMessage2")
          "Result 2"
        },
        executor,
      )

    IchnoisCore.addBreadcrumb("MyOriginalBreadcrumb")
    IchnoisCore.captureMessage("OriginalMessage")

    callableFuture.join()
    callableFuture2.join()

    val mainEvent = capturedEvents.firstOrNull { it.message?.formatted == "OriginalMessage" }
    val clonedEvent = capturedEvents.firstOrNull { it.message?.formatted == "ClonedMessage" }
    val clonedEvent2 = capturedEvents.firstOrNull { it.message?.formatted == "ClonedMessage2" }

    assertEquals(2, mainEvent?.breadcrumbs?.size)
    assertEquals(2, clonedEvent?.breadcrumbs?.size)
    assertEquals(2, clonedEvent2?.breadcrumbs?.size)
  }

  @Test
  fun `wrapped callable isolates Scopes`() {
    val capturedEvents = mutableListOf<IchnoisCoreEvent>()

    initForTest {
      it.dsn = dsn
      it.beforeSend = IchnoisCoreOptions.BeforeSendCallback { event, hint ->
        capturedEvents.add(event)
        event
      }
    }

    IchnoisCore.addBreadcrumb("MyOriginalBreadcrumbBefore")
    IchnoisCore.captureMessage("OriginalMessageBefore")
    println(Thread.currentThread().name)

    val future1 =
      executor.submit(
        IchnoisCoreWrapper.wrapCallable {
          Thread.sleep(20)
          IchnoisCore.addBreadcrumb("MyClonedBreadcrumb")
          IchnoisCore.captureMessage("ClonedMessage")
          "Result 1"
        }
      )

    val future2 =
      executor.submit(
        IchnoisCoreWrapper.wrapCallable {
          Thread.sleep(10)
          IchnoisCore.addBreadcrumb("MyClonedBreadcrumb2")
          IchnoisCore.captureMessage("ClonedMessage2")
          "Result 2"
        }
      )

    IchnoisCore.addBreadcrumb("MyOriginalBreadcrumb")
    IchnoisCore.captureMessage("OriginalMessage")

    future1.get()
    future2.get()

    val mainEvent = capturedEvents.firstOrNull { it.message?.formatted == "OriginalMessage" }
    val clonedEvent = capturedEvents.firstOrNull { it.message?.formatted == "ClonedMessage" }
    val clonedEvent2 = capturedEvents.firstOrNull { it.message?.formatted == "ClonedMessage2" }

    assertEquals(2, mainEvent?.breadcrumbs?.size)
    assertEquals(2, clonedEvent?.breadcrumbs?.size)
    assertEquals(2, clonedEvent2?.breadcrumbs?.size)
  }

  @Test
  fun `scopes is reset to state within the thread after isolated callable is done`() {
    initForTest { it.dsn = dsn }

    val mainScopes = IchnoisCore.getCurrentScopes()
    val threadedScopes = IchnoisCore.getCurrentScopes().forkedCurrentScope("test")

    executor.submit { IchnoisCore.setCurrentScopes(threadedScopes) }.get()

    assertEquals(mainScopes, IchnoisCore.getCurrentScopes())

    val callableFuture =
      executor.submit(
        IchnoisCoreWrapper.wrapCallable {
          assertNotEquals(mainScopes, IchnoisCore.getCurrentScopes())
          assertNotEquals(threadedScopes, IchnoisCore.getCurrentScopes())
          "Result 1"
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

  @Test
  fun `wrapped runnable isolates Scopes`() {
    val capturedEvents = mutableListOf<IchnoisCoreEvent>()

    initForTest {
      it.dsn = dsn
      it.beforeSend = IchnoisCoreOptions.BeforeSendCallback { event, hint ->
        capturedEvents.add(event)
        event
      }
    }

    IchnoisCore.addBreadcrumb("MyOriginalBreadcrumbBefore")
    IchnoisCore.captureMessage("OriginalMessageBefore")
    println(Thread.currentThread().name)

    val future1 =
      executor.submit(
        IchnoisCoreWrapper.wrapRunnable {
          Thread.sleep(20)
          IchnoisCore.addBreadcrumb("MyClonedBreadcrumb")
          IchnoisCore.captureMessage("ClonedMessage")
          "Result 1"
        }
      )

    val future2 =
      executor.submit(
        IchnoisCoreWrapper.wrapRunnable {
          Thread.sleep(10)
          IchnoisCore.addBreadcrumb("MyClonedBreadcrumb2")
          IchnoisCore.captureMessage("ClonedMessage2")
          "Result 2"
        }
      )

    IchnoisCore.addBreadcrumb("MyOriginalBreadcrumb")
    IchnoisCore.captureMessage("OriginalMessage")

    future1.get()
    future2.get()

    val mainEvent = capturedEvents.firstOrNull { it.message?.formatted == "OriginalMessage" }
    val clonedEvent = capturedEvents.firstOrNull { it.message?.formatted == "ClonedMessage" }
    val clonedEvent2 = capturedEvents.firstOrNull { it.message?.formatted == "ClonedMessage2" }

    assertEquals(2, mainEvent?.breadcrumbs?.size)
    assertEquals(2, clonedEvent?.breadcrumbs?.size)
    assertEquals(2, clonedEvent2?.breadcrumbs?.size)
  }

  @Test
  fun `scopes is reset to state within the thread after isolated runnable is done`() {
    initForTest { it.dsn = dsn }

    val mainScopes = IchnoisCore.getCurrentScopes()
    val threadedScopes = IchnoisCore.getCurrentScopes().forkedCurrentScope("test")

    executor.submit { IchnoisCore.setCurrentScopes(threadedScopes) }.get()

    assertEquals(mainScopes, IchnoisCore.getCurrentScopes())

    val runnableFuture =
      executor.submit(
        IchnoisCoreWrapper.wrapRunnable {
          assertNotEquals(mainScopes, IchnoisCore.getCurrentScopes())
          assertNotEquals(threadedScopes, IchnoisCore.getCurrentScopes())
          "Result 1"
        }
      )

    runnableFuture.get()

    executor
      .submit {
        assertNotEquals(mainScopes, IchnoisCore.getCurrentScopes())
        assertEquals(threadedScopes, IchnoisCore.getCurrentScopes())
      }
      .get()
  }
}
