package io.ichnois.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import io.ichnois.Breadcrumb
import io.ichnois.ITransaction
import io.ichnois.IchnoisCoreIntegrationPackageStorage
import io.ichnois.IchnoisCoreOptions
import io.ichnois.android.navigation.IchnoisCoreNavigationListener
import io.ichnois.util.IntegrationUtils.addIntegrationToSdkVersion

private const val TRACE_ORIGIN_APPENDIX = "jetpack_compose"

internal class IchnoisCoreLifecycleObserver(
  private val navController: NavController,
  private val navListener: NavController.OnDestinationChangedListener =
    IchnoisCoreNavigationListener(traceOriginAppendix = TRACE_ORIGIN_APPENDIX),
) : LifecycleEventObserver {
  private companion object {
    init {
      IchnoisCoreIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.github.ichnois:ichnois-compose", BuildConfig.VERSION_NAME)
    }
  }

  init {
    addIntegrationToSdkVersion("ComposeNavigation")
  }

  override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
    if (event == Lifecycle.Event.ON_RESUME) {
      navController.addOnDestinationChangedListener(navListener)
    } else if (event == Lifecycle.Event.ON_PAUSE) {
      navController.removeOnDestinationChangedListener(navListener)
    }
  }

  fun dispose() {
    navController.removeOnDestinationChangedListener(navListener)
  }
}

/**
 * A [DisposableEffect] that captures a [Breadcrumb] and starts an [ITransaction] and sends them to
 * IchnoisCore for every navigation event when being attached to the respective [NavHostController].
 *
 * @param navListener An instance of a [IchnoisCoreNavigationListener] that is shared with other ichnois
 *   integrations, like the fragment navigation integration.
 */
@Composable
@NonRestartableComposable
public fun NavHostController.withIchnoisCoreObservableEffect(
  navListener: IchnoisCoreNavigationListener
): NavHostController {
  val navListenerSnapshot by rememberUpdatedState(navListener)

  // As described in
  // https://developer.android.com/codelabs/jetpack-compose-advanced-state-side-effects#6
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  DisposableEffect(lifecycle, this) {
    val observer =
      IchnoisCoreLifecycleObserver(this@withIchnoisCoreObservableEffect, navListener = navListenerSnapshot)

    lifecycle.addObserver(observer)

    onDispose {
      observer.dispose()
      lifecycle.removeObserver(observer)
    }
  }
  return this
}

/**
 * A [DisposableEffect] that captures a [Breadcrumb] and starts an [ITransaction] and sends them to
 * IchnoisCore for every navigation event when being attached to the respective [NavHostController]. This
 * version of withIchnoisCoreObservableEffect should be used if you are working purely with Compose.
 *
 * @param enableNavigationBreadcrumbs Whether the integration should capture breadcrumbs for
 *   navigation events.
 * @param enableNavigationTracing Whether the integration should start a new [ITransaction] with
 *   [IchnoisCoreOptions.idleTimeout] for navigation events.
 */
@Composable
@NonRestartableComposable
public fun NavHostController.withIchnoisCoreObservableEffect(
  enableNavigationBreadcrumbs: Boolean = true,
  enableNavigationTracing: Boolean = true,
): NavHostController {
  val enableBreadcrumbsSnapshot by rememberUpdatedState(enableNavigationBreadcrumbs)
  val enableTracingSnapshot by rememberUpdatedState(enableNavigationTracing)

  return withIchnoisCoreObservableEffect(
    navListener =
      IchnoisCoreNavigationListener(
        enableNavigationBreadcrumbs = enableBreadcrumbsSnapshot,
        enableNavigationTracing = enableTracingSnapshot,
        traceOriginAppendix = TRACE_ORIGIN_APPENDIX,
      )
  )
}

/**
 * A [DisposableEffect] that captures a [Breadcrumb] and starts an [ITransaction] and sends them to
 * IchnoisCore for every navigation event when being attached to the respective [NavHostController].
 *
 * Used by the ichnois android gradle plugin for Jetpack Compose instrumentation.
 */
@Composable
internal fun NavHostController.withIchnoisCoreObservableEffect(): NavHostController =
  withIchnoisCoreObservableEffect(enableNavigationBreadcrumbs = true, enableNavigationTracing = true)
