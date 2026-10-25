package io.ichnois

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlin.test.assertFailsWith

class IchnoisOptionsTest {
  @Test
  fun `structured config builds dsn for configured endpoint`() {
    val options = IchnoisOptions().apply {
      endpoint = "https://ichnois-h2jw.onrender.com"
      projectId = "checkout-service"
      apiKey = "publicKey123"
    }

    assertThat(options.resolveDsn())
      .isEqualTo("https://publicKey123@ichnois-h2jw.onrender.com/checkout-service")
  }

  @Test
  fun `structured config supports endpoint already ending in api path`() {
    val options = IchnoisOptions().apply {
      endpoint = "https://ichnois-h2jw.onrender.com/api/"
      projectId = "checkout-service"
      apiKey = "publicKey123"
    }

    assertThat(options.resolveDsn())
      .isEqualTo("https://publicKey123@ichnois-h2jw.onrender.com/api/checkout-service")
  }

  @Test
  fun `project id rejects path separators`() {
    assertFailsWith<IllegalArgumentException> {
      IchnoisOptions().apply { projectId = "team/checkout-service" }
    }
  }

  @Test
  fun `structured config rejects unexpected endpoint base paths`() {
    val options = IchnoisOptions().apply {
      endpoint = "https://ichnois-h2jw.onrender.com/custom"
      projectId = "checkout-service"
      apiKey = "publicKey123"
    }
    assertFailsWith<IllegalArgumentException> { options.resolveDsn() }
  }

  @Test
  fun `missing structured config fails with helpful message`() {
    val exception = assertFailsWith<IllegalArgumentException> {
      IchnoisOptions().resolveDsn()
    }
    assertThat(exception).hasMessageThat().contains("endpoint, projectId, and apiKey")
  }

  @Test
  fun `explicit release must contain a git sha`() {
    val options = IchnoisOptions().apply { release = "1.2.3" }
    assertFailsWith<IllegalArgumentException> { options.resolveRelease() }
  }

  @Test
  fun `valid explicit release is preserved`() {
    val options = IchnoisOptions().apply { release = "checkout-service@4f8b2a1" }
    assertThat(options.resolveRelease()).isEqualTo("checkout-service@4f8b2a1")
  }
}
