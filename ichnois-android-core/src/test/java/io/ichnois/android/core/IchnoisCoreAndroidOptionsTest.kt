package io.ichnois.android.core

import io.ichnois.ITransactionProfiler
import io.ichnois.NoOpTransactionProfiler
import io.ichnois.protocol.DebugImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.mockito.kotlin.mock

class IchnoisCoreAndroidOptionsTest {
  @Test
  fun `init should set clientName`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()

    val clientName = "${BuildConfig.ICHNOIS_ANDROID_SDK_NAME}/${BuildConfig.VERSION_NAME}"

    assertEquals(clientName, ichnoisOptions.ichnoisClientName)
  }

  @Test
  fun `init should set SdkVersion`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()
    assertNotNull(ichnoisOptions.sdkVersion)
    val sdkVersion = ichnoisOptions.sdkVersion!!

    assertEquals(BuildConfig.ICHNOIS_ANDROID_SDK_NAME, sdkVersion.name)
    assertEquals(BuildConfig.VERSION_NAME, sdkVersion.version)

    assertTrue(
      sdkVersion.packageSet.any {
        it.name == "maven:io.ichnois:ichnois-android-core" && it.version == BuildConfig.VERSION_NAME
      }
    )

    assertTrue(
      sdkVersion.packageSet.any {
        it.name == "maven:io.ichnois:ichnois" && it.version == BuildConfig.VERSION_NAME
      }
    )
  }

  @Test
  fun `init should set NoOpDebugImagesLoader`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()
    assertEquals(NoOpDebugImagesLoader.getInstance(), ichnoisOptions.debugImagesLoader)
  }

  @Test
  fun `set debugImagesLoader accepts non null value`() {
    val ichnoisOptions =
      IchnoisCoreAndroidOptions().apply { debugImagesLoader = CustomDebugImagesLoader() }
    assertNotNull(ichnoisOptions.debugImagesLoader)
  }

  @Test
  fun `init should set NoOpTransactionProfiler`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()
    assertEquals(NoOpTransactionProfiler.getInstance(), ichnoisOptions.transactionProfiler)
  }

  @Test
  fun `set transactionProfiler accepts non null value`() {
    val profiler = mock<ITransactionProfiler>()
    val ichnoisOptions = IchnoisCoreAndroidOptions().apply { setTransactionProfiler(profiler) }
    assertEquals(profiler, ichnoisOptions.transactionProfiler)
  }

  @Test
  fun `set transactionProfiler to null is ignored`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions().apply { setTransactionProfiler(null) }
    assertEquals(NoOpTransactionProfiler.getInstance(), ichnoisOptions.transactionProfiler)
  }

  @Test
  fun `set transactionProfiler multiple times is ignored`() {
    val profiler = mock<ITransactionProfiler>()
    val ichnoisOptions =
      IchnoisCoreAndroidOptions().apply {
        setTransactionProfiler(profiler)
        setTransactionProfiler(mock())
      }
    assertEquals(profiler, ichnoisOptions.transactionProfiler)
  }

  @Test
  fun `enable scope sync by default for Android`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()

    assertTrue(ichnoisOptions.isEnableScopeSync)
  }

  @Test
  fun `attach screenshots disabled by default for Android`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()

    assertFalse(ichnoisOptions.isAttachScreenshot)
  }

  @Test
  fun `user interaction tracing disabled by default for Android`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()

    assertFalse(ichnoisOptions.isEnableUserInteractionTracing)
  }

  @Test
  fun `attach view hierarchy is disabled by default for Android`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()

    assertFalse(ichnoisOptions.isAttachViewHierarchy)
  }

  @Test
  fun `native sdk name is null by default`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()
    assertNull(ichnoisOptions.nativeSdkName)
  }

  @Test
  fun `native sdk name can be properly set`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()
    ichnoisOptions.nativeSdkName = "test_ndk_name"
    assertEquals("test_ndk_name", ichnoisOptions.nativeSdkName)
  }

  @Test
  fun `native sdk name can be properly set to null`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()
    ichnoisOptions.nativeSdkName = "test_ndk_name"
    ichnoisOptions.nativeSdkName = null
    assertNull(ichnoisOptions.nativeSdkName)
  }

  @Test
  fun `enableScopeSync can be properly disabled`() {
    val options = IchnoisCoreAndroidOptions()
    options.isEnableScopeSync = false

    assertFalse(options.isEnableScopeSync)
  }

  @Test
  fun `performance v2 is enabled by default`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()
    assertTrue(ichnoisOptions.isEnablePerformanceV2)
  }

  @Test
  fun `performance v2 can be disabled`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()
    ichnoisOptions.isEnablePerformanceV2 = false
    assertFalse(ichnoisOptions.isEnablePerformanceV2)
  }

  @Test
  fun `standalone app start tracing is disabled by default`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()
    assertFalse(ichnoisOptions.isEnableStandaloneAppStartTracing)
  }

  fun `when options is initialized, enableScopeSync is enabled by default`() {
    assertTrue(IchnoisCoreAndroidOptions().isEnableScopeSync)
  }

  @Test
  fun `ndk handler option defaults to default strategy`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()
    assertEquals(
      NdkHandlerStrategy.ICHNOIS_HANDLER_STRATEGY_DEFAULT.value,
      ichnoisOptions.ndkHandlerStrategy,
    )
  }

  @Test
  fun `ndk handler strategy option can be changed`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()
    ichnoisOptions.setNativeHandlerStrategy(
      NdkHandlerStrategy.ICHNOIS_HANDLER_STRATEGY_CHAIN_AT_START
    )
    assertEquals(
      NdkHandlerStrategy.ICHNOIS_HANDLER_STRATEGY_CHAIN_AT_START.value,
      ichnoisOptions.ndkHandlerStrategy,
    )
  }

  @Test
  fun `system event breadcrumbs extras disabled by default`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()

    assertFalse(ichnoisOptions.isEnableSystemEventBreadcrumbsExtras)
  }

  @Test
  fun `system event breadcrumbs extras can be enabled`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()
    ichnoisOptions.isEnableSystemEventBreadcrumbsExtras = true
    assertTrue(ichnoisOptions.isEnableSystemEventBreadcrumbsExtras)
  }

  @Test
  fun `anr profiling sample rate is null by default`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()

    assertNull(ichnoisOptions.anrProfilingSampleRate)
    assertFalse(ichnoisOptions.isAnrProfilingEnabled)
  }

  @Test
  fun `anr profiling can be enabled via sample rate`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()
    ichnoisOptions.anrProfilingSampleRate = 1.0
    assertEquals(1.0, ichnoisOptions.anrProfilingSampleRate)
    assertTrue(ichnoisOptions.isAnrProfilingEnabled)
  }

  @Test
  fun `anr profiling can be disabled via null sample rate`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()
    ichnoisOptions.anrProfilingSampleRate = 1.0
    ichnoisOptions.anrProfilingSampleRate = null
    assertNull(ichnoisOptions.anrProfilingSampleRate)
    assertFalse(ichnoisOptions.isAnrProfilingEnabled)
  }

  @Test
  fun `anr profiling is disabled when sample rate is zero`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()
    ichnoisOptions.anrProfilingSampleRate = 0.0
    assertFalse(ichnoisOptions.isAnrProfilingEnabled)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `anr profiling rejects invalid sample rate`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()
    ichnoisOptions.anrProfilingSampleRate = 2.0
  }

  @Test
  fun `app hang tracking is disabled by default with a 5s timeout`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()
    assertFalse(ichnoisOptions.isEnableNdkAppHangTracking)
    assertEquals(5000L, ichnoisOptions.ndkAppHangTimeoutIntervalMillis)
  }

  @Test
  fun `tombstone merge time threshold defaults to 5s and is configurable`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()
    assertEquals(5000L, ichnoisOptions.tombstoneMergeTimeThresholdMillis)

    ichnoisOptions.tombstoneMergeTimeThresholdMillis = 10000L
    assertEquals(10000L, ichnoisOptions.tombstoneMergeTimeThresholdMillis)
  }

  private class CustomDebugImagesLoader : IDebugImagesLoader {
    override fun loadDebugImages(): List<DebugImage>? = null

    override fun loadDebugImagesForAddresses(addresses: Set<String>?): Set<DebugImage>? = null

    override fun clearDebugImages() {}
  }
}
