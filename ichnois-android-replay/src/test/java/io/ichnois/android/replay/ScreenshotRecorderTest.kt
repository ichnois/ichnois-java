package io.ichnois.android.replay

import android.os.Handler
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ichnois.ScreenshotStrategyType
import io.ichnois.IchnoisCoreOptions
import io.ichnois.android.replay.ReplaySmokeTest.Fixture
import io.ichnois.android.replay.screenshot.CanvasStrategy
import io.ichnois.android.replay.screenshot.PixelCopyStrategy
import io.ichnois.android.replay.screenshot.ScreenshotStrategy
import io.ichnois.android.replay.util.MainLooperHandler
import java.util.concurrent.ScheduledExecutorService
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ScreenshotRecorderTest {

  internal class Fixture() {

    fun getSut(config: (options: IchnoisCoreOptions) -> Unit = {}): ScreenshotRecorder {
      val options = IchnoisCoreOptions()
      config(options)
      return ScreenshotRecorder(
        ScreenshotRecorderConfig(100, 100, 1f, 1f, 1, 1000),
        options,
        object : ExecutorProvider {
          override fun getExecutor(): ScheduledExecutorService = mock<ScheduledExecutorService>()

          override fun getMainLooperHandler(): MainLooperHandler = mock<MainLooperHandler>()

          override fun getBackgroundHandler(): Handler = mock<Handler>()
        },
        null,
      )
    }
  }

  private val fixture = Fixture()

  @Test
  fun `when config uses PIXEL_COPY strategy, ScreenshotRecorder creates PixelCopyStrategy`() {
    val recorder = fixture.getSut { options ->
      options.sessionReplay.screenshotStrategy = ScreenshotStrategyType.PIXEL_COPY
    }

    val strategy = getStrategy(recorder)

    assertTrue(
      strategy is PixelCopyStrategy,
      "Expected PixelCopyStrategy but got ${strategy::class.simpleName}",
    )
  }

  @Test
  fun `when config uses CANVAS strategy, ScreenshotRecorder creates CanvasStrategy`() {
    val recorder = fixture.getSut { options ->
      options.sessionReplay.screenshotStrategy = ScreenshotStrategyType.CANVAS
    }
    val strategy = getStrategy(recorder)

    assertTrue(
      strategy is CanvasStrategy,
      "Expected CanvasStrategy but got ${strategy::class.simpleName}",
    )
  }

  @Test
  fun `when config uses default strategy, ScreenshotRecorder creates PixelCopyStrategy`() {
    val recorder = fixture.getSut()
    val strategy = getStrategy(recorder)

    assertTrue(
      strategy is PixelCopyStrategy,
      "Expected PixelCopyStrategy as default but got ${strategy::class.simpleName}",
    )
  }

  private fun getStrategy(recorder: ScreenshotRecorder): ScreenshotStrategy {
    val strategyField = ScreenshotRecorder::class.java.getDeclaredField("screenshotStrategy")
    strategyField.isAccessible = true
    return strategyField.get(recorder) as ScreenshotStrategy
  }
}
