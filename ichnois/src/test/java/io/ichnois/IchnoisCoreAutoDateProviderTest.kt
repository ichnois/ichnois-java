package io.ichnois

import io.ichnois.util.Platform
import io.ichnois.util.PlatformTestManipulator
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

class IchnoisCoreAutoDateProviderTest {
  val javaNinePlus = Platform.isJavaNinePlus()

  @AfterTest
  fun restorePlatform() {
    PlatformTestManipulator.pretendJavaNinePlus(javaNinePlus)
  }

  @Test
  fun `uses IchnoisCoreInstantDate on Java9+`() {
    PlatformTestManipulator.pretendJavaNinePlus(true)
    val now = IchnoisCoreAutoDateProvider().now()
    assertTrue(now is IchnoisCoreInstantDate)
  }

  @Test
  fun `uses IchnoisCoreNanotimeDate on Java8`() {
    PlatformTestManipulator.pretendJavaNinePlus(false)
    val now = IchnoisCoreAutoDateProvider().now()
    assertTrue(now is IchnoisCoreNanotimeDate)
  }
}
