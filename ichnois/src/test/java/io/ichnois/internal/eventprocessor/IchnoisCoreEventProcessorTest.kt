package io.ichnois.internal.eventprocessor

import com.google.common.truth.Truth.assertThat
import io.ichnois.EventProcessor
import io.ichnois.IchnoisCoreOptions
import kotlin.test.Test

class IchnoisCoreEventProcessorTest {
  @Test
  fun `default SDK event processors are marked as IchnoisCore processors`() {
    assertThat(IchnoisCoreOptions().eventProcessors).isNotEmpty()
    assertThat(IchnoisCoreOptions().eventProcessors.all { it is IchnoisCoreEventProcessor }).isTrue()
  }

  @Test
  fun `customer event processors are not marked as IchnoisCore processors`() {
    val processor = object : EventProcessor {}

    assertThat(processor).isNotInstanceOf(IchnoisCoreEventProcessor::class.java)
  }
}
