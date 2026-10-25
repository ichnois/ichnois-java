package io.ichnois.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver

public object IchnoisCoreModifier {
  public const val TAG: String = "IchnoisCoreTag"

  // Based on TestTag
  // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/semantics/SemanticsProperties.kt;l=166;drc=76bc6975d1b520c545b6f8786ff5c9f0bc22bd1f
  private val IchnoisCoreTag =
    SemanticsPropertyKey<String>(
      name = TAG,
      mergePolicy = { parentValue, _ ->
        // Never merge IchnoisCoreTags, to avoid leaking internal test tags to parents.
        parentValue
      },
    )

  @JvmStatic
  public fun Modifier.ichnoisTag(tag: String): Modifier = this then IchnoisCoreTagModifierNodeElement(tag)

  private data class IchnoisCoreTagModifierNodeElement(val tag: String) :
    ModifierNodeElement<IchnoisCoreTagModifierNode>(), SemanticsModifier {
    override val semanticsConfiguration: SemanticsConfiguration =
      SemanticsConfiguration().also { it[IchnoisCoreTag] = tag }

    override fun create(): IchnoisCoreTagModifierNode = IchnoisCoreTagModifierNode(tag)

    override fun update(node: IchnoisCoreTagModifierNode) {
      node.tag = tag
    }

    override fun InspectorInfo.inspectableProperties() {
      name = "ichnoisTag"
      properties["tag"] = tag
    }
  }

  private class IchnoisCoreTagModifierNode(var tag: String) : Modifier.Node(), SemanticsModifierNode {
    override val shouldClearDescendantSemantics: Boolean
      get() = false

    override val shouldMergeDescendantSemantics: Boolean
      get() = false

    override fun SemanticsPropertyReceiver.applySemantics() {
      this[IchnoisCoreTag] = tag
    }

    // SemanticsModifierNode.isImportantForBounds() was added as an abstract method in
    // compose-ui 1.11. Classes compiled against earlier versions lack this method in
    // their bytecode, which causes AbstractMethodError when the accessibility tree is
    // traversed on 1.11+ runtimes.
    // Returning true to match the default behavior
    // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/ui/ui/src/commonMain/kotlin/androidx/compose/ui/node/SemanticsModifierNode.kt;l=69-83;drc=bd7809b4bc9205721c2f1bc681694dd348885849
    @Suppress("unused") fun isImportantForBounds(): Boolean = true
  }
}
