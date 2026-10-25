package io.ichnois.servlet.jakarta

import io.ichnois.Breadcrumb
import io.ichnois.IScopes
import io.ichnois.IIchnoisCoreLifecycleToken
import jakarta.servlet.ServletRequestEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IchnoisCoreServletRequestListenerTest {
  private class Fixture {
    val scopes = mock<IScopes>()
    val lifecycleToken = mock<IIchnoisCoreLifecycleToken>()
    val listener = IchnoisCoreServletRequestListener(scopes)
    val request = mockRequest(url = "http://localhost:8080/some-uri", method = "POST")
    val event = mock<ServletRequestEvent>()

    init {
      whenever(event.servletRequest).thenReturn(request)
      whenever(scopes.forkedScopes(any())).thenReturn(scopes)
      whenever(scopes.makeCurrent()).thenReturn(lifecycleToken)
    }
  }

  private val fixture = Fixture()

  @Test
  fun `pushes scope when request gets initialized`() {
    fixture.listener.requestInitialized(fixture.event)

    verify(fixture.scopes).forkedScopes(any())
    verify(fixture.scopes).makeCurrent()
  }

  @Test
  fun `adds breadcrumb when request gets initialized`() {
    fixture.listener.requestInitialized(fixture.event)

    verify(fixture.scopes)
      .addBreadcrumb(
        check { it: Breadcrumb ->
          assertEquals("/some-uri", it.getData("url"))
          assertEquals("POST", it.getData("method"))
          assertEquals("http", it.type)
        },
        anyOrNull(),
      )
    verify(fixture.request).setAttribute(eq("ichnois-scope-lifecycle"), same(fixture.lifecycleToken))
  }

  @Test
  fun `pops scope when request gets destroyed`() {
    whenever(fixture.request.getAttribute(eq("ichnois-scope-lifecycle")))
      .thenReturn(fixture.lifecycleToken)

    fixture.listener.requestDestroyed(fixture.event)
    verify(fixture.lifecycleToken).close()
  }
}
