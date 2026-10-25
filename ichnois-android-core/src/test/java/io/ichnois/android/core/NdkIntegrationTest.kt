package io.ichnois.android.core

import io.ichnois.ILogger
import io.ichnois.IScopes
import io.ichnois.IchnoisCoreLevel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class NdkIntegrationTest {
  private class Fixture {
    val scopes = mock<IScopes>()
    val logger = mock<ILogger>()

    fun getSut(clazz: Class<*>? = IchnoisCoreNdk::class.java): NdkIntegration = NdkIntegration(clazz)
  }

  private val fixture = Fixture()

  @Test
  fun `NdkIntegration calls init method`() {
    val integration = fixture.getSut()

    val options = getOptions()

    integration.register(fixture.scopes, options)

    verify(fixture.logger, never()).log(eq(IchnoisCoreLevel.ERROR), any<String>(), any())
    assertTrue(options.isEnableNdk)
    assertTrue(options.isEnableScopeSync)
  }

  @Test
  fun `NdkIntegration calls close method`() {
    val integration = fixture.getSut()

    val options = getOptions()

    integration.register(fixture.scopes, options)

    assertTrue(options.isEnableNdk)
    assertTrue(options.isEnableScopeSync)

    integration.close()

    verify(fixture.logger, never()).log(eq(IchnoisCoreLevel.ERROR), any<String>(), any())
    assertFalse(options.isEnableNdk)
    assertFalse(options.isEnableScopeSync)
  }

  @Test
  fun `NdkIntegration won't init if ndk integration is disabled`() {
    val integration = fixture.getSut()

    val options = getOptions(enableNdk = false)

    integration.register(fixture.scopes, options)

    verify(fixture.logger, never()).log(eq(IchnoisCoreLevel.ERROR), any<String>(), any())

    assertFalse(options.isEnableNdk)
    assertFalse(options.isEnableScopeSync)
  }

  @Test
  fun `NdkIntegration won't init if IchnoisCoreNdk class is not available`() {
    val integration = fixture.getSut(null)

    val options = getOptions()

    integration.register(fixture.scopes, options)

    verify(fixture.logger, never()).log(eq(IchnoisCoreLevel.ERROR), any<String>(), any())

    assertFalse(options.isEnableNdk)
    assertFalse(options.isEnableScopeSync)
  }

  @Test
  fun `NdkIntegration won't init if init method is not available`() {
    val integration = fixture.getSut(IchnoisCoreNdkNoInit::class.java)

    val options = getOptions()

    integration.register(fixture.scopes, options)

    verify(fixture.logger).log(eq(IchnoisCoreLevel.ERROR), any<String>(), any())

    assertFalse(options.isEnableNdk)
    assertFalse(options.isEnableScopeSync)
  }

  @Test
  fun `NdkIntegration won't close if close method is not available`() {
    val integration = fixture.getSut(IchnoisCoreNdkNoClose::class.java)

    val options = getOptions()

    integration.register(fixture.scopes, options)

    assertTrue(options.isEnableNdk)
    assertTrue(options.isEnableScopeSync)

    integration.close()

    verify(fixture.logger).log(eq(IchnoisCoreLevel.ERROR), any<String>(), any())
    assertFalse(options.isEnableNdk)
    assertFalse(options.isEnableScopeSync)
  }

  @Test
  fun `NdkIntegration won't init if init throws`() {
    val integration = fixture.getSut(IchnoisCoreNdkThrows::class.java)

    val options = getOptions()

    integration.register(fixture.scopes, options)

    verify(fixture.logger).log(eq(IchnoisCoreLevel.ERROR), any<String>(), any())

    assertFalse(options.isEnableNdk)
    assertFalse(options.isEnableScopeSync)
  }

  @Test
  fun `NdkIntegration won't init if cache dir is null`() {
    val integration = fixture.getSut()

    val options = getOptions(cacheDir = null)

    integration.register(fixture.scopes, options)

    verify(fixture.logger).log(eq(IchnoisCoreLevel.ERROR), any())

    assertFalse(options.isEnableNdk)
    assertFalse(options.isEnableScopeSync)
  }

  @Test
  fun `NdkIntegration won't init if cache dir is empty`() {
    val integration = fixture.getSut()

    val options = getOptions(cacheDir = "")

    integration.register(fixture.scopes, options)

    verify(fixture.logger).log(eq(IchnoisCoreLevel.ERROR), any())

    assertFalse(options.isEnableNdk)
    assertFalse(options.isEnableScopeSync)
  }

  private fun getOptions(
    enableNdk: Boolean = true,
    cacheDir: String? = "abc",
  ): IchnoisCoreAndroidOptions =
    IchnoisCoreAndroidOptions().apply {
      setLogger(fixture.logger)
      isDebug = true
      isEnableNdk = enableNdk
      cacheDirPath = cacheDir
    }

  private class IchnoisCoreNdkNoInit

  private class IchnoisCoreNdkThrows {
    companion object {
      @JvmStatic fun init(options: IchnoisCoreAndroidOptions): Unit = throw RuntimeException("damn")
    }
  }

  private class IchnoisCoreNdkNoClose {
    companion object {
      @JvmStatic fun init(options: IchnoisCoreAndroidOptions) {}
    }
  }
}
