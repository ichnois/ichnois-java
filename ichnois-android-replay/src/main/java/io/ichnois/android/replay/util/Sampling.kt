package io.ichnois.android.replay.util

import io.ichnois.util.Random

internal fun Random.sample(rate: Double?): Boolean {
  if (rate != null) {
    return !(rate < this.nextDouble()) // bad luck
  }
  return false
}
