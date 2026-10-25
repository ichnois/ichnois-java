package io.ichnois.android.ndk

import io.ichnois.IchnoisCoreOptions
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IchnoisCoreNdkUtilTest {
  @Test
  fun `IchnoisCoreNdk adds the Ndk package into the package list`() {
    val options = IchnoisCoreOptions()
    IchnoisCoreNdkUtil.addPackage(options.sdkVersion)
    assertTrue(
      options.sdkVersion!!.packageSet.any {
        it.name == "maven:io.ichnois:ichnois-android-ndk"
        it.version == BuildConfig.VERSION_NAME
      }
    )
  }

  @Test
  fun `IchnoisCoreNdk do not add the Ndk package into the package list`() {
    val options = IchnoisCoreOptions().apply { sdkVersion = null }
    IchnoisCoreNdkUtil.addPackage(options.sdkVersion)

    assertNull(options.sdkVersion?.packageSet)
  }
}
