package io.ichnois.android.core

import android.content.Context
import androidx.lifecycle.Lifecycle.Event.ON_START
import androidx.lifecycle.Lifecycle.Event.ON_STOP
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ichnois.CheckIn
import io.ichnois.Hint
import io.ichnois.IScope
import io.ichnois.IIchnoisCoreClient
import io.ichnois.ProfileChunk
import io.ichnois.ProfilingTraceData
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreEnvelope
import io.ichnois.IchnoisCoreEvent
import io.ichnois.IchnoisCoreLogEvent
import io.ichnois.IchnoisCoreLogEvents
import io.ichnois.IchnoisCoreMetricsEvent
import io.ichnois.IchnoisCoreMetricsEvents
import io.ichnois.IchnoisCoreReplayEvent
import io.ichnois.Session
import io.ichnois.TraceContext
import io.ichnois.UserFeedback
import io.ichnois.protocol.Feedback
import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.protocol.IchnoisCoreTransaction
import io.ichnois.transport.RateLimiter
import java.util.LinkedList
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [31])
class SessionTrackingIntegrationTest {
  private lateinit var context: Context

  @BeforeTest
  fun `set up`() {
    AppState.getInstance().resetInstance()
    context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `session tracking works properly with multiple backgrounds and foregrounds`() {
    lateinit var options: IchnoisCoreAndroidOptions
    initForTest(context) {
      it.dsn = "https://key@ichnois-h2jw.onrender.com/proj"
      it.release = "io.ichnois.samples@2.3.0"
      it.environment = "production"
      it.sessionTrackingIntervalMillis = 0L
      options = it
    }
    val client = CapturingIchnoisCoreClient()
    IchnoisCore.bindClient(client)
    val lifecycle = setupLifecycle()
    val initSid = lastSessionId()

    lifecycle.handleLifecycleEvent(ON_START)
    val sidAfterFirstStart = lastSessionId()
    Thread.sleep(100L)
    lifecycle.handleLifecycleEvent(ON_STOP)
    Thread.sleep(100L)
    val sidAfterFirstStop = lastSessionId()

    Thread.sleep(100L)

    lifecycle.handleLifecycleEvent(ON_START)
    val sidAfterSecondStart = lastSessionId()
    Thread.sleep(100L)
    lifecycle.handleLifecycleEvent(ON_STOP)
    Thread.sleep(100L)
    val sidAfterSecondStop = lastSessionId()
    // we bind our CapturingIchnoisCoreClient only after .init is called, so we'll be able to capture
    // only the Exited status of the session started in .init
    val initSessionUpdate = client.sessionUpdates.pop()
    assertTrue {
      initSid == initSessionUpdate.sessionId.toString() &&
        Session.State.Exited == initSessionUpdate.status
    }

    val afterFirstStartSessionUpdate = client.sessionUpdates.pop()
    assertTrue {
      sidAfterFirstStart == afterFirstStartSessionUpdate.sessionId.toString() &&
        Session.State.Ok == afterFirstStartSessionUpdate.status
    }

    val afterFirstStopSessionUpdate = client.sessionUpdates.pop()
    assertTrue {
      "null" == sidAfterFirstStop &&
        sidAfterFirstStart == afterFirstStopSessionUpdate.sessionId.toString() &&
        Session.State.Exited == afterFirstStopSessionUpdate.status
    }

    val afterSecondStartSessionUpdate = client.sessionUpdates.pop()
    assertTrue {
      sidAfterSecondStart == afterSecondStartSessionUpdate.sessionId.toString() &&
        Session.State.Ok == afterSecondStartSessionUpdate.status
    }

    val afterSecondStopSessionUpdate = client.sessionUpdates.pop()
    assertTrue {
      "null" == sidAfterSecondStop &&
        sidAfterSecondStart == afterSecondStopSessionUpdate.sessionId.toString() &&
        Session.State.Exited == afterSecondStopSessionUpdate.status
    }
  }

  private fun lastSessionId(): String? {
    var sid: String? = null
    IchnoisCore.configureScope { scope -> sid = scope.session?.sessionId.toString() }
    return sid
  }

  private fun setupLifecycle(): LifecycleRegistry {
    val lifecycle = LifecycleRegistry(ProcessLifecycleOwner.get())
    lifecycle.addObserver(AppState.getInstance().lifecycleObserver)
    return lifecycle
  }

  private class CapturingIchnoisCoreClient : IIchnoisCoreClient {
    val sessionUpdates = LinkedList<Session>()

    override fun isEnabled(): Boolean = true

    override fun captureEvent(event: IchnoisCoreEvent, scope: IScope?, hint: Hint?): IchnoisCoreId {
      TODO("Not yet implemented")
    }

    override fun close(isRestarting: Boolean) {
      TODO("Not yet implemented")
    }

    override fun close() {
      TODO("Not yet implemented")
    }

    override fun flush(timeoutMillis: Long) {
      TODO("Not yet implemented")
    }

    override fun captureFeedback(feedback: Feedback, hint: Hint?, scope: IScope): IchnoisCoreId {
      TODO("Not yet implemented")
    }

    override fun captureReplayEvent(
      event: IchnoisCoreReplayEvent,
      scope: IScope?,
      hint: Hint?,
    ): IchnoisCoreId {
      TODO("Not yet implemented")
    }

    override fun captureUserFeedback(userFeedback: UserFeedback) {
      TODO("Not yet implemented")
    }

    override fun captureSession(session: Session, hint: Hint?) {
      sessionUpdates.add(session)
    }

    override fun captureEnvelope(envelope: IchnoisCoreEnvelope, hint: Hint?): IchnoisCoreId? {
      TODO("Not yet implemented")
    }

    override fun captureTransaction(
      transaction: IchnoisCoreTransaction,
      traceContext: TraceContext?,
      scope: IScope?,
      hint: Hint?,
      profilingTraceData: ProfilingTraceData?,
    ): IchnoisCoreId {
      TODO("Not yet implemented")
    }

    override fun captureProfileChunk(profileChunk: ProfileChunk, scope: IScope?): IchnoisCoreId {
      TODO("Not yet implemented")
    }

    override fun captureCheckIn(checkIn: CheckIn, scope: IScope?, hint: Hint?): IchnoisCoreId {
      TODO("Not yet implemented")
    }

    override fun captureLog(event: IchnoisCoreLogEvent, scope: IScope?) {
      TODO("Not yet implemented")
    }

    override fun captureBatchedLogEvents(logEvents: IchnoisCoreLogEvents) {
      TODO("Not yet implemented")
    }

    override fun captureMetric(event: IchnoisCoreMetricsEvent, scope: IScope?, hint: Hint?) {
      TODO("Not yet implemented")
    }

    override fun captureBatchedMetricsEvents(metricsEvents: IchnoisCoreMetricsEvents) {
      TODO("Not yet implemented")
    }

    override fun getRateLimiter(): RateLimiter? {
      TODO("Not yet implemented")
    }
  }
}
