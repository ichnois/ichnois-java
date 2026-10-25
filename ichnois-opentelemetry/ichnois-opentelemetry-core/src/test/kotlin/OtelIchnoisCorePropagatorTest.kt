package io.ichnois.opentelemetry

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapSetter
import io.opentelemetry.semconv.UrlAttributes
import io.ichnois.BaggageHeader
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreTraceHeader
import io.ichnois.opentelemetry.IchnoisCoreOtelKeys.ICHNOIS_BAGGAGE_KEY
import io.ichnois.opentelemetry.IchnoisCoreOtelKeys.ICHNOIS_SCOPES_KEY
import io.ichnois.opentelemetry.IchnoisCoreOtelKeys.ICHNOIS_TRACE_KEY
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OtelIchnoisCorePropagatorTest {
  val spanStorage: IchnoisCoreWeakSpanStorage = IchnoisCoreWeakSpanStorage.getInstance()

  @BeforeTest
  fun setup() {
    IchnoisCore.init("https://key@ichnois-h2jw.onrender.com/proj")
  }

  @AfterTest
  fun cleanup() {
    spanStorage.clear()
    IchnoisCore.close()
    Context.root().makeCurrent()
  }

  @Test
  fun `propagator registers for ichnois-trace and baggage`() {
    val propagator = OtelIchnoisCorePropagator()
    assertEquals(listOf("ichnois-trace", "baggage"), propagator.fields())
  }

  @Test
  fun `invalid ichnois trace header returns context without modification`() {
    val propagator = OtelIchnoisCorePropagator()
    val carrier: Map<String, String> =
      mapOf(
        "ichnois-trace" to "wrong",
        "baggage" to
          "ichnois-environment=production,ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=df71f5972f754b4c85af13ff5c07017d",
      )
    val scopeInContext = IchnoisCore.forkedRootScopes("test")

    val newContext =
      propagator.extract(
        Context.root().with(ICHNOIS_SCOPES_KEY, scopeInContext),
        carrier,
        MapGetter(),
      )

    val scopes = newContext.get(ICHNOIS_SCOPES_KEY)
    assertNotNull(scopes)
    assertSame(scopeInContext, scopes)
  }

  @Test
  fun `ignores incoming headers when strict continuation rejects org id`() {
    IchnoisCore.init { options ->
      options.dsn = "https://key@o2.ingest.ichnois.io/123"
      options.isStrictTraceContinuation = true
    }
    val propagator = OtelIchnoisCorePropagator()
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

  @Test
  fun `uses incoming headers`() {
    val propagator = OtelIchnoisCorePropagator()
    val carrier: Map<String, String> =
      mapOf(
        "ichnois-trace" to "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1",
        "baggage" to
          "ichnois-environment=production,ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=df71f5972f754b4c85af13ff5c07017d",
      )
    val newContext = propagator.extract(Context.root(), carrier, MapGetter())

    val span = Span.fromContext(newContext)
    assertEquals("f9118105af4a2d42b4124532cd1065ff", span.spanContext.traceId)
    assertEquals("424cffc8f94feeee", span.spanContext.spanId)
    assertTrue(span.spanContext.isSampled)

    assertEquals(
      "f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1",
      newContext.get(ICHNOIS_TRACE_KEY)?.value,
    )
    assertEquals(
      "ichnois-environment=production,ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=df71f5972f754b4c85af13ff5c07017d",
      newContext.get(ICHNOIS_BAGGAGE_KEY)?.toHeaderString(null),
    )
  }

  @Test
  fun `injects headers if no URL`() {
    val propagator = OtelIchnoisCorePropagator()
    val carrier = mutableMapOf<String, String>()

    val ichnoisSpan = mock<IOtelSpanWrapper>()
    whenever(ichnoisSpan.toIchnoisCoreTrace())
      .thenReturn(IchnoisCoreTraceHeader("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1"))
    whenever(ichnoisSpan.toBaggageHeader(anyOrNull()))
      .thenReturn(
        BaggageHeader(
          "ichnois-environment=production,ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=df71f5972f754b4c85af13ff5c07017d"
        )
      )
    val otelSpanContext =
      SpanContext.create(
        "f9118105af4a2d42b4124532cd1065ff",
        "424cffc8f94feeee",
        TraceFlags.getSampled(),
        TraceState.getDefault(),
      )
    val otelSpan = Span.wrap(otelSpanContext)
    spanStorage.storeIchnoisCoreSpan(otelSpanContext, ichnoisSpan)

    propagator.inject(Context.root().with(otelSpan), carrier, MapSetter())

    assertEquals("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1", carrier["ichnois-trace"])
    assertEquals(
      "ichnois-environment=production,ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=df71f5972f754b4c85af13ff5c07017d",
      carrier["baggage"],
    )
  }

  @Test
  fun `injects headers if URL in span attributes with default options`() {
    val propagator = OtelIchnoisCorePropagator()
    val carrier = mutableMapOf<String, String>()

    val otelAttributes = Attributes.of(UrlAttributes.URL_FULL, "https://example.com/some/path")
    val ichnoisSpan = mock<IOtelSpanWrapper>()
    whenever(ichnoisSpan.toIchnoisCoreTrace())
      .thenReturn(IchnoisCoreTraceHeader("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1"))
    whenever(ichnoisSpan.toBaggageHeader(anyOrNull()))
      .thenReturn(
        BaggageHeader(
          "ichnois-environment=production,ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=df71f5972f754b4c85af13ff5c07017d"
        )
      )
    whenever(ichnoisSpan.openTelemetrySpanAttributes).thenReturn(otelAttributes)
    val otelSpanContext =
      SpanContext.create(
        "f9118105af4a2d42b4124532cd1065ff",
        "424cffc8f94feeee",
        TraceFlags.getSampled(),
        TraceState.getDefault(),
      )
    val otelSpan = Span.wrap(otelSpanContext)
    spanStorage.storeIchnoisCoreSpan(otelSpanContext, ichnoisSpan)

    propagator.inject(Context.root().with(otelSpan), carrier, MapSetter())

    assertEquals("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1", carrier["ichnois-trace"])
    assertEquals(
      "ichnois-environment=production,ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=df71f5972f754b4c85af13ff5c07017d",
      carrier["baggage"],
    )
  }

  @Test
  fun `injects headers if URL in span attributes with tracePropagationTargets set to same url`() {
    IchnoisCore.init { options ->
      options.dsn = "https://key@ichnois-h2jw.onrender.com/proj"
      options.setTracePropagationTargets(listOf("ichnois.io"))
    }
    val propagator = OtelIchnoisCorePropagator()
    val carrier = mutableMapOf<String, String>()

    val otelAttributes = Attributes.of(UrlAttributes.URL_FULL, "https://example.com/some/path")
    val ichnoisSpan = mock<IOtelSpanWrapper>()
    whenever(ichnoisSpan.toIchnoisCoreTrace())
      .thenReturn(IchnoisCoreTraceHeader("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1"))
    whenever(ichnoisSpan.toBaggageHeader(anyOrNull()))
      .thenReturn(
        BaggageHeader(
          "ichnois-environment=production,ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=df71f5972f754b4c85af13ff5c07017d"
        )
      )
    whenever(ichnoisSpan.openTelemetrySpanAttributes).thenReturn(otelAttributes)
    val otelSpanContext =
      SpanContext.create(
        "f9118105af4a2d42b4124532cd1065ff",
        "424cffc8f94feeee",
        TraceFlags.getSampled(),
        TraceState.getDefault(),
      )
    val otelSpan = Span.wrap(otelSpanContext)
    spanStorage.storeIchnoisCoreSpan(otelSpanContext, ichnoisSpan)

    propagator.inject(Context.root().with(otelSpan), carrier, MapSetter())

    assertEquals("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1", carrier["ichnois-trace"])
    assertEquals(
      "ichnois-environment=production,ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=df71f5972f754b4c85af13ff5c07017d",
      carrier["baggage"],
    )
  }

  @Test
  fun `does not inject headers if URL in span attributes with tracePropagationTargets set to different url`() {
    IchnoisCore.init { options ->
      options.dsn = "https://key@ichnois-h2jw.onrender.com/proj"
      options.setTracePropagationTargets(listOf("github.com"))
    }
    val propagator = OtelIchnoisCorePropagator()
    val carrier = mutableMapOf<String, String>()

    val otelAttributes = Attributes.of(UrlAttributes.URL_FULL, "https://example.com/some/path")
    val ichnoisSpan = mock<IOtelSpanWrapper>()
    whenever(ichnoisSpan.toIchnoisCoreTrace())
      .thenReturn(IchnoisCoreTraceHeader("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1"))
    whenever(ichnoisSpan.toBaggageHeader(anyOrNull()))
      .thenReturn(
        BaggageHeader(
          "ichnois-environment=production,ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=df71f5972f754b4c85af13ff5c07017d"
        )
      )
    whenever(ichnoisSpan.openTelemetrySpanAttributes).thenReturn(otelAttributes)
    val otelSpanContext =
      SpanContext.create(
        "f9118105af4a2d42b4124532cd1065ff",
        "424cffc8f94feeee",
        TraceFlags.getSampled(),
        TraceState.getDefault(),
      )
    val otelSpan = Span.wrap(otelSpanContext)
    spanStorage.storeIchnoisCoreSpan(otelSpanContext, ichnoisSpan)

    propagator.inject(Context.root().with(otelSpan), carrier, MapSetter())

    assertNull(carrier["ichnois-trace"])
    assertNull(carrier["baggage"])
  }

  @Test
  fun `does not inject headers if URL in span attributes with tracePropagationTargets set to same url but trace sampling disabled`() {
    IchnoisCore.init { options ->
      options.dsn = "https://key@ichnois-h2jw.onrender.com/proj"
      options.setTracePropagationTargets(listOf("ichnois.io"))
      options.isTraceSampling = false
    }
    val propagator = OtelIchnoisCorePropagator()
    val carrier = mutableMapOf<String, String>()

    val otelAttributes = Attributes.of(UrlAttributes.URL_FULL, "https://example.com/some/path")
    val ichnoisSpan = mock<IOtelSpanWrapper>()
    whenever(ichnoisSpan.toIchnoisCoreTrace())
      .thenReturn(IchnoisCoreTraceHeader("f9118105af4a2d42b4124532cd1065ff-424cffc8f94feeee-1"))
    whenever(ichnoisSpan.toBaggageHeader(anyOrNull()))
      .thenReturn(
        BaggageHeader(
          "ichnois-environment=production,ichnois-public_key=test-public-key,ichnois-sample_rand=0.456789,ichnois-sample_rate=0.5,ichnois-sampled=true,ichnois-trace_id=df71f5972f754b4c85af13ff5c07017d"
        )
      )
    whenever(ichnoisSpan.openTelemetrySpanAttributes).thenReturn(otelAttributes)
    val otelSpanContext =
      SpanContext.create(
        "f9118105af4a2d42b4124532cd1065ff",
        "424cffc8f94feeee",
        TraceFlags.getSampled(),
        TraceState.getDefault(),
      )
    val otelSpan = Span.wrap(otelSpanContext)
    spanStorage.storeIchnoisCoreSpan(otelSpanContext, ichnoisSpan)

    propagator.inject(Context.root().with(otelSpan), carrier, MapSetter())

    assertNull(carrier["ichnois-trace"])
    assertNull(carrier["baggage"])
  }

  @Test
  fun `does not inject headers if ichnois span missing`() {
    val propagator = OtelIchnoisCorePropagator()
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

    assertNull(carrier["ichnois-trace"])
    assertNull(carrier["baggage"])
  }

  @Test
  fun `does not inject headers if ichnois span noop`() {
    val propagator = OtelIchnoisCorePropagator()
    val carrier = mutableMapOf<String, String>()

    val ichnoisSpan = mock<IOtelSpanWrapper>()
    whenever(ichnoisSpan.isNoOp).thenReturn(true)
    val otelSpanContext =
      SpanContext.create(
        "f9118105af4a2d42b4124532cd1065ff",
        "424cffc8f94feeee",
        TraceFlags.getSampled(),
        TraceState.getDefault(),
      )
    val otelSpan = Span.wrap(otelSpanContext)
    spanStorage.storeIchnoisCoreSpan(otelSpanContext, ichnoisSpan)

    propagator.inject(Context.root().with(otelSpan), carrier, MapSetter())

    assertNull(carrier["ichnois-trace"])
    assertNull(carrier["baggage"])
  }

  @Test
  fun `does not inject headers if span is missing`() {
    val propagator = OtelIchnoisCorePropagator()
    val carrier = mutableMapOf<String, String>()

    propagator.inject(Context.root(), carrier, MapSetter())

    assertNull(carrier["ichnois-trace"])
    assertNull(carrier["baggage"])
  }

  @Test
  fun `does not inject headers if span is invalid`() {
    val propagator = OtelIchnoisCorePropagator()
    val carrier = mutableMapOf<String, String>()

    propagator.inject(Context.root().with(Span.getInvalid()), carrier, MapSetter())

    assertNull(carrier["ichnois-trace"])
    assertNull(carrier["baggage"])
  }
}

class MapGetter : TextMapGetter<Map<String, String>> {
  override fun keys(carrier: Map<String, String>): MutableIterable<String> =
    carrier.keys.toMutableList()

  override fun get(carrier: Map<String, String>?, key: String): String? = carrier?.get(key)
}

class MapSetter : TextMapSetter<MutableMap<String, String>> {
  override fun set(carrier: MutableMap<String, String>?, key: String, value: String) {
    carrier?.set(key, value)
  }
}
