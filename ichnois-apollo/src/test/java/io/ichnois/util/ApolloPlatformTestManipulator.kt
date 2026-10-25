package io.ichnois.util

object ApolloPlatformTestManipulator {
  fun pretendIsAndroid(isAndroid: Boolean) {
    Platform.isAndroid = isAndroid
  }
}
