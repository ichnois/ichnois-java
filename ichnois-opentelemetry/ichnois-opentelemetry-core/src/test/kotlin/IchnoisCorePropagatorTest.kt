package io.ichnois.opentelemetry

import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.ichnois.IchnoisCore
import io.ichnois.opentelemetry.IchnoisCoreOtelKeys.ICHNOIS_BAGGAGE_KEY
import io.ichnois.opentelemetry.IchnoisCoreOtelKeys.ICHNOIS_TRACE_KEY
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

class IchnoisCorePropagatorTest {

  @BeforeTest
  fun setup() {
    IchnoisCore.init("https://key@ichnois-h2jw.onrender.com/proj")
  }

  @AfterTest
  fun teardown() {
    IchnoisCore.close()
    Context.root().makeCurrent()
  }

  @Suppress("DEPRECATION")
  @Test
  fun `ignores incoming headers when strict continuation rejects org id`() {
    IchnoisCore.init { options ->
      options.dsn = "https://key@o2.ingest.ichnois.io/123"
      options.isStrictTraceContinuation = true
    }

    val propagator = IchnoisCorePropagator()
    val carrier: Map<String, String> =
      mapOf(
        "ichnois-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1",
        "baggage" to "ichnois-trace_id=f9118105af4a2d42b4124532cd1065ff,ichnois-org_id=1",
      )

    val newContext = propagator.extract(Context.root(), carrier, MapGetter())

    assertFalse(Span.fromContext(newContext).spanContext.isValid)
    assertNull(newContext.get(ICHNOIS_TRACE_KEY))
    assertNull(newContext.get(ICHNOIS_BAGGAGE_KEY))
  }
}
