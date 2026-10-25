package io.ichnois.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import io.ichnois.ISpan
import io.ichnois.NoOpSpan
import io.ichnois.IchnoisCore

/**
 * A [ProvidableCompositionLocal] for delivering [ISpan]s to composable subtrees. The delivered span
 * should be used to parent any spans the receiving subtree produces.
 *
 * Lets child composables remain agnostic about the [ISpan] hierarchies constructed by their
 * ancestors.
 */
@ExperimentalComposeUiApi
public val LocalIchnoisCoreSpan: ProvidableCompositionLocal<ISpan> = compositionLocalOf {
  UnsetIchnoisCoreSpan
}

/**
 * A wrapper for any composable subtree that should be passed the provided [span] via
 * [LocalIchnoisCoreSpan].
 *
 * The span is [normalized][normalize].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ProvideIchnoisCoreSpan(span: ISpan?, content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalIchnoisCoreSpan provides span.normalize()) {
    content()
  }
}

/**
 * Returns the receiver as-is unless it's an [UnsetIchnoisCoreSpan], in which case it returns the current
 * transaction.
 *
 * `*Bootstrap` methods are for internal use only. They're designed for scenarios where
 * [LocalIchnoisCoreSpan] hasn't been set, but we want to provide a reasonable alternative or fall back
 * to preexisting behavior.
 */
internal fun ISpan.orBootstrapCurrentTransaction(): ISpan =
  if (this.isUnset()) {
    IchnoisCore.getCurrentScopes().transaction.normalize()
  } else {
    this
  }

/**
 * A sentinel span indicating that [LocalIchnoisCoreSpan] hasn't been set. For internal use only.
 *
 * Note: This must be a distinct type from [NoOpSpan] so that [isUnset] can determine whether
 * `LocalIchnoisCoreSpan` was set with a `NoOpSpan` or was never set at all.
 *
 * Our own implementations often need to make that distinction so they can fall back to a reasonable
 * parent span if their environment doesn't provide one. We use a sentinel to avoid complicating our
 * public API for a distinction irrelevant to host apps.
 */
private object UnsetIchnoisCoreSpan : ISpan by NoOpSpan.getInstance()

private fun ISpan.isUnset(): Boolean = this === UnsetIchnoisCoreSpan

private fun ISpan?.normalize(): ISpan =
  when {
    this === UnsetIchnoisCoreSpan || this is NoOpSpan -> this
    this == null || this.isFinished -> NoOpSpan.getInstance()
    else -> this
  }
