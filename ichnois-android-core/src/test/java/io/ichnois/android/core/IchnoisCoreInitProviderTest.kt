package io.ichnois.android.core

import android.content.Context
import android.content.pm.ProviderInfo
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ichnois.IchnoisCore
import io.ichnois.test.callMethod
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.use
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.kotlin.any

@RunWith(AndroidJUnit4::class)
class IchnoisCoreInitProviderTest {
  private var ichnoisInitProvider = IchnoisCoreInitProvider()

  @BeforeTest
  fun `set up`() {
    IchnoisCore.close()
    ContextUtils.resetInstance()
  }

  @Test
  fun `when missing applicationId, IchnoisCoreInitProvider throws`() {
    val providerInfo = ProviderInfo()

    val mockContext = ContextUtilsTestHelper.createMockContext()
    providerInfo.authority = IchnoisCoreInitProvider::class.java.name
    assertFailsWith<IllegalStateException> {
      ichnoisInitProvider.attachInfo(mockContext, providerInfo)
    }
  }

  @Test
  fun `when applicationId is defined, dsn in meta-data, SDK initializes`() {
    val providerInfo = ProviderInfo()

    assertFalse(IchnoisCore.isEnabled())
    providerInfo.authority = AUTHORITY

    val metaData = Bundle()
    val mockContext = ContextUtilsTestHelper.mockMetaData(metaData = metaData)

    metaData.putString(ManifestMetadataReader.DSN, "https://key@ichnois.io/123")

    ichnoisInitProvider.attachInfo(mockContext, providerInfo)

    assertTrue(IchnoisCore.isEnabled())
  }

  @Test
  fun `when applicationId is defined, dsn in meta-data is empty, SDK doesnt initialize`() {
    val providerInfo = ProviderInfo()

    assertFalse(IchnoisCore.isEnabled())
    providerInfo.authority = AUTHORITY

    val metaData = Bundle()
    val mockContext = ContextUtilsTestHelper.mockMetaData(metaData = metaData)

    metaData.putString(ManifestMetadataReader.DSN, "")

    ichnoisInitProvider.attachInfo(mockContext, providerInfo)

    assertFalse(IchnoisCore.isEnabled())
  }

  @Test
  fun `when applicationId is defined, dsn in meta-data is not set or null, SDK throws exception`() {
    val providerInfo = ProviderInfo()

    assertFalse(IchnoisCore.isEnabled())
    providerInfo.authority = AUTHORITY

    val metaData = Bundle()
    val mockContext = ContextUtilsTestHelper.mockMetaData(metaData = metaData)

    metaData.putString(ManifestMetadataReader.DSN, null)

    assertFailsWith<IllegalArgumentException> {
      ichnoisInitProvider.attachInfo(mockContext, providerInfo)
    }
  }

  @Test
  fun `when applicationId is defined, dsn in meta-data is invalid, SDK should throw an error`() {
    val providerInfo = ProviderInfo()

    assertFalse(IchnoisCore.isEnabled())
    providerInfo.authority = AUTHORITY

    val metaData = Bundle()
    val mockContext = ContextUtilsTestHelper.mockMetaData(metaData = metaData)

    metaData.putString(ManifestMetadataReader.DSN, "invalid dsn")

    assertFailsWith<IllegalArgumentException> {
      ichnoisInitProvider.attachInfo(mockContext, providerInfo)
    }
  }

  @Test
  fun `when applicationId is defined, auto-init in meta-data is set to false, SDK doesnt initialize`() {
    val providerInfo = ProviderInfo()

    assertFalse(IchnoisCore.isEnabled())
    providerInfo.authority = AUTHORITY

    val metaData = Bundle()
    val mockContext = ContextUtilsTestHelper.mockMetaData(metaData = metaData)

    metaData.putBoolean(ManifestMetadataReader.AUTO_INIT, false)

    ichnoisInitProvider.attachInfo(mockContext, providerInfo)

    assertFalse(IchnoisCore.isEnabled())
  }

  @Test
  fun `when App context is null, do nothing`() {
    val providerInfo = ProviderInfo()

    assertFalse(IchnoisCore.isEnabled())
    providerInfo.authority = AUTHORITY

    ichnoisInitProvider.callMethod(
      "attachInfo",
      parameterTypes = arrayOf(Context::class.java, ProviderInfo::class.java),
      null,
      providerInfo,
    )

    assertFalse(IchnoisCore.isEnabled())
  }

  @Test
  fun `when applicationId is defined, ndk in meta-data is set to false, NDK doesnt initialize`() {
    val ichnoisOptions = IchnoisCoreAndroidOptions()

    val metaData = Bundle()
    val mockContext = ContextUtilsTestHelper.mockMetaData(metaData = metaData)
    metaData.putBoolean(ManifestMetadataReader.NDK_ENABLE, false)

    AndroidOptionsInitializer.loadDefaultAndMetadataOptions(ichnoisOptions, mockContext)

    val loadClass = LoadClass()
    val activityFramesTracker = ActivityFramesTracker(loadClass, ichnoisOptions)
    AndroidOptionsInitializer.installDefaultIntegrations(
      mockContext,
      ichnoisOptions,
      BuildInfoProvider(AndroidLogger()),
      loadClass,
      activityFramesTracker,
      false,
      false,
      false,
      false,
    )

    AndroidOptionsInitializer.initializeIntegrationsAndProcessors(
      ichnoisOptions,
      mockContext,
      loadClass,
      activityFramesTracker,
      false,
    )

    assertFalse(ichnoisOptions.isEnableNdk)
  }

  @Test
  fun `skips init in compose preview mode`() {
    val providerInfo = ProviderInfo()

    assertFalse(IchnoisCore.isEnabled())
    providerInfo.authority = AUTHORITY

    val metaData = Bundle()
    metaData.putString(ManifestMetadataReader.DSN, "https://key@ichnois.io/123")
    val mockContext = ContextUtilsTestHelper.mockMetaData(metaData = metaData)

    Mockito.mockStatic(ContextUtils::class.java).use { contextUtils ->
      contextUtils
        .`when`<Boolean> { ContextUtils.appIsLibraryForComposePreview(any()) }
        .thenReturn(true)
      ichnoisInitProvider.attachInfo(mockContext, providerInfo)
    }
    assertFalse(IchnoisCore.isEnabled())
  }

  companion object {
    private const val AUTHORITY = "io.ichnois.sample.IchnoisCoreInitProvider"
  }
}
