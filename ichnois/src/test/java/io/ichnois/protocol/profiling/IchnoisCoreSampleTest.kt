package io.ichnois.protocol.profiling

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IchnoisCoreSampleTest {

  @Test
  fun `copying sample keeps values and copies unknown`() {
    val unknown = mutableMapOf<String, Any>("key" to "value")
    val original =
      IchnoisCoreSample().apply {
        timestamp = 1.23
        stackId = 4
        threadId = "main"
        setUnknown(unknown)
      }

    val copy = IchnoisCoreSample(original)

    assertThat(copy.timestamp).isEqualTo(original.timestamp)
    assertThat(copy.stackId).isEqualTo(original.stackId)
    assertThat(copy.threadId).isEqualTo(original.threadId)
    assertThat(copy.unknown).containsExactlyEntriesIn(original.unknown)
    assertThat(copy.unknown).isNotSameInstanceAs(original.unknown)

    unknown["key"] = "changed"
    assertThat(copy.unknown).containsEntry("key", "value")
  }
}
