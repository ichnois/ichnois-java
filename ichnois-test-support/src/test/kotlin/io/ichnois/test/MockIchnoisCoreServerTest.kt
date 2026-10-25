package io.ichnois.test

import com.google.common.truth.Truth.assertThat
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreItemType
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MockIchnoisCoreServerTest {
  private val server = MockIchnoisCoreServer()

  @AfterTest
  fun teardown() {
    IchnoisCore.close()
    server.close()
  }

  @Test
  fun `receives an event sent through the HTTP transport`() {
    initForTest { it.dsn = server.dsn }

    val eventId = IchnoisCore.captureMessage("hello")
    IchnoisCore.flush(5000)

    val envelope = server.awaitEnvelope { it.header.eventId == eventId }
    assertThat(envelope.items.single().header.type).isEqualTo(IchnoisCoreItemType.Event)
  }

  @Test
  fun `awaitEnvelope fails once the timeout passes without a match`() {
    assertFailsWith<AssertionError> { server.awaitEnvelope(timeoutMillis = 100) { true } }
  }

  @Test
  fun `beforeEachCheck runs while waiting`() {
    var checks = 0
    MockIchnoisCoreServer { checks++ }
      .use { server ->
        assertFailsWith<AssertionError> { server.awaitEnvelope(timeoutMillis = 200) { true } }
        server.assertNoEnvelope(millis = 200) { true }
      }

    assertThat(checks).isGreaterThan(2)
  }

  @Test
  fun `assertNoEnvelope passes when nothing matches`() {
    initForTest { it.dsn = server.dsn }

    IchnoisCore.captureMessage("hello")
    IchnoisCore.flush(5000)

    server.assertNoEnvelope(millis = 100) { it.header.eventId == null }
  }

  @Test
  fun `assertNoEnvelope fails when a matching envelope arrives`() {
    initForTest { it.dsn = server.dsn }

    val eventId = IchnoisCore.captureMessage("hello")

    assertFailsWith<AssertionError> {
      server.assertNoEnvelope(millis = 5000) { it.header.eventId == eventId }
    }
  }
}
