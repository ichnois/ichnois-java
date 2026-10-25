package io.ichnois.servlet

import java.util.EventListener
import javax.servlet.ServletContext
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
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
          assertThat(it).isEqualTo(IchnoisCoreServletRequestListener::class.java)
        }
      )
  }
}
