package io.ichnois.opentelemetry

import com.google.common.truth.Truth.assertThat
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.samplers.Sampler
import io.opentelemetry.sdk.trace.samplers.SamplingDecision
import io.ichnois.DataCategory
import io.ichnois.IScopes
import io.ichnois.SamplingContext
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreTraceHeader
import io.ichnois.SpanId
import io.ichnois.TransactionContext
import io.ichnois.TransactionOptions
import io.ichnois.clientreport.DiscardReason
import io.ichnois.protocol.IchnoisCoreId
import kotlin.test.AfterTest
import kotlin.test.Test
import org.mockito.AdditionalAnswers.delegatesTo
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class IchnoisCoreSamplerTest {
  private val onDiscard = mock<IchnoisCoreOptions.OnDiscardCallback>()
  private val options =
    IchnoisCoreOptions().apply {
      tracesSampleRate = 1.0
      profilesSampleRate = 1.0
      tracesSampler = IchnoisCoreOptions.TracesSamplerCallback { throw IllegalStateException("sampler") }
      this.onDiscard = this@IchnoisCoreSamplerTest.onDiscard
    }
  private val scopes = mock<IScopes>().also { whenever(it.options).thenReturn(options) }
  private val sampler = IchnoisCoreSampler(scopes)

  @AfterTest
  fun tearDown() {
    IchnoisCoreWeakSpanStorage.getInstance().clear()
  }

  @Test
  fun `throwing tracesSampler drops root and reports callback errors alongside sample rate losses`() {
    for (parentSampled in listOf(null, false, true)) {
      val traceId = IchnoisCoreId()
      val context =
        if (parentSampled == null) Context.root()
        else
          Context.root()
            .with(
              IchnoisCoreOtelKeys.ICHNOIS_TRACE_KEY,
              IchnoisCoreTraceHeader(traceId, SpanId(), parentSampled),
            )
      val result =
        sampler.shouldSample(
          context,
          traceId.toString(),
          "root",
          SpanKind.INTERNAL,
          Attributes.empty(),
          emptyList(),
        ) as IchnoisCoreSamplingResult

      assertThat(result.decision).isEqualTo(SamplingDecision.RECORD_ONLY)
      assertThat(result.ichnoisDecision.sampled).isFalse()
      assertThat(result.ichnoisDecision.profileSampled).isFalse()
    }

    verify(onDiscard, times(3)).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Transaction, 1)
    verify(onDiscard, times(3)).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Span, 1)
    verify(onDiscard, times(3)).execute(DiscardReason.SAMPLE_RATE, DataCategory.Transaction, 1)
    verify(onDiscard, times(3)).execute(DiscardReason.SAMPLE_RATE, DataCategory.Span, 1)
    verifyNoMoreInteractions(onDiscard)
  }

  @Test
  fun `children of failed sampling decisions retain sample rate accounting`() {
    val rootResult =
      sampler.shouldSample(
        Context.root(),
        IchnoisCoreId().toString(),
        "root",
        SpanKind.INTERNAL,
        Attributes.empty(),
        emptyList(),
      ) as IchnoisCoreSamplingResult
    val restored = OtelSamplingUtil.extractSamplingDecision(rootResult.attributes)!!
    assertThat(restored.sampled).isFalse()
    assertThat(restored.sampleRand).isEqualTo(rootResult.ichnoisDecision.sampleRand)

    val parentContext =
      io.opentelemetry.api.trace.SpanContext.create(
        IchnoisCoreId().toString(),
        SpanId().toString(),
        TraceFlags.getDefault(),
        TraceState.getDefault(),
      )
    val parent =
      mock<IOtelSpanWrapper>().also {
        whenever(it.samplingDecision).thenReturn(restored)
      }
    IchnoisCoreWeakSpanStorage.getInstance().storeIchnoisCoreSpan(parentContext, parent)
    val childResult =
      sampler.shouldSample(
        Span.wrap(parentContext).storeInContext(Context.root()),
        parentContext.traceId,
        "child",
        SpanKind.INTERNAL,
        Attributes.empty(),
        emptyList(),
      ) as IchnoisCoreSamplingResult

    assertThat(childResult.decision).isEqualTo(SamplingDecision.RECORD_ONLY)
    assertThat(childResult.ichnoisDecision.sampled).isFalse()
    verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Transaction, 1)
    verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Span, 1)
    verify(onDiscard).execute(DiscardReason.SAMPLE_RATE, DataCategory.Transaction, 1)
    verify(onDiscard, times(2)).execute(DiscardReason.SAMPLE_RATE, DataCategory.Span, 1)
    verifyNoMoreInteractions(onDiscard)
  }

  @Test
  fun `IchnoisCore API sampling failure reports before forwarding through span factory`() {
    val context = TransactionContext("root", "op")
    context.samplingDecision =
      options.internalTracesSampler.sample(SamplingContext(context, null, 0.0, null))
    verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Transaction, 1)
    verify(onDiscard).execute(DiscardReason.CALLBACK_ERROR, DataCategory.Span, 1)
    verifyNoMoreInteractions(onDiscard)
    val recordingSampler = mock<Sampler>(defaultAnswer = delegatesTo(sampler))
    SdkTracerProvider.builder().setSampler(recordingSampler).build().use { provider ->
      val openTelemetry =
        mock<OpenTelemetry>().also {
          whenever(it.tracerProvider).thenReturn(provider)
        }
      OtelSpanFactory(openTelemetry).createTransaction(context, scopes, TransactionOptions(), null)
      val attributes = argumentCaptor<Attributes>()
      verify(recordingSampler).shouldSample(any(), any(), any(), any(), attributes.capture(), any())
      val restored = OtelSamplingUtil.extractSamplingDecision(attributes.firstValue)!!
      assertThat(restored.sampled).isFalse()
      assertThat(restored.profileSampled).isFalse()
    }

    verifyNoMoreInteractions(onDiscard)
  }

  @Test
  fun `null tracesSampler result uses normal sample rate accounting`() {
    options.tracesSampler = IchnoisCoreOptions.TracesSamplerCallback { null }
    options.tracesSampleRate = 0.0
    val result =
      sampler.shouldSample(
        Context.root(),
        IchnoisCoreId().toString(),
        "root",
        SpanKind.INTERNAL,
        Attributes.empty(),
        emptyList(),
      ) as IchnoisCoreSamplingResult

    assertThat(result.ichnoisDecision.sampled).isFalse()
    verify(onDiscard).execute(DiscardReason.SAMPLE_RATE, DataCategory.Transaction, 1)
    verify(onDiscard).execute(DiscardReason.SAMPLE_RATE, DataCategory.Span, 1)
    verifyNoMoreInteractions(onDiscard)
  }
}
