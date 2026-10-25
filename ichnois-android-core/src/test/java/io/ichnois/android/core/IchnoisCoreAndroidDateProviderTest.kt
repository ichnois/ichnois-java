package io.ichnois.android.core

import io.ichnois.IchnoisCoreNanotimeDate
import kotlin.test.Test
import kotlin.test.assertTrue

class IchnoisCoreAndroidDateProviderTest {
  @Test
  fun `provides IchnoisCoreInstantDate on newer Android API levels`() {
    val date = IchnoisCoreAndroidDateProvider().now()
    assertTrue(date is IchnoisCoreNanotimeDate)
  }
}
