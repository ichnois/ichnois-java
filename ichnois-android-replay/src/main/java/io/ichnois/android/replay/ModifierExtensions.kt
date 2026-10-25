package io.ichnois.android.replay

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import io.ichnois.android.replay.IchnoisCoreReplayModifiers.IchnoisCorePrivacy

public object IchnoisCoreReplayModifiers {
  public val IchnoisCorePrivacy: SemanticsPropertyKey<String> =
    SemanticsPropertyKey<String>(
      name = "IchnoisCorePrivacy",
      mergePolicy = { parentValue, _ -> parentValue },
    )
}

public fun Modifier.ichnoisReplayMask(): Modifier =
  semantics(properties = { this[IchnoisCorePrivacy] = "mask" })

public fun Modifier.ichnoisReplayUnmask(): Modifier =
  semantics(properties = { this[IchnoisCorePrivacy] = "unmask" })
