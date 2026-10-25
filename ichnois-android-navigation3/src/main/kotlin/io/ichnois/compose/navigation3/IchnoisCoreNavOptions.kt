package io.ichnois.compose.navigation3

import androidx.compose.runtime.Immutable
import org.jetbrains.annotations.ApiStatus

// Keep the default low, as every captured entry may require argument extraction and recursive
// sanitization when navigation changes are observed.
private const val DEFAULT_MAX_CAPTURED_BACK_STACK_ENTRIES = 5

/**
 * Configuration info for a [IchnoisCoreNavEffect].
 *
 * Instances are immutable; create one with the IchnoisCoreNavOptions DSL:
 * ```kotlin
 * val options = IchnoisCoreNavOptions {
 *   enableNavigationBreadcrumbs = false
 *   maxCapturedBackStackEntries = 5
 * }
 * ```
 */
@ApiStatus.Experimental
@Immutable
public class IchnoisCoreNavOptions
private constructor(
  public val enableNavigationBreadcrumbs: Boolean,
  public val enableNavigationTransactions: Boolean,
  public val captureBackStack: Boolean,
  public val maxCapturedBackStackEntries: Int,
) {

  init {
    require(maxCapturedBackStackEntries >= 0) {
      "maxCapturedBackStackEntries must be non-negative, was $maxCapturedBackStackEntries"
    }
  }

  /**
   * Mutable builder for [IchnoisCoreNavOptions]. Prefer the [IchnoisCoreNavOptions] DSL to using this
   * directly.
   *
   * Lets us keep the resulting instance [Immutable] while preserving binary compatibility, should
   * new properties be added in the future.
   */
  public class Builder {

    /**
     * Whether navigation should produce IchnoisCore breadcrumbs. If `true`, a new nav destination
     * generates a breadcrumb like `from=/Home` and `to=/Profile`.
     */
    public var enableNavigationBreadcrumbs: Boolean = true

    /**
     * Whether navigation should start a IchnoisCore transaction. If `true`, navigating from `/Home` to
     * `/Profile` starts a `/Profile` transaction and finishes the current `/Home` transaction.
     */
    public var enableNavigationTransactions: Boolean = true

    /**
     * Whether IchnoisCore should record back stack information for inclusion with crashes, errors, and
     * other captured events. If `true`, a stack like `/Home -> /Profile` is recorded alongside the
     * event, ordered with the current/top entry first.
     */
    public var captureBackStack: Boolean = true

    /**
     * Maximum number of entries IchnoisCore should record per captured back stack (starting with the
     * most recent). Set to `0` to capture no back stack entries.
     *
     * Note: IchnoisCore resolves and sanitizes up to [maxCapturedBackStackEntries] names + argument maps
     * whenever your back stack changes. Keep the back stack mapper lightweight, and reduce the max
     * captured count if mapper work is expensive.
     */
    public var maxCapturedBackStackEntries: Int = DEFAULT_MAX_CAPTURED_BACK_STACK_ENTRIES

    public fun build(): IchnoisCoreNavOptions =
      IchnoisCoreNavOptions(
        enableNavigationBreadcrumbs = enableNavigationBreadcrumbs,
        enableNavigationTransactions = enableNavigationTransactions,
        captureBackStack = captureBackStack,
        maxCapturedBackStackEntries = maxCapturedBackStackEntries,
      )
  }

  override fun equals(other: Any?): Boolean =
    this === other ||
      (other is IchnoisCoreNavOptions &&
        enableNavigationBreadcrumbs == other.enableNavigationBreadcrumbs &&
        enableNavigationTransactions == other.enableNavigationTransactions &&
        captureBackStack == other.captureBackStack &&
        maxCapturedBackStackEntries == other.maxCapturedBackStackEntries)

  override fun hashCode(): Int {
    var result = enableNavigationBreadcrumbs.hashCode()
    result = 31 * result + enableNavigationTransactions.hashCode()
    result = 31 * result + captureBackStack.hashCode()
    result = 31 * result + maxCapturedBackStackEntries
    return result
  }

  override fun toString(): String =
    "IchnoisCoreNavOptions(" +
      "enableNavigationBreadcrumbs=$enableNavigationBreadcrumbs, " +
      "enableNavigationTransactions=$enableNavigationTransactions, " +
      "captureBackStack=$captureBackStack, " +
      "maxCapturedBackStackEntries=$maxCapturedBackStackEntries)"
}

/**
 * Creates [IchnoisCoreNavOptions]. Optionally configure it via [configure]. E.g.:
 * ```kotlin
 * val options = IchnoisCoreNavOptions {
 *   enableNavigationBreadcrumbs = false
 *   maxCapturedBackStackEntries = 5
 * }
 * ```
 */
@ApiStatus.Experimental
public fun IchnoisCoreNavOptions(configure: IchnoisCoreNavOptions.Builder.() -> Unit = {}): IchnoisCoreNavOptions =
  IchnoisCoreNavOptions.Builder().apply(configure).build()
