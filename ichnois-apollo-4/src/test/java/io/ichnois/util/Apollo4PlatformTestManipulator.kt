package io.ichnois.util

object Apollo4PlatformTestManipulator {
  fun pretendIsAndroid(isAndroid: Boolean) {
    Platform.isAndroid = isAndroid
  }
}
