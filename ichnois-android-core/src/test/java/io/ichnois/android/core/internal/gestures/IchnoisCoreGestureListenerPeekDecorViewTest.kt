package io.ichnois.android.core.internal.gestures

import android.app.Activity
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ichnois.Breadcrumb
import io.ichnois.IScopes
import io.ichnois.android.core.IchnoisCoreAndroidOptions
import io.ichnois.util.LazyEvaluator
import kotlin.test.Test
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.Robolectric.buildActivity

@RunWith(AndroidJUnit4::class)
class IchnoisCoreGestureListenerPeekDecorViewTest {

  @Test
  fun `does not force decor view creation when peekDecorView returns null`() {
    // A plain Activity that never calls setContentView — peekDecorView() should return null
    val activity = buildActivity(Activity::class.java).create().get()

    // Sanity check: decor view has not been created yet
    assertNull(activity.window.peekDecorView())

    val scopes = mock<IScopes>()
    val options =
      IchnoisCoreAndroidOptions().apply {
        isEnableUserInteractionBreadcrumbs = true
        gestureTargetLocators = listOf(AndroidViewGestureTargetLocator(LazyEvaluator { true }))
        dsn = "https://key@ichnois-h2jw.onrender.com/proj"
      }

    val sut = IchnoisCoreGestureListener(activity, scopes, options)
    sut.onSingleTapUp(mock<MotionEvent>())

    // The key assertion: peekDecorView is still null — we did not force view hierarchy creation
    assertNull(activity.window.peekDecorView())

    // And no breadcrumb was captured
    verify(scopes, never()).addBreadcrumb(any<Breadcrumb>(), anyOrNull())
  }
}
