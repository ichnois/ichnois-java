package io.ichnois

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IchnoisCoreReplayOptionsTest {

  @BeforeTest
  fun setup() {
    IchnoisCoreIntegrationPackageStorage.getInstance().clearStorage()
  }

  @Test
  fun `uses medium quality as default`() {
    val replayOptions = IchnoisCoreReplayOptions(true, null)

    assertEquals(IchnoisCoreReplayOptions.IchnoisCoreReplayQuality.MEDIUM, replayOptions.quality)
    assertEquals(75_000, replayOptions.quality.bitRate)
    assertEquals(1.0f, replayOptions.quality.sizeScale)
  }

  @Test
  fun `low quality`() {
    val replayOptions =
      IchnoisCoreReplayOptions(true, null).apply {
        quality = IchnoisCoreReplayOptions.IchnoisCoreReplayQuality.LOW
      }

    assertEquals(50_000, replayOptions.quality.bitRate)
    assertEquals(0.8f, replayOptions.quality.sizeScale)
  }

  @Test
  fun `high quality`() {
    val replayOptions =
      IchnoisCoreReplayOptions(true, null).apply {
        quality = IchnoisCoreReplayOptions.IchnoisCoreReplayQuality.HIGH
      }

    assertEquals(100_000, replayOptions.quality.bitRate)
    assertEquals(1.0f, replayOptions.quality.sizeScale)
  }

  @Test
  fun testDefaultScreenshotStrategy() {
    val options = IchnoisCoreReplayOptions(false, null)
    assertEquals(ScreenshotStrategyType.PIXEL_COPY, options.getScreenshotStrategy())
  }

  @Test
  fun testSetScreenshotStrategyToCanvas() {
    val options = IchnoisCoreReplayOptions(false, null)
    options.screenshotStrategy = ScreenshotStrategyType.CANVAS
    assertEquals(ScreenshotStrategyType.CANVAS, options.getScreenshotStrategy())
  }

  @Test
  fun testSetScreenshotStrategyToPixelCopy() {
    val options = IchnoisCoreReplayOptions(false, null)
    options.screenshotStrategy = ScreenshotStrategyType.PIXEL_COPY
    assertEquals(ScreenshotStrategyType.PIXEL_COPY, options.getScreenshotStrategy())
  }

  // Network Details Options
  // https://docs.sentry.io/platforms/javascript/session-replay/configuration/#network-details

  @Test
  fun `getNetworkRequestHeaders returns default headers by default`() {
    val options = IchnoisCoreReplayOptions(false, null)
    assertEquals(
      IchnoisCoreReplayOptions.getNetworkDetailsDefaultHeaders().size,
      options.networkRequestHeaders.size,
    )

    val headers = options.networkRequestHeaders
    IchnoisCoreReplayOptions.getNetworkDetailsDefaultHeaders().forEach { defaultHeader ->
      assertEquals(true, headers.contains(defaultHeader))
    }
  }

  @Test
  fun `getNetworkResponseHeaders returns default headers by default`() {
    val options = IchnoisCoreReplayOptions(false, null)
    assertEquals(
      IchnoisCoreReplayOptions.getNetworkDetailsDefaultHeaders().size,
      options.networkResponseHeaders.size,
    )

    val headers = options.networkResponseHeaders
    IchnoisCoreReplayOptions.getNetworkDetailsDefaultHeaders().forEach { defaultHeader ->
      assertEquals(true, headers.contains(defaultHeader))
    }
  }

  @Test
  fun `setNetworkRequestHeaders adds to default headers`() {
    val options = IchnoisCoreReplayOptions(false, null)
    val additionalHeaders = listOf("X-Custom-Header", "X-Another-Header")

    options.setNetworkRequestHeaders(additionalHeaders)

    assertEquals(
      IchnoisCoreReplayOptions.getNetworkDetailsDefaultHeaders().size + additionalHeaders.size,
      options.networkRequestHeaders.size,
    )

    val headers = options.networkRequestHeaders
    IchnoisCoreReplayOptions.getNetworkDetailsDefaultHeaders().forEach { defaultHeader ->
      assertTrue(headers.contains(defaultHeader))
    }
    assertTrue(headers.contains("X-Custom-Header"))
    assertTrue(headers.contains("X-Another-Header"))
  }

  @Test
  fun `setNetworkResponseHeaders adds to default headers`() {
    val options = IchnoisCoreReplayOptions(false, null)
    val additionalHeaders = listOf("X-Response-Header", "X-Debug-Header")

    options.setNetworkResponseHeaders(additionalHeaders)

    assertEquals(
      IchnoisCoreReplayOptions.getNetworkDetailsDefaultHeaders().size + additionalHeaders.size,
      options.networkResponseHeaders.size,
    )

    val headers = options.networkResponseHeaders
    IchnoisCoreReplayOptions.getNetworkDetailsDefaultHeaders().forEach { defaultHeader ->
      assertTrue(headers.contains(defaultHeader))
    }
    assertTrue(headers.contains("X-Response-Header"))
    assertTrue(headers.contains("X-Debug-Header"))
  }

  // Custom Masking Integration Tests

  private fun hasCustomMaskingIntegration(): Boolean {
    return IchnoisCoreIntegrationPackageStorage.getInstance()
      .integrations
      .contains("ReplayCustomMasking")
  }

  @Test
  fun `default options does not add ReplayCustomMasking integration`() {
    IchnoisCoreReplayOptions(false, null)
    assertFalse(hasCustomMaskingIntegration())
  }

  @Test
  fun `empty options does not add ReplayCustomMasking integration`() {
    IchnoisCoreReplayOptions(true, null)
    assertFalse(hasCustomMaskingIntegration())
  }

  @Test
  fun `addUnmaskViewClass adds ReplayCustomMasking integration`() {
    val options = IchnoisCoreReplayOptions(false, null)
    options.addUnmaskViewClass("com.example.MyTextView")
    assertTrue(hasCustomMaskingIntegration())
  }

  @Test
  fun `setMaskViewContainerClass does not add ReplayCustomMasking integration`() {
    val options = IchnoisCoreReplayOptions(false, null)
    options.setMaskViewContainerClass("com.example.MyContainer")
    assertFalse(hasCustomMaskingIntegration())
  }

  @Test
  fun `setUnmaskViewContainerClass does not add ReplayCustomMasking integration`() {
    val options = IchnoisCoreReplayOptions(false, null)
    options.setUnmaskViewContainerClass("com.example.MyContainer")
    assertFalse(hasCustomMaskingIntegration())
  }

  @Test
  fun `setMaskAllText true does not set custom integration`() {
    val options = IchnoisCoreReplayOptions(false, null)
    options.setMaskAllText(true)
    options.setMaskAllImages(true)
    assertFalse(hasCustomMaskingIntegration())
  }

  @Test
  fun `trackCustomMasking only adds integration once`() {
    val options = IchnoisCoreReplayOptions(false, null)
    options.setMaskAllText(false)
    options.setMaskAllImages(false)
    assertTrue(hasCustomMaskingIntegration())
    assertEquals(
      1,
      IchnoisCoreIntegrationPackageStorage.getInstance().integrations.count {
        it == "ReplayCustomMasking"
      },
    )
  }

  @Test
  fun `addMaskViewClass adds ReplayCustomMasking integration`() {
    val options = IchnoisCoreReplayOptions(false, null)
    options.addMaskViewClass("com.example.MySensitiveView")
    assertTrue(hasCustomMaskingIntegration())
  }

  @Test
  fun `setMaskAllText adds ReplayCustomMasking integration`() {
    val options = IchnoisCoreReplayOptions(false, null)
    options.setMaskAllText(false)
    assertTrue(hasCustomMaskingIntegration())
  }

  @Test
  fun `setMaskAllImages adds ReplayCustomMasking integration`() {
    val options = IchnoisCoreReplayOptions(false, null)
    options.setMaskAllImages(false)
    assertTrue(hasCustomMaskingIntegration())
  }
}
