package io.ichnois.android.ndk

import io.ichnois.android.core.IchnoisCoreAndroidOptions
import io.ichnois.ndk.NdkOptions
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer

@Suppress("UnstableApiUsage")
class IchnoisCoreNdkTest {
  class Fixture {
    var capturedOptions: NdkOptions? = null

    fun getSut(
      options: IchnoisCoreAndroidOptions =
        IchnoisCoreAndroidOptions().apply {
          dsn = "https://key@ichnois-h2jw.onrender.com/proj"
          cacheDirPath = "/cache"
        },
      closure: () -> Unit,
    ) {
      Mockito.mockStatic(io.ichnois.ndk.IchnoisCoreNdk::class.java).use { utils ->
        utils
          .`when`<Any> { io.ichnois.ndk.IchnoisCoreNdk.init(any<NdkOptions>()) }
          .doAnswer { capturedOptions = it.arguments[0] as NdkOptions }
        IchnoisCoreNdk.init(options)
        closure.invoke()
      }
    }
  }

  val fixture = Fixture()

  @Test
  fun `IchnoisCoreNdk calls NDK init`() {
    fixture.getSut { assertNotNull(fixture.capturedOptions) }
  }

  @Test
  fun `IchnoisCoreNdk propagates null tracesSampleRate`() {
    fixture.getSut(
      options =
        IchnoisCoreAndroidOptions().apply {
          dsn = "https://key@ichnois-h2jw.onrender.com/proj"
          cacheDirPath = "/cache"
          tracesSampleRate = null
        }
    ) {
      assertNotNull(fixture.capturedOptions)
      assertEquals(0.0f, fixture.capturedOptions!!.tracesSampleRate, 0.0001f)
    }
  }

  @Test
  fun `IchnoisCoreNdk propagates non-null tracesSampleRate`() {
    fixture.getSut(
      options =
        IchnoisCoreAndroidOptions().apply {
          dsn = "https://key@ichnois-h2jw.onrender.com/proj"
          cacheDirPath = "/cache"
          tracesSampleRate = 0.75
        }
    ) {
      assertNotNull(fixture.capturedOptions)
      assertEquals(0.75f, fixture.capturedOptions!!.tracesSampleRate, 0.0001f)
    }
  }

  @Test
  fun `IchnoisCoreNdk does not enable app hang tracking by default`() {
    fixture.getSut {
      assertNotNull(fixture.capturedOptions)
      assertFalse(fixture.capturedOptions!!.isEnableAppHangTracking)
      assertEquals(5000L, fixture.capturedOptions!!.appHangTimeoutMillis)
    }
  }

  @Test
  fun `IchnoisCoreNdk propagates app hang tracking options`() {
    fixture.getSut(
      options =
        IchnoisCoreAndroidOptions().apply {
          dsn = "https://key@ichnois-h2jw.onrender.com/proj"
          cacheDirPath = "/cache"
          isEnableNdkAppHangTracking = true
          ndkAppHangTimeoutIntervalMillis = 2000
        }
    ) {
      assertNotNull(fixture.capturedOptions)
      assertTrue(fixture.capturedOptions!!.isEnableAppHangTracking)
      assertEquals(2000L, fixture.capturedOptions!!.appHangTimeoutMillis)
    }
  }
}
