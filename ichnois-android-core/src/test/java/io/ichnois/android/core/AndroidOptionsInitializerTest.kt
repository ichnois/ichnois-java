package io.ichnois.android.core

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ichnois.CompositePerformanceCollector
import io.ichnois.DefaultCompositePerformanceCollector
import io.ichnois.IConnectionStatusProvider
import io.ichnois.IContinuousProfiler
import io.ichnois.ILogger
import io.ichnois.ISocketTagger
import io.ichnois.ITransaction
import io.ichnois.ITransactionProfiler
import io.ichnois.MainEventProcessor
import io.ichnois.NoOpContinuousProfiler
import io.ichnois.NoOpTransactionProfiler
import io.ichnois.IchnoisCoreIntegrationPackageStorage
import io.ichnois.IchnoisCoreOptions
import io.ichnois.android.core.IchnoisCoreAndroidOptions.AndroidUserFeedbackFormHandler
import io.ichnois.android.core.cache.AndroidEnvelopeCache
import io.ichnois.android.core.internal.debugmeta.AssetsDebugMetaLoader
import io.ichnois.android.core.internal.gestures.AndroidViewGestureTargetLocator
import io.ichnois.android.core.internal.modules.AssetsModulesLoader
import io.ichnois.android.core.internal.util.AndroidConnectionStatusProvider
import io.ichnois.android.core.internal.util.AndroidThreadChecker
import io.ichnois.android.core.performance.AppStartMetrics
import io.ichnois.android.fragment.FragmentLifecycleIntegration
import io.ichnois.android.replay.ReplayIntegration
import io.ichnois.android.timber.IchnoisCoreTimberIntegration
import io.ichnois.cache.IEnvelopeCache
import io.ichnois.cache.PersistingOptionsObserver
import io.ichnois.cache.PersistingScopeObserver
import io.ichnois.compose.gestures.ComposeGestureTargetLocator
import io.ichnois.internal.debugmeta.IDebugMetaLoader
import io.ichnois.internal.modules.IModulesLoader
import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.test.ImmediateExecutorService
import io.ichnois.transport.ITransportGate
import io.ichnois.util.thread.IThreadChecker
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

private const val SAAS_DSN = "https://key@o1.ingest.us.ichnois.io/1"

@RunWith(AndroidJUnit4::class)
class AndroidOptionsInitializerTest {
  class Fixture(val context: Context, private val file: File) {
    val ichnoisOptions = IchnoisCoreAndroidOptions()
    lateinit var mockContext: Context
    val logger = mock<ILogger>()

    fun initSut(
      metadata: Bundle? = null,
      hasAppContext: Boolean = true,
      useRealContext: Boolean = false,
      configureOptions: IchnoisCoreAndroidOptions.() -> Unit = {},
      configureContext: Context.() -> Unit = {},
      assets: AssetManager? = null,
    ) {
      ichnoisOptions.executorService = ImmediateExecutorService()
      mockContext =
        if (metadata != null) {
          ContextUtilsTestHelper.mockMetaData(
            mockContext = ContextUtilsTestHelper.createMockContext(hasAppContext),
            metaData = metadata,
            assets = assets,
          )
        } else {
          ContextUtilsTestHelper.createMockContext(hasAppContext)
        }
      whenever(mockContext.cacheDir).thenReturn(file)
      if (mockContext.applicationContext != null) {
        whenever(mockContext.applicationContext.cacheDir).thenReturn(file)
      }
      mockContext.configureContext()
      AndroidOptionsInitializer.loadDefaultAndMetadataOptions(
        ichnoisOptions,
        if (useRealContext) context else mockContext,
      )

      val loadClass = LoadClass()
      val activityFramesTracker = ActivityFramesTracker(loadClass, ichnoisOptions)

      AndroidOptionsInitializer.installDefaultIntegrations(
        if (useRealContext) context else mockContext,
        ichnoisOptions,
        BuildInfoProvider(AndroidLogger()),
        loadClass,
        activityFramesTracker,
        false,
        false,
        false,
        false,
      )

      ichnoisOptions.configureOptions()
      AndroidOptionsInitializer.initializeIntegrationsAndProcessors(
        ichnoisOptions,
        if (useRealContext) context else mockContext,
        loadClass,
        activityFramesTracker,
        false,
      )
    }

    fun initSutWithClassLoader(
      classesToLoad: List<String> = emptyList(),
      isFragmentAvailable: Boolean = false,
      isTimberAvailable: Boolean = false,
      isReplayAvailable: Boolean = false,
    ) {
      mockContext =
        ContextUtilsTestHelper.mockMetaData(
          mockContext = ContextUtilsTestHelper.createMockContext(hasAppContext = true),
          metaData =
            Bundle().apply { putString(ManifestMetadataReader.DSN, "https://key@ichnois.io/123") },
        )
      ichnoisOptions.isDebug = true
      val buildInfo = createBuildInfo()
      val loadClass = createClassMock(classesToLoad)
      val activityFramesTracker = ActivityFramesTracker(loadClass, ichnoisOptions)

      AndroidOptionsInitializer.loadDefaultAndMetadataOptions(
        ichnoisOptions,
        context,
        logger,
        buildInfo,
      )

      AndroidOptionsInitializer.installDefaultIntegrations(
        context,
        ichnoisOptions,
        buildInfo,
        loadClass,
        activityFramesTracker,
        isFragmentAvailable,
        isTimberAvailable,
        isReplayAvailable,
        false,
      )

      AndroidOptionsInitializer.initializeIntegrationsAndProcessors(
        ichnoisOptions,
        context,
        buildInfo,
        loadClass,
        activityFramesTracker,
        isReplayAvailable,
      )
    }

