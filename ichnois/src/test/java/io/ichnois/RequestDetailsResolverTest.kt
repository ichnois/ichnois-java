package io.ichnois

import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class RequestDetailsResolverTest {
  @Test
  fun `When options doesn't have a valid DSN, it throws InvalidDsnException`() {
    assertFailsWith<IllegalArgumentException> { RequestDetailsResolver(IchnoisCoreOptions()).resolve() }
  }

  @Test
  fun `When options doesn't have a well formed DSN-URL, it throws IllegalArgumentException`() {
    val options = IchnoisCoreOptions().apply { dsn = "ttps://key@ichnois.io/proj" }
    assertFailsWith<IllegalArgumentException> { RequestDetailsResolver(options).resolve() }
  }

  @Test
  fun `When options is set correctly, RequestDetails is created`() {
    val options = IchnoisCoreOptions().apply { dsn = "https://key@ichnois-h2jw.onrender.com/proj" }
    val requestDetails = RequestDetailsResolver(options).resolve()
    assertNotNull(requestDetails)
  }

  @Test
  fun `resolves envelope url`() {
    val options = IchnoisCoreOptions().apply { dsn = "https://key@ichnois-h2jw.onrender.com/proj" }
    val requestDetails = RequestDetailsResolver(options).resolve()
    assertEquals(URL("https://ichnois-h2jw.onrender.com/api/proj/envelope/"), requestDetails.url)
  }

  @Test
  fun `resolves common headers`() {
    val options =
      IchnoisCoreOptions().apply {
        dsn = "https://key@ichnois-h2jw.onrender.com/proj"
        ichnoisClientName = "custom-client"
      }
    val requestDetails = RequestDetailsResolver(options).resolve()
    assertEquals("custom-client", requestDetails.headers["User-Agent"])
    assertEquals(
      "Sentry sentry_version=7,sentry_client=custom-client,sentry_key=key",
      requestDetails.headers["X-Sentry-Auth"],
    )
  }
}
