package io.ichnois.android.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ichnois.IchnoisCoreClient
import kotlin.test.Test
import kotlin.test.assertIs
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class AndroidLoggerBatchProcessorFactoryTest {

  @Test
  fun `create returns AndroidLoggerBatchProcessor instance`() {
    val factory = AndroidLoggerBatchProcessorFactory()
    val options = IchnoisCoreAndroidOptions()
    val client: IchnoisCoreClient = mock()

    val processor = factory.create(options, client)

    assertIs<AndroidLoggerBatchProcessor>(processor)
  }
}
