package io.ichnois.android.core

import io.ichnois.IchnoisCoreMaskingOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IchnoisCoreScreenshotOptionsTest {

  @Test
  fun `maskViewClasses is empty by default`() {
    val options = IchnoisCoreScreenshotOptions()
    assertTrue(options.maskViewClasses.isEmpty())
  }

  @Test
  fun `unmaskViewClasses is empty by default`() {
    val options = IchnoisCoreScreenshotOptions()
    assertTrue(options.unmaskViewClasses.isEmpty())
  }

  @Test
  fun `setMaskAllText true only adds TextView`() {
    val options = IchnoisCoreScreenshotOptions()
    options.setMaskAllText(true)

    assertTrue(options.maskViewClasses.contains(IchnoisCoreMaskingOptions.TEXT_VIEW_CLASS_NAME))
    // Should NOT add sensitive view classes (only setMaskAllImages does that)
    assertFalse(options.maskViewClasses.contains(IchnoisCoreMaskingOptions.WEB_VIEW_CLASS_NAME))
    assertFalse(options.maskViewClasses.contains(IchnoisCoreMaskingOptions.VIDEO_VIEW_CLASS_NAME))
    assertEquals(1, options.maskViewClasses.size)
  }

  @Test
  fun `setMaskAllImages true adds ImageView and sensitive view classes`() {
    val options = IchnoisCoreScreenshotOptions()
    options.setMaskAllImages(true)

    assertTrue(options.maskViewClasses.contains(IchnoisCoreMaskingOptions.IMAGE_VIEW_CLASS_NAME))
    assertTrue(options.maskViewClasses.contains(IchnoisCoreMaskingOptions.WEB_VIEW_CLASS_NAME))
    assertTrue(options.maskViewClasses.contains(IchnoisCoreMaskingOptions.VIDEO_VIEW_CLASS_NAME))
    assertTrue(
      options.maskViewClasses.contains(IchnoisCoreMaskingOptions.CAMERAX_PREVIEW_VIEW_CLASS_NAME)
    )
    assertTrue(
      options.maskViewClasses.contains(IchnoisCoreMaskingOptions.ANDROIDX_MEDIA_VIEW_CLASS_NAME)
    )
    assertTrue(options.maskViewClasses.contains(IchnoisCoreMaskingOptions.EXOPLAYER_CLASS_NAME))
    assertTrue(options.maskViewClasses.contains(IchnoisCoreMaskingOptions.EXOPLAYER_STYLED_CLASS_NAME))
  }

  @Test
  fun `setMaskAllImages false does not add sensitive view classes`() {
    val options = IchnoisCoreScreenshotOptions()
    options.setMaskAllImages(false)

    assertFalse(options.maskViewClasses.contains(IchnoisCoreMaskingOptions.WEB_VIEW_CLASS_NAME))
    assertFalse(options.maskViewClasses.contains(IchnoisCoreMaskingOptions.VIDEO_VIEW_CLASS_NAME))
    assertTrue(options.unmaskViewClasses.contains(IchnoisCoreMaskingOptions.IMAGE_VIEW_CLASS_NAME))
  }

  @Test
  fun `calling setMaskAllImages true multiple times does not duplicate classes`() {
    val options = IchnoisCoreScreenshotOptions()
    options.setMaskAllImages(true)
    options.setMaskAllImages(true)
    options.setMaskAllImages(true)

    // CopyOnWriteArraySet should prevent duplicates
    assertEquals(7, options.maskViewClasses.size)
  }

  @Test
  fun `inherits addMaskViewClass from base class`() {
    val options = IchnoisCoreScreenshotOptions()
    options.addMaskViewClass("com.example.CustomView")

    assertTrue(options.maskViewClasses.contains("com.example.CustomView"))
  }

  @Test
  fun `inherits addUnmaskViewClass from base class`() {
    val options = IchnoisCoreScreenshotOptions()
    options.addUnmaskViewClass("com.example.SafeView")

    assertTrue(options.unmaskViewClasses.contains("com.example.SafeView"))
  }

  @Test
  fun `inherits container class methods from base class`() {
    val options = IchnoisCoreScreenshotOptions()
    options.setMaskViewContainerClass("com.example.MaskContainer")
    options.setUnmaskViewContainerClass("com.example.UnmaskContainer")

    assertEquals("com.example.MaskContainer", options.maskViewContainerClass)
    assertEquals("com.example.UnmaskContainer", options.unmaskViewContainerClass)
  }

  @Test
  fun `setMaskAllImages false removes sensitive view classes added by true`() {
    val options = IchnoisCoreScreenshotOptions()
    options.setMaskAllImages(true)

    // Verify classes were added
    assertTrue(options.maskViewClasses.contains(IchnoisCoreMaskingOptions.WEB_VIEW_CLASS_NAME))
    assertTrue(options.maskViewClasses.contains(IchnoisCoreMaskingOptions.VIDEO_VIEW_CLASS_NAME))

    options.setMaskAllImages(false)

    assertTrue(options.maskViewClasses.isEmpty())
  }
}
