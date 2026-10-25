package io.ichnois.uitest.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.IdlingPolicies
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.idling.CountingIdlingResource
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnitRunner
import io.ichnois.JsonSerializer
import io.ichnois.ProfilingTraceData
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCore.OptionsConfiguration
import io.ichnois.IchnoisCoreEnvelope
import io.ichnois.IchnoisCoreEvent
import io.ichnois.IchnoisCoreItemType
import io.ichnois.IchnoisCoreOptions
import io.ichnois.android.core.IchnoisCoreAndroid
import io.ichnois.android.core.IchnoisCoreAndroidOptions
import io.ichnois.protocol.IchnoisCoreTransaction
import io.ichnois.test.applyTestOptions
import io.ichnois.test.initForTest
import io.ichnois.uitest.android.mockservers.MockRelay
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class BaseUiTest {
  /** Runner of the test. */
  protected lateinit var runner: AndroidJUnitRunner

  /** Application context for the current test. */
  protected lateinit var context: Context

  /** Mock dsn used to send envelopes to our mock [relay] server. */
  protected lateinit var mockDsn: String
    // The mockDsn cannot be changed. If a custom dsn needs to be used, it can be set in the options
    // as usual
    private set

  /**
   * Idling resource that will be checked by the relay server (if [initIchnoisCore] param
   * relayWaitForRequests is true). This should be increased to match any envelope that will be sent
   * during the test, so that they can later be checked.
   */
  protected val relayIdlingResource = CountingIdlingResource("relay-requests")

  /** Mock relay server that receives all envelopes sent during the test. */
  protected val relay = MockRelay(false, relayIdlingResource)

  private fun runCommand(cmd: String) {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val pfd = automation.executeShellCommand(cmd)
    try {
      FileInputStream(pfd.fileDescriptor).readBytes()
    } catch (e: Throwable) {
      // ignored
    }
    pfd.close()
  }

  private fun disableDontKeepActivities() {
    runCommand("settings put global always_finish_activities 0")
  }

  fun disableSystemAnimations() {
    runCommand("settings put global window_animation_scale 0")
    runCommand("settings put global transition_animation_scale 0")
    runCommand("settings put global animator_duration_scale 0")
  }

  @BeforeTest
  fun baseSetUp() {
    runner = InstrumentationRegistry.getInstrumentation() as AndroidJUnitRunner
    IdlingPolicies.setIdlingResourceTimeout(10, TimeUnit.SECONDS)
    context = ApplicationProvider.getApplicationContext()
    context.cacheDir.deleteRecursively()
    relay.start()
    mockDsn = relay.createMockDsn()
    disableDontKeepActivities()
    disableSystemAnimations()
  }

  @AfterTest
  fun baseFinish() {
    IdlingRegistry.getInstance().unregister(relayIdlingResource)
    relay.shutdown()
    IchnoisCore.close()
  }

  /**
   * Initializes the IchnoisCore sdk through [IchnoisCoreAndroid.init] with a default dsn used to catch
   * envelopes with [relay]. [relayWaitForRequests] sets whether [relay] should wait for all the
   * envelopes sent when doing assertions. If true, [relayIdlingResource] should be increased to
   * match any envelope that will be sent during the test. IchnoisCore options can be adjusted as usual
   * through [optionsConfiguration].
   */
  protected fun initIchnoisCore(
    relayWaitForRequests: Boolean = false,
    context: Context = this.context,
    optionsConfiguration: ((options: IchnoisCoreAndroidOptions) -> Unit)? = null,
  ) {
    relay.waitForRequests = relayWaitForRequests
    if (relayWaitForRequests) {
      IdlingRegistry.getInstance().register(relayIdlingResource)
    }
    initForTest(context) {
      it.dsn = mockDsn
      it.isDebug = true
      // We don't use test orchestrator, due to problems with Saucelabs.
      // So the app data is not deleted between tests. Thus, We don't know when sessions will
      // actually be sent.
      // To avoid any interference between tests we can just disable them by default.
      it.isEnableAutoSessionTracking = false
      optionsConfiguration?.invoke(it)
    }
  }
}

/** Waits until the IchnoisCore SDK is idle. */
fun waitUntilIdle() {
  // We rely on Espresso's idling resources.
  Espresso.onIdle()
}

fun classExists(className: String): Boolean {
  try {
    Class.forName(className)
    return true
  } catch (exception: ClassNotFoundException) {
    // no-op
  }
  return false
}

fun initForTest(
  context: Context,
  optionsConfiguration: OptionsConfiguration<IchnoisCoreAndroidOptions>,
) {
  IchnoisCoreAndroid.init(context) {
    applyTestOptions(it)
    optionsConfiguration.configure(it)
  }
}

/**
 * Function used to describe the content of the envelope to print in the logs. For debugging
 * purposes only.
 */
internal fun IchnoisCoreEnvelope.describeForTest(): String {
  var descr = ""
  items.forEach { item ->
    when (item.header.type) {
      IchnoisCoreItemType.Event -> {
        val deserialized =
          JsonSerializer(IchnoisCoreOptions())
            .deserialize(item.data.inputStream().reader(), IchnoisCoreEvent::class.java)!!
        descr += "Event (${deserialized.eventId}) - message: ${deserialized.message?.formatted} -- "
      }
      IchnoisCoreItemType.Transaction -> {
        val deserialized =
          JsonSerializer(IchnoisCoreOptions())
            .deserialize(item.data.inputStream().reader(), IchnoisCoreTransaction::class.java)!!
        descr +=
          "Transaction (${deserialized.eventId}) - transaction: ${deserialized.transaction} - spans: ${deserialized.spans.joinToString { "${it.op} ${it.description}" }} -- "
      }
      IchnoisCoreItemType.Profile -> {
        val deserialized =
          JsonSerializer(IchnoisCoreOptions())
            .deserialize(item.data.inputStream().reader(), ProfilingTraceData::class.java)!!
        descr +=
          "Profile (${deserialized.profileId}) - transactionName: ${deserialized.transactionName} -- "
      }
      else -> {
        descr += "${item.header.type} -- "
      }
    }
  }
  return "*** Envelope: $descr ***"
}
