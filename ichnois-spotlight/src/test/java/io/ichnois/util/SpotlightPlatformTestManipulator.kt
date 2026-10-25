package io.ichnois.util

object SpotlightPlatformTestManipulator {
  fun pretendIsAndroid(isAndroid: Boolean) {
    Platform.isAndroid = isAndroid
  }
}
