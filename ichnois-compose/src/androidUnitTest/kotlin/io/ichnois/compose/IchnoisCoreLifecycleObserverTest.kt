package io.ichnois.compose

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import io.ichnois.android.navigation.IchnoisCoreNavigationListener
import kotlin.test.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class IchnoisCoreLifecycleObserverTest {
  class Fixture {
    val navListener = mock<IchnoisCoreNavigationListener>()
    val navController = mock<NavController>()

    fun getSut(): IchnoisCoreLifecycleObserver = IchnoisCoreLifecycleObserver(navController, navListener)
  }

  private val fixture = Fixture()

  @Test
  fun `onResume adds navigation listener`() {
    val sut = fixture.getSut()

    sut.onStateChanged(mock(), Lifecycle.Event.ON_RESUME)

    verify(fixture.navController).addOnDestinationChangedListener(fixture.navListener)
  }

  @Test
  fun `onPause removes navigation listener`() {
    val sut = fixture.getSut()

    sut.onStateChanged(mock(), Lifecycle.Event.ON_PAUSE)

    verify(fixture.navController).removeOnDestinationChangedListener(fixture.navListener)
  }

  @Test
  fun `dispose removes navigation listener`() {
    val sut = fixture.getSut()

    sut.dispose()

    verify(fixture.navController).removeOnDestinationChangedListener(fixture.navListener)
  }
}
