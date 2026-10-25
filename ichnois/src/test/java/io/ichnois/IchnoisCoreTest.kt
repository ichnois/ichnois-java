package io.ichnois

import com.google.common.truth.Truth.assertThat
import io.ichnois.IchnoisCoreFeedbackOptions.IFormHandler
import io.ichnois.IchnoisCoreOptions.ProfilesSamplerCallback
import io.ichnois.IchnoisCoreOptions.TracesSamplerCallback
import io.ichnois.backpressure.BackpressureMonitor
import io.ichnois.backpressure.NoOpBackpressureMonitor
import io.ichnois.cache.EnvelopeCache
import io.ichnois.cache.IEnvelopeCache
import io.ichnois.internal.debugmeta.IDebugMetaLoader
import io.ichnois.internal.debugmeta.ResourcesDebugMetaLoader
import io.ichnois.internal.modules.CompositeModulesLoader
import io.ichnois.internal.modules.IModulesLoader
import io.ichnois.internal.modules.NoOpModulesLoader
import io.ichnois.protocol.Feedback
import io.ichnois.protocol.SdkVersion
import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.protocol.IchnoisCoreThread
import io.ichnois.test.ImmediateExecutorService
import io.ichnois.test.NonOverridableNoOpIchnoisCoreExecutorService
import io.ichnois.test.createIchnoisCoreClientMock
import io.ichnois.test.initForTest
import io.ichnois.test.injectForField
import io.ichnois.transport.ITransport
import io.ichnois.util.PlatformTestManipulator
import io.ichnois.util.thread.IThreadChecker
import io.ichnois.util.thread.ThreadChecker
import java.io.Closeable
import java.io.File
import java.io.FileReader
import java.nio.file.Files
import java.util.Properties
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.awaitility.kotlin.await
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IchnoisCoreTest {
  private val dsn = "http://key@localhost/proj"

  @get:Rule val tmpDir = TemporaryFolder()

  @BeforeTest
  @AfterTest
  fun beforeTest() {
    IchnoisCore.close()
    IchnoisCoreCrashLastRunState.getInstance().reset()
  }

  @Test
  fun `init multiple times calls scopes close with isRestarting true`() {
    val scopes = mock<IScopes>()
    initForTest { it.dsn = dsn }
    IchnoisCore.setCurrentScopes(scopes)
    initForTest { it.dsn = dsn }
    verify(scopes).close(eq(true))
  }

  @Test
  fun `init multiple times calls close on previous options not new`() {
    val profiler1 = mock<ITransactionProfiler>()
    val profiler2 = mock<ITransactionProfiler>()
    initForTest {
      it.dsn = dsn
      it.setTransactionProfiler(profiler1)
    }
    verify(profiler1, never()).close()

    initForTest {
      it.dsn = dsn
      it.setTransactionProfiler(profiler2)
    }
    verify(profiler2, never()).close()
    verify(profiler1).close()

    IchnoisCore.close()
    verify(profiler2).close()
  }

  @Test
  fun `init multiple times calls close on previous integrations not new`() {
    val integration1 = mock<CloseableIntegration>()
    val integration2 = mock<CloseableIntegration>()
    initForTest {
      it.dsn = dsn
      it.addIntegration(integration1)
    }
    verify(integration1, never()).close()

    initForTest {
      it.dsn = dsn
      it.addIntegration(integration2)
    }
    verify(integration2, never()).close()
    verify(integration1).close()

    IchnoisCore.close()
    verify(integration2).close()
  }

  @Test
  fun `if a single integration crashes, the SDK and other integrations are still initialized`() {
    val goodIntegrationInitialized = AtomicBoolean(false)
    val goodIntegration = Integration { scopes, options ->
      // no-op
      goodIntegrationInitialized.set(true)
    }

    val badIntegration = Integration { scopes, options ->
      throw IllegalStateException("bad integration")
    }

    initForTest {
      it.dsn = dsn
      it.integrations.clear()
      it.integrations.add(badIntegration)
      it.integrations.add(goodIntegration)
    }

    assertTrue(IchnoisCore.isEnabled())
    assertTrue(goodIntegrationInitialized.get())
  }

  interface CloseableIntegration : Integration, Closeable

  @Test
  fun `global client is enabled after restart`() {
    val scopes = mock<IScopes>()
    whenever(scopes.close()).then { IchnoisCore.getGlobalScope().client.close() }
    whenever(scopes.close(anyOrNull())).then { IchnoisCore.getGlobalScope().client.close() }

    initForTest { it.dsn = dsn }
    IchnoisCore.setCurrentScopes(scopes)
    initForTest { it.dsn = dsn }
    verify(scopes).close(eq(true))
    assertTrue(IchnoisCore.getGlobalScope().client.isEnabled)
  }

  @Test
  fun `global client is disabled after close`() {
    val scopes = mock<IScopes>()
    whenever(scopes.close()).then { IchnoisCore.getGlobalScope().client.close() }
    whenever(scopes.close(anyOrNull())).then { IchnoisCore.getGlobalScope().client.close() }

    initForTest { it.dsn = dsn }
    IchnoisCore.setCurrentScopes(scopes)
    IchnoisCore.close()
    verify(scopes).close(eq(false))
    assertFalse(IchnoisCore.getGlobalScope().client.isEnabled)
  }

  @Test
  fun `manual Logs and Metrics reach transport when automatic collection is disabled`() {
    val transport = mock<ITransport>()
    val transportFactory = mock<ITransportFactory>()
    whenever(transportFactory.create(any(), any())).thenReturn(transport)
    initForTest {
      it.dsn = dsn
      it.logs.isEnabled = false
      it.metrics.isEnabled = false
      it.setTransportFactory(transportFactory)
      it.shutdownTimeoutMillis = 5_000
    }

    IchnoisCore.logger().info("manual log")
    IchnoisCore.metrics().count("manual metric")
    IchnoisCore.flush(5_000)

    val envelopes = argumentCaptor<IchnoisCoreEnvelope>()
    verify(transport, atLeastOnce()).send(envelopes.capture())
    val itemTypes =
      envelopes.allValues.flatMap { envelope -> envelope.items.map { it.header.type } }
    assertThat(itemTypes).containsAtLeast(IchnoisCoreItemType.Log, IchnoisCoreItemType.TraceMetric)
  }

  @Test
  fun `close calls scopes close with isRestarting false`() {
    val scopes = mock<IScopes>()
    initForTest { it.dsn = dsn }
    IchnoisCore.setCurrentScopes(scopes)
    IchnoisCore.close()
    verify(scopes).close(eq(false))
  }

  @Test
  fun `outboxPath is not created during initialization`() {
    var ichnoisOptions: IchnoisCoreOptions? = null
    initForTest {
      it.dsn = dsn
      it.cacheDirPath = getTempPath()
      ichnoisOptions = it
    }

    // The outbox dir is created lazily by its consumers (file observer, native SDK), not at init.
    val file = File(ichnoisOptions!!.outboxPath!!)
    assertFalse(file.exists())
  }

  @Test
  fun `cacheDirPath is not created during initialization`() {
    var ichnoisOptions: IchnoisCoreOptions? = null
    initForTest {
      it.dsn = dsn
      it.cacheDirPath = getTempPath()
      ichnoisOptions = it
    }

    // The cache dir is created lazily on the first envelope store, not at init.
    val file = File(ichnoisOptions!!.cacheDirPath!!)
    assertFalse(file.exists())
  }

  @Test
  fun `cacheDirPathWithoutDsn is not created during initialization`() {
    var ichnoisOptions: IchnoisCoreOptions? = null
    initForTest {
      it.dsn = dsn
      it.cacheDirPath = getTempPath()
      ichnoisOptions = it
    }

    val cacheDirPathWithoutDsn = ichnoisOptions!!.cacheDirPathWithoutDsn!!
    val file = File(cacheDirPathWithoutDsn)
    assertFalse(file.exists())
  }

  @Test
  fun `Init sets SystemOutLogger if logger is NoOp and debug is enabled`() {
    var ichnoisOptions: IchnoisCoreOptions? = null
    initForTest {
      it.dsn = dsn
      it.cacheDirPath = getTempPath()
      ichnoisOptions = it
      it.setDebug(true)
    }

    assertTrue((ichnoisOptions!!.logger as DiagnosticLogger).logger is SystemOutLogger)
  }

  @Test
  fun `scope changes are isolated to a thread`() {
    initForTest { it.dsn = dsn }
    IchnoisCore.configureScope { it.setTag("a", "a") }

    CompletableFuture.runAsync {
        IchnoisCore.configureScope { it.setTag("b", "b") }

        IchnoisCore.configureScope { assertEquals(setOf("a", "b"), it.tags.keys) }
      }
      .get()

    IchnoisCore.configureScope { assertEquals(setOf("a"), it.tags.keys) }
  }

  @Test
  fun `warns about multiple IchnoisCore initializations`() {
    val logger = mock<ILogger>()
    initForTest { it.dsn = dsn }
    initForTest {
      it.dsn = dsn
      it.setDebug(true)
      it.setLogger(logger)
    }
    verify(logger)
      .log(
        eq(IchnoisCoreLevel.WARNING),
        eq("IchnoisCore has been already initialized. Previous configuration will be overwritten."),
      )
  }

  @Test
  fun `warns about multiple IchnoisCore initializations with string overload`() {
    val logger = mock<ILogger>()
    initForTest(dsn)
    initForTest {
      it.dsn = dsn
      it.setDebug(true)
      it.setLogger(logger)
    }
    verify(logger)
      .log(
        eq(IchnoisCoreLevel.WARNING),
        eq("IchnoisCore has been already initialized. Previous configuration will be overwritten."),
      )
  }

  @Test
  fun `initializes IchnoisCore using external properties`() {
    // create a ichnois.properties file in temporary folder
    val temporaryFolder = TemporaryFolder()
    temporaryFolder.create()
    val file = temporaryFolder.newFile("ichnois.properties")
    file.writeText("dsn=http://key@localhost/proj")
    // set location of the ichnois.properties file
    System.setProperty("ichnois.properties.file", file.absolutePath)

    try {
      // initialize IchnoisCore with empty DSN and enable loading properties from external sources
      initForTest { it.isEnableExternalConfiguration = true }
      assertTrue(ScopesAdapter.getInstance().isEnabled)
    } finally {
      temporaryFolder.delete()
    }
  }

  @Test
  fun `initializes IchnoisCore with enabled=false, thus disabling IchnoisCore even if dsn is set`() {
    initForTest {
      it.isEnabled = false
      it.dsn = "http://key@localhost/proj"
    }

    IchnoisCore.setTag("none", "shouldNotExist")

    var value: String? = null
    IchnoisCore.getCurrentScopes().configureScope { value = it.tags[value] }
    assertTrue(IchnoisCore.getCurrentScopes().isNoOp)
    assertNull(value)
  }

  @Test
  fun `initializes IchnoisCore with enabled=false, thus disabling IchnoisCore even if dsn is null`() {
    initForTest { it.isEnabled = false }

    IchnoisCore.setTag("none", "shouldNotExist")

    var value: String? = null
    IchnoisCore.getCurrentScopes().configureScope { value = it.tags[value] }
    assertTrue(IchnoisCore.getCurrentScopes().isNoOp)
    assertNull(value)
  }

  @Test
  fun `initializes IchnoisCore with dsn = null, throwing IllegalArgumentException`() {
    val exception = assertThrows(java.lang.IllegalArgumentException::class.java) { initForTest() }
    assertEquals(
      "DSN is required. Use empty string or set enabled to false in IchnoisCoreOptions to disable SDK.",
      exception.message,
    )
  }

  @Test
  fun `captureUserFeedback gets forwarded to client`() {
    initForTest { it.dsn = dsn }

    val client = createIchnoisCoreClientMock()
    IchnoisCore.getCurrentScopes().bindClient(client)

    val userFeedback = UserFeedback(IchnoisCoreId.EMPTY_ID)
    IchnoisCore.captureUserFeedback(userFeedback)

    verify(client).captureUserFeedback(argThat { eventId == userFeedback.eventId })
  }

  @Test
  fun `startTransaction sets operation and description`() {
    initForTest {
      it.dsn = dsn
      it.tracesSampleRate = 1.0
    }

    val transaction = IchnoisCore.startTransaction("name", "op", "desc", TransactionOptions())
    assertEquals("name", transaction.name)
    assertEquals("op", transaction.operation)
    assertEquals("desc", transaction.description)
  }

  @Test
  fun `isCrashedLastRun returns true if crashedLastRun is set`() {
    initForTest { it.dsn = dsn }

    IchnoisCoreCrashLastRunState.getInstance().setCrashedLastRun(true)

    assertTrue(IchnoisCore.isCrashedLastRun()!!)
  }

  @Test
  fun `profilingTracesDirPath should be created and cleared at initialization when profiling is enabled`() {
    val tempPath = getTempPath()
    var ichnoisOptions: IchnoisCoreOptions? = null
    initForTest {
      it.dsn = dsn
      it.profilesSampleRate = 1.0
      it.cacheDirPath = tempPath
      ichnoisOptions = it
    }

    assertTrue(File(ichnoisOptions?.profilingTracesDirPath!!).exists())
    assertTrue(File(ichnoisOptions?.profilingTracesDirPath!!).list()!!.isEmpty())
  }

  @Test
  fun `profilingTracesDirPath should be created and cleared at initialization when continuous profiling is enabled`() {
    val tempPath = getTempPath()
    var ichnoisOptions: IchnoisCoreOptions? = null
    initForTest {
      it.dsn = dsn
      it.profileSessionSampleRate = 1.0
      it.cacheDirPath = tempPath
      ichnoisOptions = it
    }

    assertTrue(File(ichnoisOptions?.profilingTracesDirPath!!).exists())
    assertTrue(File(ichnoisOptions?.profilingTracesDirPath!!).list()!!.isEmpty())
  }

  @Test
  fun `profilingTracesDirPath should not be created when no profiling is enabled`() {
    val tempPath = getTempPath()
    var ichnoisOptions: IchnoisCoreOptions? = null
    initForTest {
      it.dsn = dsn
      it.profileSessionSampleRate = 0.0
      it.cacheDirPath = tempPath
      ichnoisOptions = it
    }

    assertFalse(File(ichnoisOptions?.profilingTracesDirPath!!).exists())
  }

  @Test
  fun `only old profiles in profilingTracesDirPath should be cleared when profiling is enabled`() {
    val tempPath = getTempPath()
    val options =
      IchnoisCoreOptions().also {
        it.dsn = dsn
        it.cacheDirPath = tempPath
      }
    val dir = File(options.profilingTracesDirPath!!)
    val oldProfile = File(dir, "oldProfile")
    val newProfile = File(dir, "newProfile")

    // Create all files
    dir.mkdirs()
    oldProfile.createNewFile()
    newProfile.createNewFile()
    // Make the old profile look like it's created earlier
    oldProfile.setLastModified(10000)
    // Make the new profile look like it's created later
    newProfile.setLastModified(System.currentTimeMillis() + 10000)

    // Assert both file exist
    assertTrue(oldProfile.exists())
    assertTrue(newProfile.exists())

    initForTest {
      it.dsn = dsn
      it.profilesSampleRate = 1.0
      it.cacheDirPath = tempPath
      it.executorService = ImmediateExecutorService()
    }

    // Assert only the new profile exists
    assertFalse(oldProfile.exists())
    assertTrue(newProfile.exists())
  }

  @Test
  fun `profilingTracesDirPath should not be created and cleared when profiling is disabled`() {
    val tempPath = getTempPath()
    var ichnoisOptions: IchnoisCoreOptions? = null
    initForTest {
      it.dsn = dsn
      it.profilesSampleRate = 0.0
      it.cacheDirPath = tempPath
      ichnoisOptions = it
    }

    assertFalse(File(ichnoisOptions?.profilingTracesDirPath!!).exists())
  }

  @Test
  fun `using ichnois before calling init creates NoOpScopes but after init IchnoisCore uses a new clone`() {
    // noop as not yet initialized, caches NoOpScopes in ThreadLocal
    IchnoisCore.captureMessage("noop caused")

    assertTrue(IchnoisCore.getCurrentScopes().isNoOp)

    // init IchnoisCore in another thread
    val thread = Thread {
      initForTest {
        it.dsn = dsn
        it.isDebug = true
      }
    }
    thread.start()
    thread.join()

    IchnoisCore.captureMessage("should work now")

    val scopes = IchnoisCore.getCurrentScopes()
    assertNotNull(scopes)
    assertFalse(scopes.isNoOp)
  }

  @Test
  fun `main scopes can be cloned and does not share scope with current scopes`() {
    // noop as not yet initialized, caches NoOpScopes in ThreadLocal
    IchnoisCore.addBreadcrumb("breadcrumbNoOp")
    IchnoisCore.captureMessage("messageNoOp")

    assertTrue(IchnoisCore.getCurrentScopes().isNoOp)

    val capturedEvents = mutableListOf<IchnoisCoreEvent>()

    // init IchnoisCore in another thread
    val thread = Thread {
      initForTest {
        it.dsn = dsn
        it.isDebug = true
        it.beforeSend = IchnoisCoreOptions.BeforeSendCallback { event, hint ->
          capturedEvents.add(event)
          event
        }
      }
    }
    thread.start()
    thread.join()

    IchnoisCore.addBreadcrumb("breadcrumbCurrent")

    val scopes = IchnoisCore.getCurrentScopes()
    assertNotNull(scopes)
    assertFalse(IchnoisCore.getCurrentScopes().isNoOp)

    val forkedRootScopes = IchnoisCore.forkedRootScopes("test")
    forkedRootScopes.addBreadcrumb("breadcrumbMainClone")

    scopes.captureMessage("messageCurrent")
    forkedRootScopes.captureMessage("messageMainClone")

    assertEquals(2, capturedEvents.size)
    val mainCloneEvent = capturedEvents.firstOrNull { it.message?.formatted == "messageMainClone" }
    val currentScopesEvent = capturedEvents.firstOrNull {
      it.message?.formatted == "messageCurrent"
    }

    assertNotNull(mainCloneEvent)
    assertNotNull(mainCloneEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbMainClone" })
    assertNull(mainCloneEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbCurrent" })
    assertNull(mainCloneEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbNoOp" })

    assertNotNull(currentScopesEvent)
    assertNull(currentScopesEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbMainClone" })
    assertNotNull(currentScopesEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbCurrent" })
    assertNull(currentScopesEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbNoOp" })
  }

  @Test
  fun `main scopes is not cloned in global scopes mode and shares scope with current scopes`() {
    // noop as not yet initialized, caches NoOpScopes in ThreadLocal
    IchnoisCore.addBreadcrumb("breadcrumbNoOp")
    IchnoisCore.captureMessage("messageNoOp")

    assertTrue(IchnoisCore.getCurrentScopes().isNoOp)

    val capturedEvents = mutableListOf<IchnoisCoreEvent>()

    // init IchnoisCore in another thread
    val thread = Thread {
      initForTest(
        {
          it.dsn = dsn
          it.isDebug = true
          it.beforeSend = IchnoisCoreOptions.BeforeSendCallback { event, hint ->
            capturedEvents.add(event)
            event
          }
        },
        true,
      )
    }
    thread.start()
    thread.join()

    IchnoisCore.addBreadcrumb("breadcrumbCurrent")

    val scopes = IchnoisCore.getCurrentScopes()
    assertNotNull(scopes)
    assertFalse(scopes.isNoOp)

    val forkedRootScopes = IchnoisCore.forkedRootScopes("test")
    forkedRootScopes.addBreadcrumb("breadcrumbMainClone")

    scopes.captureMessage("messageCurrent")
    forkedRootScopes.captureMessage("messageMainClone")

    assertEquals(2, capturedEvents.size)
    val mainCloneEvent = capturedEvents.firstOrNull { it.message?.formatted == "messageMainClone" }
    val currentScopesEvent = capturedEvents.firstOrNull {
      it.message?.formatted == "messageCurrent"
    }

    assertNotNull(mainCloneEvent)
    assertNotNull(mainCloneEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbMainClone" })
    assertNotNull(mainCloneEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbCurrent" })
    assertNull(mainCloneEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbNoOp" })

    assertNotNull(currentScopesEvent)
    assertNotNull(
      currentScopesEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbMainClone" }
    )
    assertNotNull(currentScopesEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbCurrent" })
    assertNull(currentScopesEvent.breadcrumbs?.firstOrNull { it.message == "breadcrumbNoOp" })
  }

  @Test
  fun `when init is called and configure throws an exception then an error is logged`() {
    val logger = mock<ILogger>()
    val initException = Exception("init")

    initForTest(
      {
        it.dsn = dsn
        it.isDebug = true
        it.setLogger(logger)
        throw initException
      },
      true,
    )

    verify(logger).log(eq(IchnoisCoreLevel.ERROR), any(), eq(initException))
  }

  @Test
  fun `when init with a IchnoisCoreOptions Subclass is called and configure throws an exception then an error is logged`() {
    class ExtendedIchnoisCoreOptions : IchnoisCoreOptions()

    val logger = mock<ILogger>()
    val initException = Exception("init")

    IchnoisCore.init(OptionsContainer.create(ExtendedIchnoisCoreOptions::class.java)) {
      options: ExtendedIchnoisCoreOptions ->
      options.dsn = dsn
      options.isDebug = true
      options.setLogger(logger)
      throw initException
    }

    verify(logger).log(eq(IchnoisCoreLevel.ERROR), any(), eq(initException))
  }

  @Test
  fun `overrides envelope cache if it's not set`() {
    var ichnoisOptions: IchnoisCoreOptions? = null

    initForTest {
      it.dsn = dsn
      it.cacheDirPath = getTempPath()
      ichnoisOptions = it
    }

    assertTrue { ichnoisOptions!!.envelopeDiskCache is EnvelopeCache }
  }

  @Test
  fun `does not override envelope cache if it's already set`() {
    var ichnoisOptions: IchnoisCoreOptions? = null

    initForTest {
      it.dsn = dsn
      it.cacheDirPath = getTempPath()
      it.setEnvelopeDiskCache(CustomEnvelopCache())
      ichnoisOptions = it
    }

    assertTrue { ichnoisOptions!!.envelopeDiskCache is CustomEnvelopCache }
  }

  @Test
  fun `overrides modules loader if it's not set`() {
    var ichnoisOptions: IchnoisCoreOptions? = null

    initForTest {
      it.dsn = dsn
      ichnoisOptions = it
    }

    assertTrue { ichnoisOptions!!.modulesLoader is CompositeModulesLoader }
  }

  @Test
  fun `does not override modules loader if it's already set`() {
    var ichnoisOptions: IchnoisCoreOptions? = null

    initForTest {
      it.dsn = dsn
      it.setModulesLoader(CustomModulesLoader())
      ichnoisOptions = it
    }

    assertTrue { ichnoisOptions!!.modulesLoader is CustomModulesLoader }
  }

  @Test
  fun `overrides debug meta loader if it's not set`() {
    var ichnoisOptions: IchnoisCoreOptions? = null

    initForTest {
      it.dsn = dsn
      ichnoisOptions = it
    }

    assertTrue { ichnoisOptions!!.debugMetaLoader is ResourcesDebugMetaLoader }
  }

  @Test
  fun `does not override debug meta loader if it's already set`() {
    var ichnoisOptions: IchnoisCoreOptions? = null

    initForTest {
      it.dsn = dsn
      it.setDebugMetaLoader(CustomDebugMetaLoader())
      ichnoisOptions = it
    }

    assertTrue { ichnoisOptions!!.debugMetaLoader is CustomDebugMetaLoader }
  }

  @Test
  fun `overrides main thread checker if it's not set`() {
    var ichnoisOptions: IchnoisCoreOptions? = null

    initForTest {
      it.dsn = dsn
      ichnoisOptions = it
    }

    assertTrue { ichnoisOptions!!.threadChecker is ThreadChecker }
  }

  @Test
  fun `does not override main thread checker if it's already set`() {
    var ichnoisOptions: IchnoisCoreOptions? = null

    initForTest {
      it.dsn = dsn
      it.threadChecker = CustomThreadChecker()
      ichnoisOptions = it
    }

    assertTrue { ichnoisOptions!!.threadChecker is CustomThreadChecker }
  }

  @Test
  fun `overrides collector if it's not set`() {
    var ichnoisOptions: IchnoisCoreOptions? = null

    initForTest {
      it.dsn = dsn
      ichnoisOptions = it
    }

    assertTrue { ichnoisOptions!!.performanceCollectors.any { it is JavaMemoryCollector } }
  }

  @Test
  fun `does not override collector if it's already set`() {
    var ichnoisOptions: IchnoisCoreOptions? = null

    initForTest {
      it.dsn = dsn
      it.addPerformanceCollector(CustomMemoryCollector())
      ichnoisOptions = it
    }

    assertTrue { ichnoisOptions!!.performanceCollectors.any { it is CustomMemoryCollector } }
  }

  @Test
  fun `init does not throw on executor shut down`() {
    val logger = mock<ILogger>()

    initForTest {
      it.dsn = dsn
      it.profilesSampleRate = 1.0
      it.cacheDirPath = getTempPath()
      it.setLogger(logger)
      it.executorService = IchnoisCoreExecutorService()
      it.executorService.close(0)
      it.isDebug = true
    }

    verify(logger)
      .log(
        eq(IchnoisCoreLevel.ERROR),
        eq(
          "Failed to call the executor. Old profiles will not be deleted. Did you call IchnoisCore.close()?"
        ),
        any(),
      )
  }

  @Test
  fun `reportFullyDisplayed calls scopes reportFullyDisplayed`() {
    val scopes = mock<IScopes>()
    initForTest { it.dsn = dsn }
    IchnoisCore.setCurrentScopes(scopes)
    IchnoisCore.reportFullyDisplayed()
    verify(scopes).reportFullyDisplayed()
  }

  @Test
  fun `ignores executorService if it is closed`() {
    var ichnoisOptions: IchnoisCoreOptions? = null
    val executorService = mock<IIchnoisCoreExecutorService>()
    whenever(executorService.isClosed).thenReturn(true)

    initForTest {
      it.dsn = dsn
      it.executorService = executorService
      ichnoisOptions = it
    }

    assertNotEquals(executorService, ichnoisOptions!!.executorService)
  }

  @Test
  fun `accept executorService if it is not closed`() {
    var ichnoisOptions: IchnoisCoreOptions? = null
    val executorService = mock<IIchnoisCoreExecutorService>()
    whenever(executorService.isClosed).thenReturn(false)

    initForTest {
      it.dsn = dsn
      it.executorService = executorService
      ichnoisOptions = it
    }

    assertEquals(executorService, ichnoisOptions!!.executorService)
  }

  @Test
  fun `init notifies option observers`() {
    val optionsObserver = InMemoryOptionsObserver()

    initForTest {
      it.dsn = dsn

      it.executorService = ImmediateExecutorService()

      it.addOptionsObserver(optionsObserver)

      it.release = "io.ichnois.sample@1.1.0+220"
      it.proguardUuid = "uuid"
      it.dist = "220"
      it.sdkVersion = SdkVersion("ichnois.java.android", "6.13.0")
      it.environment = "debug"
      it.setTag("one", "two")
      it.sessionReplay.onErrorSampleRate = 0.5
    }

    assertEquals("io.ichnois.sample@1.1.0+220", optionsObserver.release)
    assertEquals("debug", optionsObserver.environment)
    assertEquals("220", optionsObserver.dist)
    assertEquals("uuid", optionsObserver.proguardUuid)
    assertEquals(mapOf("one" to "two"), optionsObserver.tags)
    assertEquals(SdkVersion("ichnois.java.android", "6.13.0"), optionsObserver.sdkVersion)
    assertEquals(0.5, optionsObserver.replayErrorSampleRate)
  }

  @Test
  fun `if there is work enqueued, init notifies options observers after that work is done`() {
    val optionsObserver =
      InMemoryOptionsObserver().apply {
        setRelease("io.ichnois.sample@2.0.0")
        setEnvironment("production")
      }
    val triggered = AtomicBoolean(false)

    initForTest {
      it.dsn = dsn

      it.addOptionsObserver(optionsObserver)

      it.release = "io.ichnois.sample@1.1.0+220"
      it.environment = "debug"

      it.executorService = IchnoisCoreExecutorService()
      it.executorService.submit {
        // here the values should be still old. IchnoisCore.init will submit another runnable
        // to notify the options observers, but because the executor is single-threaded, the
        // work will be enqueued and the observers will be notified after current work is
        // finished, ensuring that even if something is using the options observer from a
        // different thread, it will still use the old values.
        Thread.sleep(1000L)
        assertEquals("io.ichnois.sample@2.0.0", optionsObserver.release)
        assertEquals("production", optionsObserver.environment)
        triggered.set(true)
      }
    }

    await.untilTrue(triggered)
    assertEquals("io.ichnois.sample@1.1.0+220", optionsObserver.release)
    assertEquals("debug", optionsObserver.environment)
  }

  @Test
  fun `init finalizes previous session`() {
    lateinit var previousSessionFile: File

    initForTest {
      it.dsn = dsn
      it.isDebug = true
      it.setLogger(SystemOutLogger())

      it.release = "io.ichnois.sample@2.0"
      it.cacheDirPath = tmpDir.newFolder().absolutePath

      it.executorService = ImmediateExecutorService()

      previousSessionFile = EnvelopeCache.getPreviousSessionFile(it.cacheDirPath!!)
      previousSessionFile.parentFile.mkdirs()
      it.serializer.serialize(
        Session(null, null, "release", "io.ichnois.samples@2.0"),
        previousSessionFile.bufferedWriter(),
      )
      assertEquals(
        "release",
        it.serializer
          .deserialize(previousSessionFile.bufferedReader(), Session::class.java)!!
          .environment,
      )
      it.sessionFlushTimeoutMillis = 100
    }

    assertFalse(previousSessionFile.exists())
  }

  @Test
  fun `if there is work enqueued, init finalizes previous session after that work is done`() {
    lateinit var previousSessionFile: File
    val triggered = AtomicBoolean(false)

    initForTest {
      it.dsn = dsn

      it.release = "io.ichnois.sample@2.0"
      it.cacheDirPath = tmpDir.newFolder().absolutePath

      previousSessionFile = EnvelopeCache.getPreviousSessionFile(it.cacheDirPath!!)
      previousSessionFile.parentFile.mkdirs()
      it.serializer.serialize(
        Session(null, null, "release", "io.ichnois.sample@1.0"),
        previousSessionFile.bufferedWriter(),
      )

      it.executorService = IchnoisCoreExecutorService()
      it.executorService.submit {
        // here the previous session should still exist. IchnoisCore.init will submit another runnable
        // to finalize the previous session, but because the executor is single-threaded, the
        // work will be enqueued and the previous session will be finalized after current work is
        // finished, ensuring that even if something is using the previous session from a
        // different thread, it will still be able to access it.
        Thread.sleep(1000L)
        val session =
          it.serializer.deserialize(previousSessionFile.bufferedReader(), Session::class.java)
        assertEquals("io.ichnois.sample@1.0", session!!.release)
        assertEquals("release", session.environment)
        triggered.set(true)
      }
    }

    await.untilTrue(triggered)
    // The PreviousSessionFinalizer runs as a separate task after the test's task in the
    // single-threaded executor, so we need to wait for it to delete the file too.
    await.until { !previousSessionFile.exists() }
  }

  @Test
  fun `captureFeedback gets forwarded to client`() {
    initForTest { it.dsn = dsn }

    val client = createIchnoisCoreClientMock()
    IchnoisCore.getCurrentScopes().bindClient(client)

    val feedback = Feedback("message")
    val hint = Hint()

    IchnoisCore.captureFeedback(feedback)
    IchnoisCore.captureFeedback(feedback, hint)
    IchnoisCore.captureFeedback(feedback, hint) { it.setTag("testKey", "testValue") }

    verify(client).captureFeedback(eq(feedback), eq(null), anyOrNull())
    verify(client)
      .captureFeedback(
        eq(feedback),
        eq(hint),
        check { assertFalse(it.tags.containsKey("testKey")) },
      )
    verify(client)
      .captureFeedback(
        eq(feedback),
        eq(hint),
        check { assertEquals("testValue", it.tags["testKey"]) },
      )
  }

  @Test
  fun `captureCheckIn gets forwarded to client`() {
    initForTest { it.dsn = dsn }

    val client = createIchnoisCoreClientMock()
    IchnoisCore.getCurrentScopes().bindClient(client)

    val checkIn = CheckIn("some_slug", CheckInStatus.OK)
    IchnoisCore.captureCheckIn(checkIn)

    verify(client)
      .captureCheckIn(argThat { checkInId == checkIn.checkInId }, anyOrNull(), anyOrNull())
  }

  @Test
  fun `if send modules is false, uses NoOpModulesLoader`() {
    var ichnoisOptions: IchnoisCoreOptions? = null
    initForTest {
      it.dsn = dsn
      it.isSendModules = false
      ichnoisOptions = it
    }

    assertIs<NoOpModulesLoader>(ichnoisOptions?.modulesLoader)
  }

  @Test
  fun `if IchnoisCore is disabled through options with scope callback is executed`() {
    initForTest { it.isEnabled = false }

    val scopeCallback = mock<ScopeCallback>()

    IchnoisCore.withScope(scopeCallback)

    verify(scopeCallback).run(any())
  }

  @Test
  fun `if IchnoisCore is not initialized with scope callback is executed`() {
    val scopeCallback = mock<ScopeCallback>()

    IchnoisCore.withScope(scopeCallback)

    verify(scopeCallback).run(any())
  }

  @Test
  fun `getSpan calls scopes getSpan`() {
    val scopes = mock<IScopes>()
    val options = IchnoisCoreOptions().also { it.dsn = dsn }
    whenever(scopes.options).thenReturn(options)

    initForTest(options)

    IchnoisCore.setCurrentScopes(scopes)
    IchnoisCore.getSpan()
    verify(scopes).span
  }

  @Test
  fun `getSpan calls returns root span if globalHubMode is enabled on Android`() {
    var ichnoisOptions: CustomAndroidOptions? = null
    PlatformTestManipulator.pretendIsAndroid(true)
    IchnoisCore.init(
      OptionsContainer.create(CustomAndroidOptions::class.java),
      {
        it.dsn = dsn
        it.tracesSampleRate = 1.0
        it.sampleRate = 1.0
        it.mockName()
        ichnoisOptions = it
      },
      true,
    )
    ichnoisOptions?.resetName()

    val transaction =
      IchnoisCore.startTransaction(
        "name",
        "op-root",
        TransactionOptions().also { it.isBindToScope = true },
      )
    transaction.startChild("op-child")

    val span = IchnoisCore.getSpan()!!
    assertEquals("op-root", span.operation)
    PlatformTestManipulator.pretendIsAndroid(false)
  }

  @Test
  fun `getSpan calls returns child span if globalHubMode is enabled, but the platform is not Android`() {
    PlatformTestManipulator.pretendIsAndroid(false)
    initForTest(
      {
        it.dsn = dsn
        it.tracesSampleRate = 1.0
        it.sampleRate = 1.0
      },
      false,
    )

    val transaction =
      IchnoisCore.startTransaction(
        "name",
        "op-root",
        TransactionOptions().also { it.isBindToScope = true },
      )
    transaction.startChild("op-child")

    val span = IchnoisCore.getSpan()!!
    assertEquals("op-child", span.operation)
  }

  @Test
  fun `getSpan calls returns child span if globalHubMode is disabled`() {
    initForTest(
      {
        it.dsn = dsn
        it.tracesSampleRate = 1.0
        it.sampleRate = 1.0
      },
      false,
    )

    val transaction =
      IchnoisCore.startTransaction(
        "name",
        "op-root",
        TransactionOptions().also { it.isBindToScope = true },
      )
    transaction.startChild("op-child")

    val span = IchnoisCore.getSpan()!!
    assertEquals("op-child", span.operation)
  }

  @Test
  fun `backpressure monitor is a NoOp if handling is disabled`() {
    var ichnoisOptions: IchnoisCoreOptions? = null
    initForTest {
      it.dsn = dsn
      it.isEnableBackpressureHandling = false
      ichnoisOptions = it
    }
    assertIs<NoOpBackpressureMonitor>(ichnoisOptions?.backpressureMonitor)
  }

  @Test
  fun `backpressure monitor is set if handling is enabled`() {
    var ichnoisOptions: IchnoisCoreOptions? = null

    initForTest {
      it.dsn = dsn
      it.isEnableBackpressureHandling = true
      ichnoisOptions = it
    }
    assertIs<BackpressureMonitor>(ichnoisOptions?.backpressureMonitor)
  }

  @Test
  fun `init calls samplers if isEnableAppStartProfiling is true`() {
    val mockSampleTracer = mock<TracesSamplerCallback>()
    val mockProfilesSampler = mock<ProfilesSamplerCallback>()
    initForTest {
      it.dsn = dsn
      it.tracesSampleRate = 1.0
      it.isEnableAppStartProfiling = true
      it.profilesSampleRate = 1.0
      it.tracesSampler = mockSampleTracer
      it.profilesSampler = mockProfilesSampler
      it.executorService = ImmediateExecutorService()
      it.cacheDirPath = getTempPath()
    }
    // Samplers are called with isForNextAppStart flag set to true
    verify(mockSampleTracer)
      .sample(
        check {
          assertEquals("app.launch", it.transactionContext.name)
          assertEquals("profile", it.transactionContext.operation)
          assertTrue(it.transactionContext.isForNextAppStart)
        }
      )
    verify(mockProfilesSampler)
      .sample(
        check {
          assertEquals("app.launch", it.transactionContext.name)
          assertEquals("profile", it.transactionContext.operation)
          assertTrue(it.transactionContext.isForNextAppStart)
        }
      )
  }

  @Test
  fun `init calls samplers if isStartProfilerOnAppStart is true`() {
    val mockSampleTracer = mock<TracesSamplerCallback>()
    val mockProfilesSampler = mock<ProfilesSamplerCallback>()
    initForTest {
      it.dsn = dsn
      it.tracesSampleRate = 1.0
      it.isStartProfilerOnAppStart = true
      it.profilesSampleRate = 1.0
      it.tracesSampler = mockSampleTracer
      it.profilesSampler = mockProfilesSampler
      it.executorService = ImmediateExecutorService()
      it.cacheDirPath = getTempPath()
    }
    // Samplers are not called
    verify(mockSampleTracer, never()).sample(any())
    verify(mockProfilesSampler, never()).sample(any())
  }

  @Test
  fun `init calls app start profiling samplers in the background`() {
    val mockSampleTracer = mock<TracesSamplerCallback>()
    val mockProfilesSampler = mock<ProfilesSamplerCallback>()
    initForTest {
      it.dsn = dsn
      it.tracesSampleRate = 1.0
      it.isEnableAppStartProfiling = true
      it.profilesSampleRate = 1.0
      it.tracesSampler = mockSampleTracer
      it.profilesSampler = mockProfilesSampler
      it.executorService = NonOverridableNoOpIchnoisCoreExecutorService()
      it.cacheDirPath = getTempPath()
    }
    // Samplers are called with isForNextAppStart flag set to true
    verify(mockSampleTracer, never()).sample(any())
    verify(mockProfilesSampler, never()).sample(any())
  }

  @Test
  fun `init does not call app start profiling samplers if cache dir is null`() {
    val mockSampleTracer = mock<TracesSamplerCallback>()
    val mockProfilesSampler = mock<ProfilesSamplerCallback>()
    initForTest {
      it.dsn = dsn
      it.tracesSampleRate = 1.0
      it.isEnableAppStartProfiling = true
      it.profilesSampleRate = 1.0
      it.tracesSampler = mockSampleTracer
      it.profilesSampler = mockProfilesSampler
      it.executorService = NonOverridableNoOpIchnoisCoreExecutorService()
      it.cacheDirPath = null
    }
    // Samplers are called with isForNextAppStart flag set to true
    verify(mockSampleTracer, never()).sample(any())
    verify(mockProfilesSampler, never()).sample(any())
  }

  @Test
  fun `init does not call app start profiling samplers if performance is disabled`() {
    val logger = mock<ILogger>()
    val mockProfilesSampler = mock<ProfilesSamplerCallback>()
    initForTest {
      it.dsn = dsn
      it.tracesSampleRate = null
      it.isEnableAppStartProfiling = true
      it.profilesSampleRate = 1.0
      it.profilesSampler = mockProfilesSampler
      it.executorService = ImmediateExecutorService()
      it.cacheDirPath = getTempPath()
      it.isDebug = true
      it.setLogger(logger)
    }
    verify(logger)
      .log(eq(IchnoisCoreLevel.INFO), eq("Tracing is disabled and app start profiling will not start."))
    verify(mockProfilesSampler, never()).sample(any())
  }

  @Test
  fun `init deletes app start profiling config`() {
    val path = getTempPath()
    File(path).mkdirs()
    val appStartProfilingConfigFile = File(path, "app_start_profiling_config")
    appStartProfilingConfigFile.createNewFile()
    assertTrue(appStartProfilingConfigFile.exists())
    initForTest {
      it.dsn = dsn
      it.executorService = ImmediateExecutorService()
      it.cacheDirPath = path
    }
    assertFalse(appStartProfilingConfigFile.exists())
  }

  @Test
  fun `init creates app start profiling config if isEnableAppStartProfiling and performance is enabled`() {
    val path = getTempPath()
    File(path).mkdirs()
    val appStartProfilingConfigFile = File(path, "app_start_profiling_config")
    appStartProfilingConfigFile.createNewFile()
    assertTrue(appStartProfilingConfigFile.exists())
    initForTest {
      it.dsn = dsn
      it.cacheDirPath = path
      it.isEnableAppStartProfiling = true
      it.profilesSampleRate = 1.0
      it.tracesSampleRate = 1.0
      it.executorService = ImmediateExecutorService()
    }
    assertTrue(appStartProfilingConfigFile.exists())
  }

  @Test
  fun `init creates app start profiling config if isStartProfilerOnAppStart, even with performance disabled`() {
    val path = getTempPath()
    File(path).mkdirs()
    val appStartProfilingConfigFile = File(path, "app_start_profiling_config")
    appStartProfilingConfigFile.createNewFile()
    assertTrue(appStartProfilingConfigFile.exists())
    initForTest {
      it.dsn = dsn
      it.cacheDirPath = path
      it.isEnableAppStartProfiling = false
      it.isStartProfilerOnAppStart = true
      it.tracesSampleRate = 0.0
      it.executorService = ImmediateExecutorService()
    }
    assertTrue(appStartProfilingConfigFile.exists())
  }

  @Test
  fun `init creates app start profiling config when the cache dir does not exist yet`() {
    val path = getTempPath()
    // Profiling is left disabled on purpose: it is the only other init-time consumer that creates
    // the cache dir, so with it off nothing materializes the dir before the config is written.
    initForTest {
      it.dsn = dsn
      it.cacheDirPath = path
      it.isEnableAppStartProfiling = false
      it.isStartProfilerOnAppStart = true
      it.tracesSampleRate = 0.0
      it.profilesSampleRate = null
      it.executorService = ImmediateExecutorService()
    }
    assertTrue(File(path, "app_start_profiling_config").exists())
  }

  @Test
  fun `init saves IchnoisCoreAppStartProfilingOptions to disk`() {
    var options = IchnoisCoreOptions()
    val path = getTempPath()
    initForTest {
      it.dsn = dsn
      it.cacheDirPath = path
      it.tracesSampleRate = 0.5
      it.isEnableAppStartProfiling = true
      it.isStartProfilerOnAppStart = true
      it.profilesSampleRate = 0.2
      it.executorService = ImmediateExecutorService()
      options = it
    }
    val appStartProfilingConfigFile = File(path, "app_start_profiling_config")
    assertTrue(appStartProfilingConfigFile.exists())
    val appStartOption =
      JsonSerializer(options)
        .deserialize(
          FileReader(appStartProfilingConfigFile),
          IchnoisCoreAppStartProfilingOptions::class.java,
        )
    assertNotNull(appStartOption)
    assertEquals(0.5, appStartOption.traceSampleRate)
    assertEquals(0.2, appStartOption.profileSampleRate)
    assertTrue(appStartOption.isProfilingEnabled)
    assertTrue(appStartOption.isEnableAppStartProfiling)
    assertTrue(appStartOption.isStartProfilerOnAppStart)
  }

  @Test
  fun `init on Android throws when not using IchnoisCoreAndroidOptions`() {
    PlatformTestManipulator.pretendIsAndroid(true)
    assertFails("You are running Android. Please, use IchnoisCoreAndroid.init.") {
      initForTest { it.dsn = dsn }
    }
    PlatformTestManipulator.pretendIsAndroid(false)
  }

  @Test
  fun `init on Android works when using IchnoisCoreAndroidOptions`() {
    PlatformTestManipulator.pretendIsAndroid(true)
    val options =
      CustomAndroidOptions().also {
        it.dsn = dsn
        it.mockName()
      }
    initForTest(options)
    options.resetName()
    PlatformTestManipulator.pretendIsAndroid(false)
  }

  @Test
  fun `init on Java works when not using IchnoisCoreAndroidOptions`() {
    initForTest { it.dsn = dsn }
  }

  @Test
  fun `if globalHubMode on options is not set, uses false from init param`() {
    initForTest({ o -> o.dsn = dsn }, false)
    val s1 = IchnoisCore.forkedRootScopes("s1")
    val s2 = IchnoisCore.forkedRootScopes("s2")
    assertNotSame(s1, s2)
  }

  @Test
  fun `if globalHubMode on options is not set, uses true from init param`() {
    initForTest({ o -> o.dsn = dsn }, true)
    val s1 = IchnoisCore.forkedRootScopes("s1")
    val s2 = IchnoisCore.forkedRootScopes("s2")
    assertSame(s1, s2)
  }

  @Test
  fun `if globalHubMode on options is set, ignores false from init param`() {
    initForTest(
      { o ->
        o.dsn = dsn
        o.isGlobalHubMode = true
      },
      false,
    )
    val s1 = IchnoisCore.forkedRootScopes("s1")
    val s2 = IchnoisCore.forkedRootScopes("s2")
    assertSame(s1, s2)
  }

  @Test
  fun `if globalHubMode on options is set, ignores true from init param`() {
    initForTest(
      { o ->
        o.dsn = dsn
        o.isGlobalHubMode = false
      },
      true,
    )
    val s1 = IchnoisCore.forkedRootScopes("s1")
    val s2 = IchnoisCore.forkedRootScopes("s2")
    assertNotSame(s1, s2)
  }

  private fun initCapturingEvents(globalHubMode: Boolean): MutableList<IchnoisCoreEvent> {
    val events = mutableListOf<IchnoisCoreEvent>()
    initForTest(
      { o ->
        o.dsn = dsn
        o.beforeSend = IchnoisCoreOptions.BeforeSendCallback { event, _ ->
          events.add(event)
          null
        }
      },
      globalHubMode,
    )
    return events
  }

  @Test
  fun `withScope data is applied to events captured inside the callback`() {
    for (globalHubMode in listOf(false, true)) {
      val events = initCapturingEvents(globalHubMode)

      IchnoisCore.withScope { scope ->
        scope.setTag("http-url", "https://example.com")
        IchnoisCore.captureException(RuntimeException("failed"))
      }

      assertThat(events.single().tags).containsEntry("http-url", "https://example.com")
    }
  }

  @Test
  fun `withIsolationScope data is applied to events captured inside the callback`() {
    for (globalHubMode in listOf(false, true)) {
      val events = initCapturingEvents(globalHubMode)

      IchnoisCore.withIsolationScope { scope ->
        scope.setTag("http-url", "https://example.com")
        IchnoisCore.captureException(RuntimeException("failed"))
      }

      assertThat(events.single().tags).containsEntry("http-url", "https://example.com")
    }
  }

  @Test
  fun `withScope data does not leak into events captured after the callback`() {
    for (globalHubMode in listOf(false, true)) {
      val events = initCapturingEvents(globalHubMode)

      IchnoisCore.withScope { scope -> scope.setTag("http-url", "https://example.com") }
      IchnoisCore.captureException(RuntimeException("failed"))

      assertThat(events.single().tags?.get("http-url")).isNull()
    }
  }

  @Test
  fun `in globalHubMode scopes are not forked for a thread that has none`() {
    initForTest({ o -> o.dsn = dsn }, true)

    val fromOtherThread = CompletableFuture.supplyAsync { IchnoisCore.getCurrentScopes() }.get()

    assertSame(IchnoisCore.getCurrentScopes(), fromOtherThread)
  }

  @Test
  fun `in globalHubMode scopes left over from a closed SDK are ignored`() {
    initForTest({ o -> o.dsn = dsn }, true)
    val stale = IchnoisCore.forkedCurrentScope("stale")

    IchnoisCore.close()
    // close only clears the storage of the calling thread, other threads may still hold stale ones
    IchnoisCore.setCurrentScopes(stale)

    assertTrue(IchnoisCore.getCurrentScopes().isNoOp)
  }

  @Test
  fun `in globalHubMode scopes left over from a previous init are ignored`() {
    initForTest({ o -> o.dsn = dsn }, true)
    val stale = IchnoisCore.forkedCurrentScope("stale")

    initForTest({ o -> o.dsn = dsn }, true)
    IchnoisCore.setCurrentScopes(stale)

    assertNotSame(stale, IchnoisCore.getCurrentScopes())
  }

  @Test
  fun `startProfiler starts the continuous profiler`() {
    val profiler = mock<IContinuousProfiler>()
    initForTest {
      it.dsn = dsn
      it.setContinuousProfiler(profiler)
      it.profileSessionSampleRate = 1.0
    }
    IchnoisCore.startProfiler()
    verify(profiler).startProfiler(eq(ProfileLifecycle.MANUAL), any())
  }

  @Test
  fun `startProfiler is ignored when continuous profiling is disabled`() {
    val profiler = mock<IContinuousProfiler>()
    initForTest {
      it.dsn = dsn
      it.setContinuousProfiler(profiler)
      it.profilesSampleRate = 1.0
    }
    IchnoisCore.startProfiler()
    verify(profiler, never()).startProfiler(eq(ProfileLifecycle.MANUAL), any())
  }

  @Test
  fun `startProfiler is ignored when profile lifecycle is TRACE`() {
    val profiler = mock<IContinuousProfiler>()
    val logger = mock<ILogger>()
    initForTest {
      it.dsn = dsn
      it.setContinuousProfiler(profiler)
      it.profileSessionSampleRate = 1.0
      it.profileLifecycle = ProfileLifecycle.TRACE
      it.isDebug = true
      it.setLogger(logger)
    }
    IchnoisCore.startProfiler()
    verify(profiler, never()).startProfiler(any(), any())
    verify(logger)
      .log(
        eq(IchnoisCoreLevel.WARNING),
        eq("Profiling lifecycle is %s. Profiling cannot be started manually."),
        eq(ProfileLifecycle.TRACE.name),
      )
  }

  @Test
  fun `stopProfiler stops the continuous profiler`() {
    val profiler = mock<IContinuousProfiler>()
    initForTest {
      it.dsn = dsn
      it.setContinuousProfiler(profiler)
      it.profileSessionSampleRate = 1.0
    }
    IchnoisCore.stopProfiler()
    verify(profiler).stopProfiler(eq(ProfileLifecycle.MANUAL))
  }

  @Test
  fun `stopProfiler is ignored when continuous profiling is disabled`() {
    val profiler = mock<IContinuousProfiler>()
    initForTest {
      it.dsn = dsn
      it.setContinuousProfiler(profiler)
      it.profilesSampleRate = 1.0
    }
    IchnoisCore.stopProfiler()
    verify(profiler, never()).stopProfiler(eq(ProfileLifecycle.MANUAL))
  }

  @Test
  fun `replay API is forwarded to replay controller`() {
    val replayController = mock<ReplayController>()
    initForTest {
      it.dsn = dsn
      it.setReplayController(replayController)
    }
    IchnoisCore.replay().start()
    IchnoisCore.replay().startBuffering()
    IchnoisCore.replay().pause()
    IchnoisCore.replay().resume()
    IchnoisCore.replay().flush()
    IchnoisCore.replay().stop()

    verify(replayController).start()
    verify(replayController).startBuffering()
    verify(replayController).pause()
    verify(replayController).resume()
    verify(replayController).flush()
    verify(replayController).stop()

    IchnoisCore.replay().enableDebugMaskingOverlay()
    IchnoisCore.replay().disableDebugMaskingOverlay()
    verify(replayController).enableDebugMaskingOverlay()
    verify(replayController).disableDebugMaskingOverlay()
  }

  @Test
  fun `feedback show forwards to feedbackOptions_formHandler`() {
    val mockFormHandler = mock<IFormHandler>()
    initForTest {
      it.dsn = dsn
      it.feedbackOptions.setFormHandler(mockFormHandler)
    }
    IchnoisCore.feedback().show()
    verify(mockFormHandler).showForm(eq(null), eq(null))
  }

  @Test
  fun `feedback show forwards to feedbackOptions_formHandler with configurator`() {
    val mockFormHandler = mock<IFormHandler>()
    val configurator = mock<IchnoisCoreFeedbackOptions.OptionsConfigurator>()
    initForTest {
      it.dsn = dsn
      it.feedbackOptions.setFormHandler(mockFormHandler)
    }
    IchnoisCore.feedback().show(configurator)
    verify(mockFormHandler).showForm(eq(null), eq(configurator))
  }

  @Test
  fun `feedback show forwards to feedbackOptions_formHandler with associatedEventId and configurator`() {
    val mockFormHandler = mock<IFormHandler>()
    val configurator = mock<IchnoisCoreFeedbackOptions.OptionsConfigurator>()
    val associatedEventId = IchnoisCoreId()
    initForTest {
      it.dsn = dsn
      it.feedbackOptions.setFormHandler(mockFormHandler)
    }
    IchnoisCore.feedback().show(associatedEventId, configurator)
    verify(mockFormHandler).showForm(eq(associatedEventId), eq(configurator))
  }

  @Test
  fun `init calls movePreviousSession before registering integrations`() {
    val mockExecutorService = mock<IIchnoisCoreExecutorService>()
    val mockIntegration = mock<Integration>()

    initForTest {
      it.dsn = dsn
      it.cacheDirPath = getTempPath()
      it.isEnableAutoSessionTracking = true
      it.executorService = mockExecutorService
      it.addIntegration(mockIntegration)
    }

    // Verify that movePreviousSession is called before integration registration
    // This ensures the session move happens early in the init process
    val inOrder = inOrder(mockExecutorService, mockIntegration)
    inOrder.verify(mockExecutorService).submit(any<MovePreviousSession>())
    inOrder.verify(mockIntegration).register(any(), any())
  }

  @Test
  fun `init moves previous session to its own file`() {
    val cacheDir = tmpDir.newFolder().absolutePath
    lateinit var currentSessionFile: File

    val previousSessionFile = EnvelopeCache.getPreviousSessionFile(cacheDir)
    assertFalse(previousSessionFile.exists())

    initForTest {
      it.dsn = dsn
      it.isDebug = true
      it.setLogger(SystemOutLogger())

      it.release = "io.ichnois.sample@2.0"

      it.cacheDirPath = tmpDir.newFolder().absolutePath
      it.executorService = ImmediateExecutorService()

      currentSessionFile = EnvelopeCache.getCurrentSessionFile(it.cacheDirPath!!)
      currentSessionFile.parentFile.mkdirs()
      it.serializer.serialize(
        Session(null, null, "release", "io.ichnois.samples@2.0"),
        currentSessionFile.bufferedWriter(),
      )
      assertEquals(
        "release",
        it.serializer
          .deserialize(currentSessionFile.bufferedReader(), Session::class.java)!!
          .environment,
      )

      it.addIntegration { scopes, _ ->
        // this is just a hack to assert previous session has been moved to its own file.
        // Integrations are being registered after moving but before finalizing previous session
        // (== deleting the file) so we can check it's been moved here
        assertFalse(currentSessionFile.exists())
        assertTrue(previousSessionFile.exists())
        assertEquals(
          "release",
          it.serializer
            .deserialize(previousSessionFile.bufferedReader(), Session::class.java)!!
            .environment,
        )
      }
    }
  }

  private class InMemoryOptionsObserver : IOptionsObserver {
    var release: String? = null
      private set

    var environment: String? = null
      private set

    var proguardUuid: String? = null
      private set

    var sdkVersion: SdkVersion? = null
      private set

    var dist: String? = null
      private set

    var tags: Map<String, String> = mapOf()
      private set

    var replayErrorSampleRate: Double? = null
      private set

    override fun setRelease(release: String?) {
      this.release = release
    }

    override fun setEnvironment(environment: String?) {
      this.environment = environment
    }

    override fun setProguardUuid(proguardUuid: String?) {
      this.proguardUuid = proguardUuid
    }

    override fun setSdkVersion(sdkVersion: SdkVersion?) {
      this.sdkVersion = sdkVersion
    }

    override fun setDist(dist: String?) {
      this.dist = dist
    }

    override fun setTags(tags: MutableMap<String, String>) {
      this.tags = tags
    }

    override fun setReplayErrorSampleRate(replayErrorSampleRate: Double?) {
      this.replayErrorSampleRate = replayErrorSampleRate
    }
  }

  private class CustomThreadChecker : IThreadChecker {
    override fun isMainThread(threadId: Long): Boolean = false

    override fun isMainThread(thread: Thread): Boolean = false

    override fun isMainThread(): Boolean = false

    override fun isMainThread(ichnoisThread: IchnoisCoreThread): Boolean = false

    override fun currentThreadSystemId(): Long = 0

    override fun getCurrentThreadName(): String = ""
  }

  private class CustomMemoryCollector : IPerformanceSnapshotCollector {
    override fun setup() {}

    override fun collect(performanceCollectionData: PerformanceCollectionData) {}
  }

  private class CustomModulesLoader : IModulesLoader {
    override fun getOrLoadModules(): MutableMap<String, String>? = null
  }

  private class CustomDebugMetaLoader : IDebugMetaLoader {
    override fun loadDebugMeta(): List<Properties>? = null
  }

  private class CustomEnvelopCache : IEnvelopeCache {
    override fun iterator(): MutableIterator<IchnoisCoreEnvelope> = TODO()

    override fun store(envelope: IchnoisCoreEnvelope, hint: Hint) = Unit

    override fun discard(envelope: IchnoisCoreEnvelope) = Unit
  }

  private fun getTempPath(): String {
    val tempFile = Files.createTempDirectory("cache").toFile()
    tempFile.delete()

    // sanity check
    assertFalse(tempFile.exists())
    return tempFile.absolutePath
  }

  /**
   * Custom IchnoisCoreOptions for Android. It needs to call [mockName] to change its name in
   * io.ichnois.android.core.IchnoisCoreAndroidOptions. The name cannot be changed right away, because
   * IchnoisCore.init instantiates the options through reflection. So the name should be changed in
   * option configuration. After the test, it needs to call [resetName] to reset the name back to
   * io.ichnois.IchnoisCoreTest$CustomAndroidOptions, since it's cached internally and would break
   * subsequent tests otherwise.
   */
  private class CustomAndroidOptions : IchnoisCoreOptions() {
    init {
      resetName()
    }

    fun mockName() {
      javaClass.injectForField("name", "io.ichnois.android.core.IchnoisCoreAndroidOptions")
    }

    fun resetName() {
      javaClass.injectForField("name", "io.ichnois.IchnoisCoreTest\$CustomAndroidOptions")
    }
  }

  @Test
  fun `when scopesStorageFactory is set, it is used instead of default storage`() {
    val customStorage = mock<IScopesStorage>()
    whenever(customStorage.set(anyOrNull())).thenReturn(mock())
    whenever(customStorage.get()).thenReturn(null)

    initForTest {
      it.dsn = dsn
      it.scopesStorageFactory = IScopesStorageFactory { _ -> customStorage }
    }

    verify(customStorage).init()
    // get() always returns null here, so every later getCurrentScopes() call forks the root scopes
    // and calls set() again, e.g. the BackpressureMonitor health check on the executor
    verify(customStorage, atLeastOnce()).set(any())
  }

  @Test
  fun `when scopesStorageFactory is null, default auto-detection is used`() {
    initForTest {
      it.dsn = dsn
      it.scopesStorageFactory = null
    }

    // Should work normally with DefaultScopesStorage
    val scopes = IchnoisCore.getCurrentScopes()
    assertFalse(scopes.isNoOp)
  }

  @Test
  fun `custom scopes storage from factory is functional`() {
    val backingStorage = DefaultScopesStorage()
    val factoryCalled = AtomicBoolean(false)

    initForTest {
      it.dsn = dsn
      it.scopesStorageFactory = IScopesStorageFactory { _ ->
        factoryCalled.set(true)
        backingStorage
      }
    }

    assertTrue(factoryCalled.get())

    val scopes = IchnoisCore.getCurrentScopes()
    assertFalse(scopes.isNoOp)
  }

  // region app start extension

  private fun initWithExtender(extender: IAppStartExtender) {
    initForTest {
      it.dsn = dsn
      it.setAppStartExtender(extender)
    }
  }

  @Test
  fun `extendAppStart delegates to the app start extender`() {
    val extender = mock<IAppStartExtender>()
    initWithExtender(extender)

    IchnoisCore.extendAppStart()

    verify(extender).extendAppStart()
  }

  @Test
  fun `finishExtendedAppStart delegates to the app start extender`() {
    val extender = mock<IAppStartExtender>()
    initWithExtender(extender)

    IchnoisCore.finishExtendedAppStart()

    verify(extender).finishExtendedAppStart()
  }

  @Test
  fun `getExtendedAppStartSpan delegates to the app start extender`() {
    val span = mock<ISpan>()
    val extender = mock<IAppStartExtender>()
    whenever(extender.extendedAppStartSpan).thenReturn(span)
    initWithExtender(extender)

    assertSame(span, IchnoisCore.getExtendedAppStartSpan())
  }

  @Test
  fun `app start extension api is a no-op when the SDK is disabled`() {
    // beforeTest called IchnoisCore.close(), so the current scopes are NoOp.
    IchnoisCore.extendAppStart()
    IchnoisCore.finishExtendedAppStart()
    assertNull(IchnoisCore.getExtendedAppStartSpan())
  }

  // endregion
}
