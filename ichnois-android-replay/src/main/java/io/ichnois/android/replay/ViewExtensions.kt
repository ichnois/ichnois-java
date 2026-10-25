package io.ichnois.android.replay

import android.view.View

/** Marks this view to be masked in session replay. */
public fun View.ichnoisReplayMask() {
  setTag(R.id.ichnois_privacy, "mask")
}

/**
 * Marks this view to be unmasked in session replay. All its content will be visible in the replay,
 * use with caution.
 */
public fun View.ichnoisReplayUnmask() {
  setTag(R.id.ichnois_privacy, "unmask")
}
