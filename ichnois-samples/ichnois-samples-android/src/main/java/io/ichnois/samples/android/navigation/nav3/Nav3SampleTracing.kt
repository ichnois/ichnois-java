package io.ichnois.samples.android.navigation.nav3

import io.ichnois.IchnoisCore
import io.ichnois.SpanStatus
import io.ichnois.protocol.IchnoisCoreTransaction
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

internal fun tagCurrentNavigationSampleScenario(scenarioLabel: String) {
  IchnoisCore.getSpan()?.setTag(NAVIGATION_SAMPLE_SCENARIO_TAG, scenarioLabel)
  IchnoisCore.configureScope { scope ->
    scope.withTransaction { transaction ->
      transaction?.setTag(NAVIGATION_SAMPLE_SCENARIO_TAG, scenarioLabel)
    }
  }
}

internal fun IchnoisCoreTransaction.navigationSampleScenarioLabel(): String =
  getTag(NAVIGATION_SAMPLE_SCENARIO_TAG)
    ?: getTag(NAV2_SCENARIO_TAG)
    ?: getTag(NAV3_SCENARIO_TAG)
    ?: UNKNOWN_NAVIGATION_SCENARIO_LABEL

internal fun cancelCurrentActivityUiLoadTransaction() {
  IchnoisCore.configureScope { scope ->
    scope.withTransaction { transaction ->
      if (transaction?.operation == ACTIVITY_UI_LOAD_OP) {
        transaction.forceFinish(SpanStatus.CANCELLED, false, null)
        scope.clearTransaction()
      }
    }
  }
}

internal suspend fun recordSimulatedBackgroundSpan(routeName: String) {
  val parentSpan = IchnoisCore.getSpan()
  suspendCancellableCoroutine { continuation ->
    val worker = Thread {
      val span =
        parentSpan?.startChild(
          "test.navigation.background_work",
          "Nav3 /$routeName background work",
        )
      span?.setData("sample.background_work", true)
      try {
        Thread.sleep(BACKGROUND_WORK_MILLIS)
      } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
      } finally {
        span?.finish()
        if (continuation.isActive) {
          continuation.resume(Unit)
        }
      }
    }
    continuation.invokeOnCancellation { worker.interrupt() }
    worker.start()
  }
}

internal fun emitSampleNavigationSpan(routeName: String) {
  val span =
    IchnoisCore.getSpan()
      ?.startChild(
        "test.navigation.emit_span",
        "Nav3 /$routeName emit a span",
      )
  span?.setData("sample.emit_span", true)
  span?.finish()
}

internal const val ICHNOIS_FLUSH_TIMEOUT_MILLIS = 5000L

private const val BACKGROUND_WORK_MILLIS = 1000L
private const val NAV2_SCENARIO_TAG = "sample_nav2_scenario"
internal const val NAV3_SCENARIO_TAG = "sample_nav3_scenario"
internal const val NAVIGATION_SAMPLE_ACTIVITY_TAG = "sample_navigation_activity"
internal const val NAVIGATION_SAMPLE_SESSION_TAG = "sample_navigation_session"
internal const val NAVIGATION_SAMPLE_SCENARIO_TAG = "sample_navigation_scenario"
private const val UNKNOWN_NAVIGATION_SCENARIO_LABEL = "Unknown"
internal const val ACTIVITY_UI_LOAD_OP = "ui.load"
