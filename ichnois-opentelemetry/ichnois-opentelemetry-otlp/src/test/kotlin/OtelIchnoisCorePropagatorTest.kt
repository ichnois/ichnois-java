package io.ichnois.opentelemetry.otlp

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapSetter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.ichnois.Baggage
import io.ichnois.IchnoisCore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenTelemetryOtlpPropagatorTest {

  @BeforeTest
  fun setup() {
    IchnoisCore.init("https://key@ichnois-h2jw.onrender.com/proj")
  }

  @AfterTest
  fun teardown() {
    IchnoisCore.close()
    Context.root().makeCurrent()
  }

  @Test
  fun `propagator registers for ichnois-trace and baggage`() {
    val propagator = OpenTelemetryOtlpPropagator()
    assertEquals(listOf("ichnois-trace", "baggage"), propagator.fields())
  }

  @Test
  fun `invalid ichnois trace header returns context without modification`() {
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier: Map<String, String> =
      mapOf(
        "ichnois-trace" to "wrong",
        "baggage" to
          "ichnois-environment=production,ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=df71f5972f754b4c85af13ff5c07017d",
      )
    val scopeInContext = IchnoisCore.forkedRootScopes("test")

    val newContext = propagator.extract(Context.root(), carrier, MapGetter())

    val baggage = newContext.get(OpenTelemetryOtlpPropagator.ICHNOIS_BAGGAGE_KEY)
    assertNull(baggage)
  }

  @Test
  fun `ignores incoming headers when strict continuation rejects org id`() {
    IchnoisCore.init { options ->
      options.dsn = "https://key@o2.ingest.ichnois.io/123"
      options.isStrictTraceContinuation = true
    }
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier: Map<String, String> =
      mapOf(
        "ichnois-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1",
        "baggage" to "ichnois-trace_id=f9118105af4a2d42b4124532cd1065ff,ichnois-org_id=1",
      )

    val newContext = propagator.extract(Context.root(), carrier, MapGetter())

    assertFalse(Span.fromContext(newContext).spanContext.isValid)
    assertNull(newContext.get(OpenTelemetryOtlpPropagator.ICHNOIS_BAGGAGE_KEY))
  }

  @Test
  fun `uses incoming headers`() {
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier: Map<String, String> =
      mapOf(
        "ichnois-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1",
        "baggage" to
          "ichnois-environment=production,ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=df71f5972f754b4c85af13ff5c07017d",
      )

    val newContext = propagator.extract(Context.root(), carrier, MapGetter())

    val span = Span.fromContext(newContext)
    assertTrue(span.spanContext.isValid)
    assertEquals("f9118105af4a2d42b4124532cd1065ff", span.spanContext.traceId)
    assertEquals("424cffc8f94feeee", span.spanContext.spanId)

    val baggage = newContext.get(OpenTelemetryOtlpPropagator.ICHNOIS_BAGGAGE_KEY)
    assertEquals("production", baggage?.environment)
  }

  @Test
  fun `extract sets sampled trace flag when ichnois-trace has sampled=0`() {
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier: Map<String, String> =
      mapOf(
        "ichnois-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-0",
        "baggage" to
          "ichnois-environment=production,ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=false,ichnois-trace_id=df71f5972f754b4c85af13ff5c07017d",
      )

    val newContext = propagator.extract(Context.root(), carrier, MapGetter())

    val span = Span.fromContext(newContext)
    assertTrue(span.spanContext.isValid)
    assertFalse(span.spanContext.traceFlags.isSampled)
  }

  @Test
  fun `extract sets sampled trace flag when ichnois-trace has no sampling decision`() {
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier: Map<String, String> =
      mapOf(
        "ichnois-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee",
        "baggage" to
          "ichnois-environment=production,ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-trace_id=df71f5972f754b4c85af13ff5c07017d",
      )

    val newContext = propagator.extract(Context.root(), carrier, MapGetter())

    val span = Span.fromContext(newContext)
    assertTrue(span.spanContext.isValid)
    assertTrue(span.spanContext.traceFlags.isSampled)
  }

  @Test
  fun `extract does not store baggage in context when baggage header is missing`() {
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier: Map<String, String> =
      mapOf("ichnois-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1")

    val newContext = propagator.extract(Context.root(), carrier, MapGetter())

    val span = Span.fromContext(newContext)
    assertTrue(span.spanContext.isValid)

    val baggage = newContext.get(OpenTelemetryOtlpPropagator.ICHNOIS_BAGGAGE_KEY)
    assertNull(baggage)
  }

  @Test
  fun `injects headers`() {
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier = mutableMapOf<String, String>()

    val otelSpanContext =
      SpanContext.create(
        "f9118105af4a2d42b4124532cd1065ff",
        "424cffc8f94feeee",
        TraceFlags.getSampled(),
        TraceState.getDefault(),
      )
    val otelSpan = Span.wrap(otelSpanContext)
    val baggage =
      Baggage.fromHeader(
        "ichnois-environment=production,ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=df71f5972f754b4c85af13ff5c07017d"
      )
    val context =
      Context.root().with(otelSpan).with(OpenTelemetryOtlpPropagator.ICHNOIS_BAGGAGE_KEY, baggage)

    propagator.inject(context, carrier, MapSetter())

    assertEquals("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1", carrier["ichnois-trace"])
    assertEquals(
      "ichnois-environment=production,ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=df71f5972f754b4c85af13ff5c07017d",
      carrier["baggage"],
    )
  }

  @Test
  fun `injects headers if URL in span attributes matches tracePropagationTargets`() {
    IchnoisCore.init { options ->
      options.dsn = "https://key@ichnois-h2jw.onrender.com/proj"
      options.setTracePropagationTargets(listOf("ichnois.io"))
    }
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier = mutableMapOf<String, String>()
    val tracerProvider = SdkTracerProvider.builder().build()
    val otelSpan =
      tracerProvider
        .get("test")
        .spanBuilder("test")
        .setAttribute("url.full", "https://example.com/api/0/")
        .startSpan()
    val baggage =
      Baggage.fromHeader(
        "ichnois-environment=production,ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=df71f5972f754b4c85af13ff5c07017d"
      )

    try {
      val context =
        Context.root().with(otelSpan).with(OpenTelemetryOtlpPropagator.ICHNOIS_BAGGAGE_KEY, baggage)

      propagator.inject(context, carrier, MapSetter())
    } finally {
      otelSpan.end()
      tracerProvider.shutdown()
    }

    assertEquals(
      "${otelSpan.spanContext.traceId}-${otelSpan.spanContext.spanId}-1",
      carrier["ichnois-trace"],
    )
    assertEquals(
      "ichnois-environment=production,ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=df71f5972f754b4c85af13ff5c07017d",
      carrier["baggage"],
    )
  }

  @Test
  fun `does not inject headers if URL in span attributes does not match tracePropagationTargets`() {
    IchnoisCore.init { options ->
      options.dsn = "https://key@ichnois-h2jw.onrender.com/proj"
      options.setTracePropagationTargets(listOf("github.com"))
    }
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier = mutableMapOf<String, String>()
    val tracerProvider = SdkTracerProvider.builder().build()
    val otelSpan =
      tracerProvider
        .get("test")
        .spanBuilder("test")
        .setAttribute("url.full", "https://example.com/api/0/")
        .startSpan()

    try {
      propagator.inject(Context.root().with(otelSpan), carrier, MapSetter())
    } finally {
      otelSpan.end()
      tracerProvider.shutdown()
    }

    assertNull(carrier["ichnois-trace"])
    assertNull(carrier["baggage"])
  }

  @Test
  fun `injects headers if tracePropagationTargets is restricted and URL is unavailable`() {
    IchnoisCore.init { options ->
      options.dsn = "https://key@ichnois-h2jw.onrender.com/proj"
      options.setTracePropagationTargets(listOf("ichnois.io"))
    }
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier = mutableMapOf<String, String>()

    val otelSpanContext =
      SpanContext.create(
        "f9118105af4a2d42b4124532cd1065ff",
        "424cffc8f94feeee",
        TraceFlags.getSampled(),
        TraceState.getDefault(),
      )
    val otelSpan = Span.wrap(otelSpanContext)

    propagator.inject(Context.root().with(otelSpan), carrier, MapSetter())

    assertEquals("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1", carrier["ichnois-trace"])
    assertNull(carrier["baggage"])
  }

  @Test
  fun `does not inject headers when no span in context`() {
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier = mutableMapOf<String, String>()

    propagator.inject(Context.root(), carrier, MapSetter())

    assertNull(carrier["ichnois-trace"])
    assertNull(carrier["baggage"])
  }

  @Test
  fun `does not inject headers when span is invalid`() {
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier = mutableMapOf<String, String>()

    propagator.inject(Context.root().with(Span.getInvalid()), carrier, MapSetter())

    assertNull(carrier["ichnois-trace"])
    assertNull(carrier["baggage"])
  }

  @Test
  fun `does not inject baggage header when baggage is missing from context`() {
    val propagator = OpenTelemetryOtlpPropagator()
    val carrier = mutableMapOf<String, String>()

    val otelSpanContext =
      SpanContext.create(
        "f9118105af4a2d42b4124532cd1065ff",
        "424cffc8f94feeee",
        TraceFlags.getSampled(),
        TraceState.getDefault(),
      )
    val otelSpan = Span.wrap(otelSpanContext)

    propagator.inject(Context.root().with(otelSpan), carrier, MapSetter())

    assertEquals("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1", carrier["ichnois-trace"])
    assertNull(carrier["baggage"])
  }
}

class MapGetter : TextMapGetter<Map<String, String>> {
  override fun keys(carrier: Map<String, String>): MutableIterable<String> {
    return carrier.keys.toMutableList()
  }

  override fun get(carrier: Map<String, String>?, key: String): String? {
    return carrier?.get(key)
  }
}

class MapSetter : TextMapSetter<MutableMap<String, String>> {
  override fun set(carrier: MutableMap<String, String>?, key: String, value: String) {
    carrier?.put(key, value)
  }
}