    private fun createBuildInfo(): BuildInfoProvider {
      val buildInfo = mock<BuildInfoProvider>()
      whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.LOLLIPOP)
      return buildInfo
    }

    private fun createClassMock(classes: List<String>): LoadClass {
      val loadClassMock = mock<LoadClass>()
      classes.forEach {
        whenever(loadClassMock.loadClass(eq(it), any()))
          .thenReturn(Class.forName(it, false, this::class.java.classLoader))
        whenever(loadClassMock.isClassAvailable(eq(it), any<IchnoisCoreOptions>())).thenReturn(true)
      }
      return loadClassMock
    }
  }

  private lateinit var fixture: Fixture

  @BeforeTest
  fun `set up`() {
    ContextUtils.resetInstance()
    val appContext = ApplicationProvider.getApplicationContext<Context>()
    fixture = Fixture(appContext, appContext.cacheDir)
  }

  @Test
  fun `logger set to AndroidLogger`() {
    fixture.initSut()
    val logger = IchnoisCoreOptions::class.java.declaredFields.first { it.name == "logger" }
    logger.isAccessible = true
    val loggerField = logger.get(fixture.ichnoisOptions)
    val innerLogger = loggerField.javaClass.declaredFields.first { it.name == "logger" }
    innerLogger.isAccessible = true
    assertTrue(innerLogger.get(loggerField) is AndroidLogger)
  }

  @Test
  fun `flush timeout is set to Android specific default value`() {
    fixture.initSut()
    assertEquals(
      AndroidOptionsInitializer.DEFAULT_FLUSH_TIMEOUT_MS,
      fixture.ichnoisOptions.flushTimeoutMillis,
    )
  }

  @Test
  fun `flush timeout can be overridden`() {
    fixture.initSut(configureOptions = { flushTimeoutMillis = 1234 })
    assertEquals(1234, fixture.ichnoisOptions.flushTimeoutMillis)
  }

  @Test
  fun `AndroidEventProcessor added to processors list`() {
    fixture.initSut()
    val actual =
      fixture.ichnoisOptions.eventProcessors.firstOrNull { it is DefaultAndroidEventProcessor }
    assertNotNull(actual)
  }

  @Test
  fun `PerformanceAndroidEventProcessor added to processors list`() {
    fixture.initSut()
    val actual =
      fixture.ichnoisOptions.eventProcessors.firstOrNull { it is PerformanceAndroidEventProcessor }
    assertNotNull(actual)
  }

  @Test
  fun `MainEventProcessor added to processors list and its the 1st`() {
    fixture.initSut()
    val actual = fixture.ichnoisOptions.eventProcessors.firstOrNull { it is MainEventProcessor }
    assertNotNull(actual)
  }

  @Test
  fun `ScreenshotEventProcessor added to processors list`() {
    fixture.initSut()
    val actual =
      fixture.ichnoisOptions.eventProcessors.firstOrNull { it is ScreenshotEventProcessor }
    assertNotNull(actual)
  }

  @Test
  fun `ViewHierarchyEventProcessor added to processors list`() {
    fixture.initSut()
    val actual =
      fixture.ichnoisOptions.eventProcessors.firstOrNull { it is ViewHierarchyEventProcessor }
    assertNotNull(actual)
  }

  @Test
  fun `ApplicationExitInfoProcessor added to processors list`() {
    fixture.initSut()
    val actual =
      fixture.ichnoisOptions.eventProcessors.firstOrNull { it is ApplicationExitInfoEventProcessor }
    assertNotNull(actual)
  }

  @Test
  fun `envelopesDir should be set at initialization`() {
    fixture.initSut()

    assertTrue(
      fixture.ichnoisOptions.cacheDirPath?.endsWith(
        "${File.separator}cache${File.separator}ichnois"
      )!!
    )
  }

  @Test
  fun `getCacheDir returns ichnois subfolder`() {
    fixture.initSut()
    assertTrue(
      AndroidOptionsInitializer.getCacheDir(fixture.context)
        .path
        .endsWith("${File.separator}cache${File.separator}ichnois")
    )
  }

  @Test
  fun `profilingTracesDirPath should be set at initialization`() {
    fixture.initSut()

    assertTrue(
      fixture.ichnoisOptions.profilingTracesDirPath?.endsWith(
        "${File.separator}cache${File.separator}ichnois${File.separator}profiling_traces"
      )!!
    )
    assertFalse(File(fixture.ichnoisOptions.profilingTracesDirPath!!).exists())
  }

  @Test
  fun `outboxDir should be set at initialization`() {
    fixture.initSut()

    assertTrue(
      fixture.ichnoisOptions.outboxPath?.endsWith(
        "${File.separator}cache${File.separator}ichnois${File.separator}outbox"
      )!!
    )
  }

  @Test
  fun `init should set context package name as appInclude`() {
    fixture.initSut(useRealContext = true)

    // cant mock PackageInfo, its buggy
    assertTrue(fixture.ichnoisOptions.inAppIncludes.contains("io.ichnois.android.core.test"))
  }

  @Test
  fun `init should set release if empty`() {
    fixture.initSut(useRealContext = true)

    // cant mock PackageInfo, its buggy
    assertTrue(fixture.ichnoisOptions.release!!.startsWith("io.ichnois.android.core.test@"))
  }

  @Test
  fun `init should not replace options if set on manifest`() {
    fixture.initSut(configureOptions = { release = "release" })

    assertEquals("release", fixture.ichnoisOptions.release)
  }

  @Test
  fun `init should not set context package name if it starts with android package`() {
    fixture.initSut(configureContext = { whenever(packageName).thenReturn("android.context") })

    assertFalse(fixture.ichnoisOptions.inAppIncludes.contains("android.context"))
  }

  @Test
  fun `init should set distinct id on start`() {
    fixture.initSut()

    assertNotNull(fixture.ichnoisOptions.distinctId) { assertTrue(it.isNotEmpty()) }

    val installation = File(fixture.context.filesDir, Installation.INSTALLATION)
    installation.deleteOnExit()
  }

  @Test
  fun `init should set proguard uuid id on start`() {
    fixture.initSut(
      Bundle().apply { putString(ManifestMetadataReader.PROGUARD_UUID, "proguard-uuid") },
      hasAppContext = false,
    )

    assertEquals("proguard-uuid", fixture.ichnoisOptions.proguardUuid)
  }

  @Test
  fun `init should set Android transport gate`() {
    fixture.initSut()

    assertNotNull(fixture.ichnoisOptions.transportGate)
    assertTrue(fixture.ichnoisOptions.transportGate is AndroidTransportGate)
  }

  @Config(sdk = [Build.VERSION_CODES.N_MR1])
  @Test
  fun `init on API 25 and lower sets SSLSocketFactory with bundled IchnoisCore root CAs`() {
    fixture.initSut(configureOptions = { dsn = SAAS_DSN })

    assertIs<IchnoisCoreRootCaSslSocketFactory>(fixture.ichnoisOptions.sslSocketFactory)
  }

  @Config(sdk = [Build.VERSION_CODES.N_MR1])
  @Test
  fun `init on API 25 and lower does not set SSLSocketFactory for self-hosted DSN`() {
    fixture.initSut(configureOptions = { dsn = "https://key@ichnois.example.com/1" })

    assertNull(fixture.ichnoisOptions.sslSocketFactory)
  }

  @Config(sdk = [Build.VERSION_CODES.O])
  @Test
  fun `init on API 26 and higher does not set SSLSocketFactory`() {
    fixture.initSut(configureOptions = { dsn = SAAS_DSN })

    assertNull(fixture.ichnoisOptions.sslSocketFactory)
  }

  @Config(sdk = [Build.VERSION_CODES.N_MR1])
  @Test
  fun `init on API 25 and lower keeps user provided SSLSocketFactory`() {
    val sslSocketFactory = mock<javax.net.ssl.SSLSocketFactory>()
    fixture.initSut(
      configureOptions = {
        dsn = SAAS_DSN
        this.sslSocketFactory = sslSocketFactory
      }
    )

    assertEquals(sslSocketFactory, fixture.ichnoisOptions.sslSocketFactory)
  }

  @Test
  fun `init should set Android continuous profiler`() {
    fixture.initSut()

    assertNotNull(fixture.ichnoisOptions.transactionProfiler)
    assertEquals(fixture.ichnoisOptions.transactionProfiler, NoOpTransactionProfiler.getInstance())
    assertTrue(fixture.ichnoisOptions.continuousProfiler is AndroidContinuousProfiler)
  }

  @Config(sdk = [35])
  @Test
  fun `init on API 35+ always sets PerfettoContinuousProfiler`() {
    fixture.initSut()
    assertTrue(fixture.ichnoisOptions.continuousProfiler is PerfettoContinuousProfiler)
  }

  @Config(sdk = [35])
  @Test
  fun `init on API 35+ reports PerfettoContinuousProfiling integration`() {
    IchnoisCoreIntegrationPackageStorage.getInstance().clearStorage()
    fixture.initSut()

    assertTrue(
      IchnoisCoreIntegrationPackageStorage.getInstance()
        .integrations
        .contains("PerfettoContinuousProfiling")
    )
  }

  @Config(sdk = [34])
  @Test
  fun `init below API 35 does not report PerfettoContinuousProfiling integration`() {
    IchnoisCoreIntegrationPackageStorage.getInstance().clearStorage()
    fixture.initSut(configureOptions = { isEnableLegacyProfiling = true })

    assertFalse(
      IchnoisCoreIntegrationPackageStorage.getInstance()
        .integrations
        .contains("PerfettoContinuousProfiling")
    )
  }

  @Config(sdk = [34])
  @Test
  fun `init below API 35 with enableLegacyProfiling true sets AndroidContinuousProfiler`() {
    fixture.initSut(configureOptions = { isEnableLegacyProfiling = true })
    assertTrue(fixture.ichnoisOptions.continuousProfiler is AndroidContinuousProfiler)
  }

  @Config(sdk = [34])
  @Test
  fun `init below API 35 with enableLegacyProfiling false noops profiler`() {
    fixture.initSut(configureOptions = { isEnableLegacyProfiling = false })
    assertTrue(fixture.ichnoisOptions.continuousProfiler is NoOpContinuousProfiler)
  }

  @Test
  fun `init with profilesSampleRate should set Android transaction profiler`() {
    fixture.initSut(configureOptions = { profilesSampleRate = 1.0 })

    assertNotNull(fixture.ichnoisOptions.transactionProfiler)
    assertTrue(fixture.ichnoisOptions.transactionProfiler is AndroidTransactionProfiler)
    assertEquals(fixture.ichnoisOptions.continuousProfiler, NoOpContinuousProfiler.getInstance())
  }

  @Test
  fun `init with profilesSampleRate 0 should set Android transaction profiler`() {
    fixture.initSut(configureOptions = { profilesSampleRate = 0.0 })

    assertNotNull(fixture.ichnoisOptions.transactionProfiler)
    assertTrue(fixture.ichnoisOptions.transactionProfiler is AndroidTransactionProfiler)
    assertEquals(fixture.ichnoisOptions.continuousProfiler, NoOpContinuousProfiler.getInstance())
  }

  @Test
  fun `init with profilesSampler should set Android transaction profiler`() {
    fixture.initSut(configureOptions = { profilesSampler = mock() })

    assertNotNull(fixture.ichnoisOptions.transactionProfiler)
    assertTrue(fixture.ichnoisOptions.transactionProfiler is AndroidTransactionProfiler)
    assertEquals(fixture.ichnoisOptions.continuousProfiler, NoOpContinuousProfiler.getInstance())
  }

  @Test
  fun `init with profilesSampleRate and enableLegacyProfiling false noops both profilers`() {
    fixture.initSut(
      configureOptions = {
        profilesSampleRate = 1.0
        isEnableLegacyProfiling = false
      }
    )

    assertEquals(NoOpTransactionProfiler.getInstance(), fixture.ichnoisOptions.transactionProfiler)
    assertEquals(NoOpContinuousProfiler.getInstance(), fixture.ichnoisOptions.continuousProfiler)
  }

  @Test
  fun `init with profilesSampler and enableLegacyProfiling false noops both profilers`() {
    fixture.initSut(
      configureOptions = {
        profilesSampler = mock()
        isEnableLegacyProfiling = false
      }
    )

    assertEquals(NoOpTransactionProfiler.getInstance(), fixture.ichnoisOptions.transactionProfiler)
    assertEquals(NoOpContinuousProfiler.getInstance(), fixture.ichnoisOptions.continuousProfiler)
  }

  @Test
  fun `init with profilesSampleRate and enableLegacyProfiling false closes app start profiler`() {
    val appStartProfiler = mock<ITransactionProfiler>()
    AppStartMetrics.getInstance().appStartProfiler = appStartProfiler
    fixture.initSut(
      configureOptions = {
        profilesSampleRate = 1.0
        isEnableLegacyProfiling = false
      }
    )

    assertEquals(NoOpTransactionProfiler.getInstance(), fixture.ichnoisOptions.transactionProfiler)
    verify(appStartProfiler).close()

    // AppStartMetrics should be cleared
    assertNull(AppStartMetrics.getInstance().appStartProfiler)
    assertNull(AppStartMetrics.getInstance().appStartContinuousProfiler)
  }

  @Test
  fun `init reuses transaction profiler of appStartMetrics, if exists`() {
    val appStartProfiler = mock<ITransactionProfiler>()
    AppStartMetrics.getInstance().appStartProfiler = appStartProfiler
    fixture.initSut(configureOptions = { profilesSampler = mock() })

    assertEquals(appStartProfiler, fixture.ichnoisOptions.transactionProfiler)
    assertEquals(fixture.ichnoisOptions.continuousProfiler, NoOpContinuousProfiler.getInstance())

    // AppStartMetrics should be cleared
    assertNull(AppStartMetrics.getInstance().appStartProfiler)
    assertNull(AppStartMetrics.getInstance().appStartContinuousProfiler)
  }

  @Test
  fun `init reuses continuous profiler of appStartMetrics, if exists`() {
    val appStartContinuousProfiler = mock<IContinuousProfiler>()
    AppStartMetrics.getInstance().appStartContinuousProfiler = appStartContinuousProfiler
    fixture.initSut()

    assertEquals(fixture.ichnoisOptions.transactionProfiler, NoOpTransactionProfiler.getInstance())
    assertEquals(appStartContinuousProfiler, fixture.ichnoisOptions.continuousProfiler)

    // AppStartMetrics should be cleared
    assertNull(AppStartMetrics.getInstance().appStartProfiler)
    assertNull(AppStartMetrics.getInstance().appStartContinuousProfiler)
  }

  @Test
  fun `init starts performance collector if continuous profiler of appStartMetrics is running`() {
    val appStartContinuousProfiler = mock<IContinuousProfiler>()
    val mockPerformanceCollector = mock<CompositePerformanceCollector>()
    val chunkId = IchnoisCoreId()
    whenever(appStartContinuousProfiler.isRunning()).thenReturn(true)
    whenever(appStartContinuousProfiler.chunkId).thenReturn(chunkId)

    AppStartMetrics.getInstance().appStartContinuousProfiler = appStartContinuousProfiler
    fixture.initSut(configureOptions = { compositePerformanceCollector = mockPerformanceCollector })

    verify(mockPerformanceCollector).start(eq(chunkId.toString()))
  }

  @Test
  fun `init does not start performance collector if transaction profiler of appStartMetrics is running`() {
    val appStartTransactionProfiler = mock<ITransactionProfiler>()
    val mockPerformanceCollector = mock<CompositePerformanceCollector>()
    whenever(appStartTransactionProfiler.isRunning()).thenReturn(true)

    AppStartMetrics.getInstance().appStartProfiler = appStartTransactionProfiler
    fixture.initSut(configureOptions = { compositePerformanceCollector = mockPerformanceCollector })

    verify(mockPerformanceCollector, never()).start(any<String>())
    verify(mockPerformanceCollector, never()).start(any<ITransaction>())
  }

  @Test
  fun `init with transaction profiling closes continuous profiler of appStartMetrics`() {
    val appStartContinuousProfiler = mock<IContinuousProfiler>()
    AppStartMetrics.getInstance().appStartContinuousProfiler = appStartContinuousProfiler
    fixture.initSut(configureOptions = { profilesSampler = mock() })

    assertNotNull(fixture.ichnoisOptions.transactionProfiler)
    assertNotEquals(
      NoOpTransactionProfiler.getInstance(),
      fixture.ichnoisOptions.transactionProfiler,
    )
    assertEquals(fixture.ichnoisOptions.continuousProfiler, NoOpContinuousProfiler.getInstance())

    // app start profiler is closed, because it will never be used
    verify(appStartContinuousProfiler).close(eq(true))

    // AppStartMetrics should be cleared
    assertNull(AppStartMetrics.getInstance().appStartProfiler)
    assertNull(AppStartMetrics.getInstance().appStartContinuousProfiler)
  }

  @Test
  fun `init with continuous profiling closes transaction profiler of appStartMetrics`() {
    val appStartProfiler = mock<ITransactionProfiler>()
    AppStartMetrics.getInstance().appStartProfiler = appStartProfiler
    fixture.initSut()

    assertEquals(NoOpTransactionProfiler.getInstance(), fixture.ichnoisOptions.transactionProfiler)
    assertNotNull(fixture.ichnoisOptions.continuousProfiler)
    assertNotEquals(NoOpContinuousProfiler.getInstance(), fixture.ichnoisOptions.continuousProfiler)

    // app start profiler is closed, because it will never be used
    verify(appStartProfiler).close()

    // AppStartMetrics should be cleared
    assertNull(AppStartMetrics.getInstance().appStartProfiler)
    assertNull(AppStartMetrics.getInstance().appStartContinuousProfiler)
  }

  @Test
  fun `NdkIntegration will load IchnoisCoreNdk class and add to the integration list`() {
    fixture.initSutWithClassLoader(
      classesToLoad = listOfNotNull(NdkIntegration.ICHNOIS_NDK_CLASS_NAME)
    )

    val actual = fixture.ichnoisOptions.integrations.firstOrNull { it is NdkIntegration }
    assertNotNull((actual as NdkIntegration).ichnoisNdkClass)
  }

  @Test
  fun `NdkIntegration won't be enabled, if class not found`() {
    fixture.initSutWithClassLoader(classesToLoad = emptyList())

    val actual = fixture.ichnoisOptions.integrations.firstOrNull { it is NdkIntegration }
    assertNull((actual as NdkIntegration).ichnoisNdkClass)
  }

  @Test
  fun `AnrIntegration added to integration list`() {
    fixture.initSut()

    val actual = fixture.ichnoisOptions.integrations.firstOrNull { it is AnrIntegration }
    assertNotNull(actual)
  }

  @Test
  fun `AnrIntegration is added after AppLifecycleIntegration`() {
    fixture.initSut()

    val appLifecycleIndex =
      fixture.ichnoisOptions.integrations.indexOfFirst { it is AppLifecycleIntegration }
    val anrIndex = fixture.ichnoisOptions.integrations.indexOfFirst { it is AnrIntegration }
    assertTrue { appLifecycleIndex < anrIndex }
  }

  @Test
  fun `EnvelopeFileObserverIntegration added to integration list`() {
    fixture.initSut()

    val actual =
      fixture.ichnoisOptions.integrations.firstOrNull { it is EnvelopeFileObserverIntegration }
    assertNotNull(actual)
  }

  @Test
  fun `SendCachedEnvelopeIntegration added to integration list`() {
    fixture.initSut()

    val actual =
      fixture.ichnoisOptions.integrations.firstOrNull { it is SendCachedEnvelopeIntegration }
    assertNotNull(actual)
  }

  @Test
  fun `When given Context returns a non null ApplicationContext, uses it`() {
    fixture.initSut()

    assertNotNull(fixture.mockContext)
  }

  @Test
  fun `When given Context returns a null ApplicationContext is null, keep given Context`() {
    fixture.initSut(hasAppContext = false)

    assertNotNull(fixture.mockContext)
  }

  @Test
  fun `When given Context is not an Application class, do not add ActivityLifecycleIntegration`() {
    fixture.initSut(hasAppContext = false)

    val actual =
      fixture.ichnoisOptions.integrations.firstOrNull { it is ActivityLifecycleIntegration }
    assertNull(actual)
  }

  @Test
  fun `When given Context is not an Application class, do not add ActivityBreadcrumbsIntegration`() {
    fixture.initSut(hasAppContext = false)

    val actual =
      fixture.ichnoisOptions.integrations.firstOrNull { it is ActivityBreadcrumbsIntegration }
    assertNull(actual)
  }

  @Test
  fun `When given Context is not an Application class, do not add UserInteractionIntegration`() {
    fixture.initSut(hasAppContext = false)

    val actual = fixture.ichnoisOptions.integrations.firstOrNull { it is UserInteractionIntegration }
    assertNull(actual)
  }

  @Test
  fun `FragmentLifecycleIntegration added to the integration list if available on classpath`() {
    fixture.initSutWithClassLoader(isFragmentAvailable = true)

    val actual =
      fixture.ichnoisOptions.integrations.firstOrNull { it is FragmentLifecycleIntegration }
    assertNotNull(actual)
  }

  @Test
  fun `FragmentLifecycleIntegration won't be enabled, it throws class not found`() {
    fixture.initSutWithClassLoader(isFragmentAvailable = false)

    val actual =
      fixture.ichnoisOptions.integrations.firstOrNull { it is FragmentLifecycleIntegration }
    assertNull(actual)
  }

  @Test
  fun `IchnoisCoreTimberIntegration added to the integration list if available on classpath`() {
    fixture.initSutWithClassLoader(isTimberAvailable = true)

    val actual = fixture.ichnoisOptions.integrations.firstOrNull { it is IchnoisCoreTimberIntegration }
    assertNotNull(actual)
  }

  @Test
  fun `IchnoisCoreTimberIntegration won't be enabled, it throws class not found`() {
    fixture.initSutWithClassLoader(isTimberAvailable = false)

    val actual = fixture.ichnoisOptions.integrations.firstOrNull { it is IchnoisCoreTimberIntegration }
    assertNull(actual)
  }

  @Test
  fun `ReplayIntegration added to the integration list if available on classpath`() {
    fixture.initSutWithClassLoader(isReplayAvailable = true)

    val actual = fixture.ichnoisOptions.integrations.firstOrNull { it is ReplayIntegration }
    assertNotNull(actual)
  }

  @Test
  fun `ReplayIntegration set as ReplayController if available on classpath`() {
    fixture.initSutWithClassLoader(isReplayAvailable = true)

    assertTrue(fixture.ichnoisOptions.replayController is ReplayIntegration)
  }

  @Test
  fun `ReplayIntegration won't be enabled, it throws class not found`() {
    fixture.initSutWithClassLoader(isReplayAvailable = false)

    val actual = fixture.ichnoisOptions.integrations.firstOrNull { it is ReplayIntegration }
    assertNull(actual)
  }

  @Test
  fun `AndroidEnvelopeCache is set to options`() {
    fixture.initSut()

    assertTrue { fixture.ichnoisOptions.envelopeDiskCache is AndroidEnvelopeCache }
  }

  @Test
  fun `When Activity Frames Tracking is enabled, the Activity Frames Tracker should be unavailable`() {
    fixture.initSut(
      hasAppContext = true,
      useRealContext = true,
      configureOptions = { isEnableFramesTracking = true },
    )

    val activityLifeCycleIntegration =
      fixture.ichnoisOptions.integrations.first { it is ActivityLifecycleIntegration }

    assertFalse(
      (activityLifeCycleIntegration as ActivityLifecycleIntegration)
        .activityFramesTracker
        .isFrameMetricsAggregatorAvailable
    )
  }

  @Test
  fun `When Activity Frames Tracking is enabled, the Activity Frames Tracker should be available if perfv2 is false`() {
    fixture.initSut(
      hasAppContext = true,
      useRealContext = true,
      configureOptions = {
        isEnablePerformanceV2 = false
        isEnableFramesTracking = true
      },
    )

    val activityLifeCycleIntegration =
      fixture.ichnoisOptions.integrations.first { it is ActivityLifecycleIntegration }

    assertTrue(
      (activityLifeCycleIntegration as ActivityLifecycleIntegration)
        .activityFramesTracker
        .isFrameMetricsAggregatorAvailable
    )
  }

  @Test
  fun `When Frames Tracking is disabled, the Activity Frames Tracker should not be available`() {
    fixture.initSut(
      hasAppContext = true,
      useRealContext = true,
      configureOptions = { isEnableFramesTracking = false },
    )

    val activityLifeCycleIntegration =
      fixture.ichnoisOptions.integrations.first { it is ActivityLifecycleIntegration }

    assertFalse(
      (activityLifeCycleIntegration as ActivityLifecycleIntegration)
        .activityFramesTracker
        .isFrameMetricsAggregatorAvailable
    )
  }

  @Test
  fun `When Frames Tracking is initially disabled, but enabled via configureOptions it should be unavailable`() {
    fixture.ichnoisOptions.isEnableFramesTracking = false
    fixture.initSut(
      hasAppContext = true,
      useRealContext = true,
      configureOptions = { isEnableFramesTracking = true },
    )

    val activityLifeCycleIntegration =
      fixture.ichnoisOptions.integrations.first { it is ActivityLifecycleIntegration }

    assertFalse(
      (activityLifeCycleIntegration as ActivityLifecycleIntegration)
        .activityFramesTracker
        .isFrameMetricsAggregatorAvailable
    )
  }

  @Test
  fun `When Frames Tracking is initially disabled, but enabled via configureOptions it should be available if perfv2 is false`() {
    fixture.ichnoisOptions.isEnableFramesTracking = false
    fixture.initSut(
      hasAppContext = true,
      useRealContext = true,
      configureOptions = {
        isEnablePerformanceV2 = false
        isEnableFramesTracking = true
      },
    )

    val activityLifeCycleIntegration =
      fixture.ichnoisOptions.integrations.first { it is ActivityLifecycleIntegration }

    assertTrue(
      (activityLifeCycleIntegration as ActivityLifecycleIntegration)
        .activityFramesTracker
        .isFrameMetricsAggregatorAvailable
    )
  }

  @Test
  fun `AssetsModulesLoader is set to options`() {
    fixture.initSut()

    assertTrue { fixture.ichnoisOptions.modulesLoader is AssetsModulesLoader }
  }

  @Test
  fun `AndroidThreadChecker is set to options`() {
    fixture.initSut()

    assertTrue { fixture.ichnoisOptions.threadChecker is AndroidThreadChecker }
  }

  @Test
  fun `AndroidSocketTagger is set to options`() {
    fixture.initSut()

    assertTrue { fixture.ichnoisOptions.socketTagger is AndroidSocketTagger }
  }

  @Test
  fun `AndroidLoggerBatchProcessorFactory is set to options`() {
    fixture.initSut()

    assertTrue {
      fixture.ichnoisOptions.logs.loggerBatchProcessorFactory is AndroidLoggerBatchProcessorFactory
    }
  }

  @Test
  fun `does not install ComposeGestureTargetLocator, if ichnois-compose is not available`() {
    fixture.initSutWithClassLoader()

    assertTrue { fixture.ichnoisOptions.gestureTargetLocators.size == 1 }
    assertTrue { fixture.ichnoisOptions.gestureTargetLocators[0] is AndroidViewGestureTargetLocator }
  }

  @Test
  fun `installs ComposeGestureTargetLocator, if ichnois-compose is available`() {
    fixture.initSutWithClassLoader(
      classesToLoad =
        listOf(
          AndroidOptionsInitializer.COMPOSE_CLASS_NAME,
          AndroidOptionsInitializer.ICHNOIS_COMPOSE_GESTURE_INTEGRATION_CLASS_NAME,
        )
    )

    assertTrue { fixture.ichnoisOptions.gestureTargetLocators.size == 2 }
    assertTrue { fixture.ichnoisOptions.gestureTargetLocators[0] is AndroidViewGestureTargetLocator }
    assertTrue { fixture.ichnoisOptions.gestureTargetLocators[1] is ComposeGestureTargetLocator }
  }

  @Test
  fun `AndroidMemoryCollector is set to options`() {
    fixture.initSut()

    assertTrue { fixture.ichnoisOptions.performanceCollectors.any { it is AndroidMemoryCollector } }
  }

  @Test
  fun `AndroidCpuCollector is set to options`() {
    fixture.initSut()

    assertTrue { fixture.ichnoisOptions.performanceCollectors.any { it is AndroidCpuCollector } }
  }

  @Test
  fun `DefaultCompositePerformanceCollector is set to options`() {
    fixture.initSut()

    assertIs<DefaultCompositePerformanceCollector>(
      fixture.ichnoisOptions.compositePerformanceCollector
    )
  }

  @Test
  fun `PersistingScopeObserver is set to options`() {
    fixture.initSut()

    assertTrue { fixture.ichnoisOptions.scopeObservers.any { it is PersistingScopeObserver } }
  }

  @Test
  fun `PersistingOptionsObserver is set to options`() {
    fixture.initSut()

    assertTrue { fixture.ichnoisOptions.optionsObservers.any { it is PersistingOptionsObserver } }
  }

  @Test
  fun `options cache generation observer is set when app update time is valid`() {
    val buildInfo = mock<BuildInfoProvider>()
    whenever(buildInfo.sdkInfoVersion).thenReturn(Build.VERSION_CODES.LOLLIPOP)
    ContextUtils.getPackageInfo(fixture.context, buildInfo)!!.lastUpdateTime = 1_000L

    fixture.initSut(useRealContext = true)

    assertTrue {
      fixture.ichnoisOptions.optionsObservers.any {
        it is PersistingOptionsCacheGenerationObserver
      }
    }
  }

  @Test
  fun `when cacheDir is not set, persisting observers are not set to options`() {
    fixture.initSut(configureOptions = { cacheDirPath = null })

    assertFalse(fixture.ichnoisOptions.optionsObservers.any { it is PersistingOptionsObserver })
    assertFalse(fixture.ichnoisOptions.scopeObservers.any { it is PersistingScopeObserver })
  }

  @Test
  fun `installDefaultIntegrations does not evaluate cacheDir or outboxPath when called`() {
    val mockOptions = spy(fixture.ichnoisOptions)
    AndroidOptionsInitializer.installDefaultIntegrations(
      fixture.context,
      mockOptions,
      mock(),
      mock(),
      mock(),
      false,
      false,
      false,
      false,
    )
    verify(mockOptions, never()).outboxPath
    verify(mockOptions, never()).cacheDirPath
  }

  @Config(sdk = [30])
  @Test
  fun `AnrV2Integration added to integrations list for API 30 and above`() {
    fixture.initSut(useRealContext = true)

    val anrv2Integration = fixture.ichnoisOptions.integrations.firstOrNull { it is AnrV2Integration }
    assertNotNull(anrv2Integration)

    val anrv1Integration = fixture.ichnoisOptions.integrations.firstOrNull { it is AnrIntegration }
    assertNull(anrv1Integration)
  }

  @Test
  fun `MemoryLimiterIntegration added to integrations list for API 37 and above`() {
    val options = IchnoisCoreAndroidOptions()
    val buildInfo = mock<BuildInfoProvider>()
    whenever(buildInfo.sdkInfoVersion).thenReturn(37)
    val loadClass = LoadClass()
    val activityFramesTracker = ActivityFramesTracker(loadClass, options)

    AndroidOptionsInitializer.installDefaultIntegrations(
      fixture.context,
      options,
      buildInfo,
      loadClass,
      activityFramesTracker,
      false,
      false,
      false,
      false,
    )

    val integration = options.integrations.firstOrNull { it is MemoryLimiterIntegration }

    assertNotNull(integration)
  }

  @Test
  fun `MemoryLimiterIntegration not added to integrations list below API 37`() {
    val options = IchnoisCoreAndroidOptions()
    val buildInfo = mock<BuildInfoProvider>()
    whenever(buildInfo.sdkInfoVersion).thenReturn(36)
    val loadClass = LoadClass()
    val activityFramesTracker = ActivityFramesTracker(loadClass, options)

    AndroidOptionsInitializer.installDefaultIntegrations(
      fixture.context,
      options,
      buildInfo,
      loadClass,
      activityFramesTracker,
      false,
      false,
      false,
      false,
    )

    val integration = options.integrations.firstOrNull { it is MemoryLimiterIntegration }

    assertNull(integration)
  }

  @Test
  fun `AndroidUserFeedbackFormHandler is set as feedback form handler`() {
    fixture.initSut()
    assertIs<AndroidUserFeedbackFormHandler>(fixture.ichnoisOptions.feedbackOptions.formHandler)
  }

  @Test
  fun `PersistingScopeObserver is no-op, if scope persistence is disabled`() {
    fixture.initSut(configureOptions = { isEnableScopePersistence = false })

    fixture.ichnoisOptions.findPersistingScopeObserver()?.setTags(mapOf("key" to "value"))
    assertFalse(
      File(
          AndroidOptionsInitializer.getCacheDir(fixture.context),
          PersistingScopeObserver.SCOPE_CACHE,
        )
        .exists()
    )
  }

  @Test
  fun `user options have precedence over defaults`() {
    fixture.initSut(
      configureOptions = {
        setTransportGate(mock<ITransportGate>())
        setEnvelopeDiskCache(mock<IEnvelopeCache>())
        connectionStatusProvider = mock<IConnectionStatusProvider>()
        setModulesLoader(mock<IModulesLoader>())
        setDebugMetaLoader(mock<IDebugMetaLoader>())
        threadChecker = mock<IThreadChecker>()
        setSocketTagger(mock<ISocketTagger>())
        compositePerformanceCollector = mock<CompositePerformanceCollector>()
      }
    )

    assertFalse { fixture.ichnoisOptions.transportGate is AndroidTransportGate }
    assertFalse { fixture.ichnoisOptions.envelopeDiskCache is AndroidEnvelopeCache }
    assertFalse {
      fixture.ichnoisOptions.connectionStatusProvider is AndroidConnectionStatusProvider
    }
    assertFalse { fixture.ichnoisOptions.modulesLoader is AssetsModulesLoader }
    assertFalse { fixture.ichnoisOptions.debugMetaLoader is AssetsDebugMetaLoader }
    assertFalse { fixture.ichnoisOptions.threadChecker is AndroidThreadChecker }
    assertFalse { fixture.ichnoisOptions.socketTagger is AndroidSocketTagger }
    assertFalse {
      fixture.ichnoisOptions.compositePerformanceCollector is DefaultCompositePerformanceCollector
    }
  }
}
