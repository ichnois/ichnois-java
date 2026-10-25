package io.ichnois.android.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ichnois.IIchnoisCoreClient
import io.ichnois.IIchnoisCoreExecutorService
import io.ichnois.IchnoisCoreLogEvent
import io.ichnois.IchnoisCoreLogLevel
import io.ichnois.IchnoisCoreOptions
import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.test.ImmediateExecutorService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class AndroidLoggerBatchProcessorTest {

  private class Fixture {
    val options = IchnoisCoreAndroidOptions()
    val client: IIchnoisCoreClient = mock()

    fun getSut(
      useImmediateExecutor: Boolean = false,
      config: ((IchnoisCoreOptions) -> Unit)? = null,
    ): AndroidLoggerBatchProcessor {
      if (useImmediateExecutor) {
        options.executorService = ImmediateExecutorService()
      }
      config?.invoke(options)
      return AndroidLoggerBatchProcessor(options, client)
    }
  }

  private val fixture = Fixture()

  @BeforeTest
  fun `set up`() {
    AppState.getInstance().resetInstance()
  }

  @AfterTest
  fun `tear down`() {
    AppState.getInstance().resetInstance()
  }

  @Test
  fun `constructor registers as AppState listener`() {
    fixture.getSut()
    assertNotNull(AppState.getInstance().lifecycleObserver)
  }

  @Test
  fun `onBackground schedules flush`() {
    val sut = fixture.getSut(useImmediateExecutor = true)
    val logEvent = IchnoisCoreLogEvent(IchnoisCoreId(), 1.0, "test", IchnoisCoreLogLevel.INFO)
    sut.add(logEvent)

    sut.onBackground()

    verify(fixture.client).captureBatchedLogEvents(any())
  }

  @Test
  fun `onBackground does not submit shared executor work without accepted items`() {
    val sharedExecutor = mock<IIchnoisCoreExecutorService>()
    val sut = fixture.getSut { options -> options.executorService = sharedExecutor }

    sut.onBackground()

    verifyNoInteractions(sharedExecutor)
  }

  @Test
  fun `onBackground handles executor exception gracefully`() {
    val sut = fixture.getSut { options ->
      val rejectingExecutor = mock<IIchnoisCoreExecutorService>()
      whenever(rejectingExecutor.submit(any())).thenThrow(RuntimeException("Rejected"))
      options.executorService = rejectingExecutor
    }
    sut.add(IchnoisCoreLogEvent(IchnoisCoreId(), 1.0, "test", IchnoisCoreLogLevel.INFO))

    // Should not throw
    sut.onBackground()
  }

  @Test
  fun `close removes AppState listener`() {
    val sut = fixture.getSut()
    sut.close(false)

    assertTrue(AppState.getInstance().lifecycleObserver.listeners.isEmpty())
  }

  @Test
  fun `close with isRestarting true still removes listener`() {
    val sut = fixture.getSut()
    sut.close(true)

    assertTrue(AppState.getInstance().lifecycleObserver.listeners.isEmpty())
  }
}
