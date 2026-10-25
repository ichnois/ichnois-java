package io.ichnois.spring

import io.ichnois.IchnoisCoreOptions
import java.security.Principal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

class HttpServletRequestIchnoisCoreUserProviderTest {
  @Test
  fun `attaches user's IP address to IchnoisCore Event`() {
    val request = MockHttpServletRequest()
    request.addHeader("X-FORWARDED-FOR", "192.168.0.1,192.168.0.2")
    RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))

    val options = IchnoisCoreOptions()
    options.isSendDefaultPii = true
    val userProvider = HttpServletRequestIchnoisCoreUserProvider(options)
    val result = userProvider.provideUser()

    assertNotNull(result)
    assertEquals("192.168.0.1", result.ipAddress)
  }

  @Test
  fun `attaches username to IchnoisCore Event`() {
    val principal = mock<Principal>()
    whenever(principal.name).thenReturn("janesmith")
    val request = MockHttpServletRequest()
    request.userPrincipal = principal
    RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))

    val options = IchnoisCoreOptions()
    options.isSendDefaultPii = true
    val userProvider = HttpServletRequestIchnoisCoreUserProvider(options)
    val result = userProvider.provideUser()

    assertNotNull(result)
    assertEquals("janesmith", result.username)
  }

  @Test
  fun `when user info is disabled, does not attach user data`() {
    val principal = mock<Principal>()
    whenever(principal.name).thenReturn("janesmith")
    val request = MockHttpServletRequest()
    request.userPrincipal = principal
    RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))

    val options =
      IchnoisCoreOptions().apply {
        isSendDefaultPii = true
        dataCollection.setUserInfo(false)
      }
    val result = HttpServletRequestIchnoisCoreUserProvider(options).provideUser()

    assertNull(result)
  }

  @Test
  fun `when user info is enabled, attaches user data`() {
    val principal = mock<Principal>()
    whenever(principal.name).thenReturn("janesmith")
    val request = MockHttpServletRequest()
    request.userPrincipal = principal
    RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))

    val options =
      IchnoisCoreOptions().apply {
        isSendDefaultPii = false
        dataCollection.setUserInfo(true)
      }
    val result = HttpServletRequestIchnoisCoreUserProvider(options).provideUser()

    assertNotNull(result)
    assertEquals("janesmith", result.username)
  }

  @Test
  fun `when sendDefaultPii is set to false, does not attach user data IchnoisCore Event`() {
    val principal = mock<Principal>()
    whenever(principal.name).thenReturn("janesmith")
    val request = MockHttpServletRequest()
    request.userPrincipal = principal
    RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))

    val options = IchnoisCoreOptions()
    options.isSendDefaultPii = false
    val userProvider = HttpServletRequestIchnoisCoreUserProvider(options)
    val result = userProvider.provideUser()

    assertNull(result)
  }
}
