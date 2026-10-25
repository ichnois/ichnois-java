package io.ichnois.android.core

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ichnois.Breadcrumb
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreLevel
import io.ichnois.IchnoisCoreLogEvent
import io.ichnois.IchnoisCoreLogLevel
import io.ichnois.IchnoisCoreOptions
import io.ichnois.android.core.performance.AppStartMetrics
import java.lang.RuntimeException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IchnoisCoreLogcatAdapterTest {
  private val tag = "my-tag"
  private val commonMsg = "IchnoisCoreLogcatAdapter"
  private val throwable = RuntimeException("Test Exception")

  class Fixture {
    val breadcrumbs = mutableListOf<Breadcrumb>()
    val logs = mutableListOf<IchnoisCoreLogEvent>()

    fun initSut(options: IchnoisCore.OptionsConfiguration<IchnoisCoreAndroidOptions>? = null) {
      val metadata =
        Bundle().apply { putString(ManifestMetadataReader.DSN, "https://key@ichnois.io/123") }
      val mockContext = ContextUtilsTestHelper.mockMetaData(metaData = metadata)
      initForTest(mockContext) {
        it.beforeBreadcrumb = IchnoisCoreOptions.BeforeBreadcrumbCallback { breadcrumb, _ ->
          breadcrumbs.add(breadcrumb)
          breadcrumb
        }
        it.logs.isEnabled = true
        it.logs.beforeSend =
          IchnoisCoreOptions.Logs.BeforeSendLogCallback { logEvent ->
            logs.add(logEvent)
            logEvent
          }
        options?.configure(it)
      }
    }
  }

  private val fixture = Fixture()

  @AfterTest
  fun `clean up`() {
    AppStartMetrics.getInstance().clear()
    ContextUtils.resetInstance()
    IchnoisCore.close()
    fixture.breadcrumbs.clear()
    fixture.logs.clear()
  }

  @Test
  fun `verbose log message has expected content`() {
    fixture.initSut()
    IchnoisCoreLogcatAdapter.v(tag, "$commonMsg verbose")
    fixture.breadcrumbs.first().assert(tag, "$commonMsg verbose", IchnoisCoreLevel.DEBUG)
    fixture.logs.first().assert("$commonMsg verbose", IchnoisCoreLogLevel.TRACE)
    assertEquals("auto.log.logcat", fixture.logs.first().attributes?.get("ichnois.origin")?.value)
  }

  @Test
  fun `info log message has expected content`() {
    fixture.initSut()
    IchnoisCoreLogcatAdapter.i(tag, "$commonMsg info")
    fixture.breadcrumbs.first().assert(tag, "$commonMsg info", IchnoisCoreLevel.INFO)
    fixture.logs.first().assert("$commonMsg info", IchnoisCoreLogLevel.INFO)
  }

  @Test
  fun `debug log message has expected content`() {
    fixture.initSut()
    IchnoisCoreLogcatAdapter.d(tag, "$commonMsg debug")
    fixture.breadcrumbs.first().assert(tag, "$commonMsg debug", IchnoisCoreLevel.DEBUG)
    fixture.logs.first().assert("$commonMsg debug", IchnoisCoreLogLevel.DEBUG)
  }

  @Test
  fun `warning log message has expected content`() {
    fixture.initSut()
    IchnoisCoreLogcatAdapter.w(tag, "$commonMsg warning")
    fixture.breadcrumbs.first().assert(tag, "$commonMsg warning", IchnoisCoreLevel.WARNING)
    fixture.logs.first().assert("$commonMsg warning", IchnoisCoreLogLevel.WARN)
  }

  @Test
  fun `error log message has expected content`() {
    fixture.initSut()
    IchnoisCoreLogcatAdapter.e(tag, "$commonMsg error")
    fixture.breadcrumbs.first().assert(tag, "$commonMsg error", IchnoisCoreLevel.ERROR)
    fixture.logs.first().assert("$commonMsg error", IchnoisCoreLogLevel.ERROR)
  }

  @Test
  fun `wtf log message has expected content`() {
    fixture.initSut()
    IchnoisCoreLogcatAdapter.wtf(tag, "$commonMsg wtf")
    fixture.breadcrumbs.first().assert(tag, "$commonMsg wtf", IchnoisCoreLevel.ERROR)
    fixture.logs.first().assert("$commonMsg wtf", IchnoisCoreLogLevel.FATAL)
  }

  @Test
  fun `e log throwable has expected content`() {
    fixture.initSut()
    IchnoisCoreLogcatAdapter.e(tag, "$commonMsg error exception", throwable)
    fixture.breadcrumbs.first().assert(tag, "$commonMsg error exception", IchnoisCoreLevel.ERROR)
    fixture.logs
      .first()
      .assert("$commonMsg error exception\n${throwable.message}", IchnoisCoreLogLevel.ERROR)
  }

  @Test
  fun `v log throwable has expected content`() {
    fixture.initSut()
    IchnoisCoreLogcatAdapter.v(tag, "$commonMsg verbose exception", throwable)
    fixture.breadcrumbs.first().assert(tag, "$commonMsg verbose exception", IchnoisCoreLevel.DEBUG)
    fixture.logs
      .first()
      .assert("$commonMsg verbose exception\n${throwable.message}", IchnoisCoreLogLevel.TRACE)
  }

  @Test
  fun `i log throwable has expected content`() {
    fixture.initSut()
    IchnoisCoreLogcatAdapter.i(tag, "$commonMsg info exception", throwable)
    fixture.breadcrumbs.first().assert(tag, "$commonMsg info exception", IchnoisCoreLevel.INFO)
    fixture.logs
      .first()
      .assert("$commonMsg info exception\n${throwable.message}", IchnoisCoreLogLevel.INFO)
  }

  @Test
  fun `d log throwable has expected content`() {
    fixture.initSut()
    IchnoisCoreLogcatAdapter.d(tag, "$commonMsg debug exception", throwable)
    fixture.breadcrumbs.first().assert(tag, "$commonMsg debug exception", IchnoisCoreLevel.DEBUG)
    fixture.logs
      .first()
      .assert("$commonMsg debug exception\n${throwable.message}", IchnoisCoreLogLevel.DEBUG)
  }

  @Test
  fun `w log throwable has expected content`() {
    fixture.initSut()
    IchnoisCoreLogcatAdapter.w(tag, "$commonMsg warning exception", throwable)
    fixture.breadcrumbs.first().assert(tag, "$commonMsg warning exception", IchnoisCoreLevel.WARNING)
    fixture.logs
      .first()
      .assert("$commonMsg warning exception\n${throwable.message}", IchnoisCoreLogLevel.WARN)
  }

  @Test
  fun `wtf log throwable has expected content`() {
    fixture.initSut()
    IchnoisCoreLogcatAdapter.wtf(tag, "$commonMsg wtf exception", throwable)
    fixture.breadcrumbs.first().assert(tag, "$commonMsg wtf exception", IchnoisCoreLevel.ERROR)
    fixture.logs
      .first()
      .assert("$commonMsg wtf exception\n${throwable.message}", IchnoisCoreLogLevel.FATAL)
  }

  @Test
  fun `do not send logs if logs is disabled`() {
    fixture.initSut { it.logs.isEnabled = false }

    IchnoisCoreLogcatAdapter.v(tag, "$commonMsg verbose")
    IchnoisCoreLogcatAdapter.i(tag, "$commonMsg info")
    IchnoisCoreLogcatAdapter.d(tag, "$commonMsg debug")
    IchnoisCoreLogcatAdapter.w(tag, "$commonMsg warning")
    IchnoisCoreLogcatAdapter.e(tag, "$commonMsg error")
    IchnoisCoreLogcatAdapter.wtf(tag, "$commonMsg wtf")
    IchnoisCoreLogcatAdapter.e(tag, "$commonMsg error exception", throwable)
    IchnoisCoreLogcatAdapter.v(tag, "$commonMsg verbose exception", throwable)
    IchnoisCoreLogcatAdapter.i(tag, "$commonMsg info exception", throwable)
    IchnoisCoreLogcatAdapter.d(tag, "$commonMsg debug exception", throwable)
    IchnoisCoreLogcatAdapter.w(tag, "$commonMsg warning exception", throwable)
    IchnoisCoreLogcatAdapter.wtf(tag, "$commonMsg wtf exception", throwable)

    assertTrue(fixture.logs.isEmpty())
  }

  @Test
  fun `logs add correct number of breadcrumb`() {
    fixture.initSut()
    IchnoisCoreLogcatAdapter.v(tag, commonMsg)
    IchnoisCoreLogcatAdapter.d(tag, commonMsg)
    IchnoisCoreLogcatAdapter.i(tag, commonMsg)
    IchnoisCoreLogcatAdapter.w(tag, commonMsg)
    IchnoisCoreLogcatAdapter.e(tag, commonMsg)
    IchnoisCoreLogcatAdapter.wtf(tag, commonMsg)
    assertEquals(
      6,
      fixture.breadcrumbs.filter { it.message?.contains("IchnoisCoreLogcatAdapter") ?: false }.size,
    )
  }

  private fun Breadcrumb.assert(
    expectedTag: String,
    expectedMessage: String,
    expectedLevel: IchnoisCoreLevel,
  ) {
    assertEquals(expectedMessage, message)
    assertEquals(expectedTag, data["tag"])
    assertEquals(expectedLevel, level)
    assertEquals("Logcat", category)
  }

  private fun IchnoisCoreLogEvent.assert(expectedMessage: String, expectedLevel: IchnoisCoreLogLevel) {
    assertEquals(expectedMessage, body)
    assertEquals(expectedLevel, level)
  }
}
