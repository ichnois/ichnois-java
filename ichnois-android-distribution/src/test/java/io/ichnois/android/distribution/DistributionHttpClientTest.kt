package io.ichnois.android.distribution

import io.ichnois.IchnoisCoreOptions
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

class DistributionHttpClientTest {

  private lateinit var options: IchnoisCoreOptions
  private lateinit var httpClient: DistributionHttpClient

  @Before
  fun setUp() {
    options =
      IchnoisCoreOptions().apply {
        connectionTimeoutMillis = 10000
        readTimeoutMillis = 10000
      }

    options.distribution.apply {
      orgSlug = "ichnois"
      projectSlug = "launchpad-test"
      orgAuthToken = "DONT_CHECK_THIS_IN"
      ichnoisBaseUrl = "https://ichnois-h2jw.onrender.com"
    }

    httpClient = DistributionHttpClient(options)
  }

  @Test
  @Ignore("This is just used for testing against the real API.")
  fun `test checkForUpdates with real API`() {
    val params =
      DistributionHttpClient.UpdateCheckParams(
        appId = "com.emergetools.hackernews",
        versionName = "1.0.0",
        versionCode = 5L,
        buildConfiguration = "release",
      )

    val response = httpClient.checkForUpdates(params)

    // Print response for debugging
    println("HTTP Status: ${response.statusCode}")
    println("Response Body: ${response.body}")
    println("Is Successful: ${response.isSuccessful}")

    // Basic assertions
    assertTrue("Response should have a status code", response.statusCode > 0)
    assertNotNull("Response body should not be null", response.body)
  }
}
