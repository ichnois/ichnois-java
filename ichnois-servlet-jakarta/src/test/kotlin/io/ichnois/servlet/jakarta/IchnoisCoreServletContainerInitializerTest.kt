package io.ichnois.servlet.jakarta

import jakarta.servlet.ServletContext
import java.util.EventListener
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class IchnoisCoreServletContainerInitializerTest {
  private val initializer = IchnoisCoreServletContainerInitializer()

  @Test
  fun `adds IchnoisCoreServletRequestListener on startup`() {
    val servletContext = mock<ServletContext>()
    initializer.onStartup(null, servletContext)
    verify(servletContext)
      .addListener(
        check { it: Class<out EventListener> ->
          assertEquals(IchnoisCoreServletRequestListener::class.java, it)
        }
      )
  }
}
