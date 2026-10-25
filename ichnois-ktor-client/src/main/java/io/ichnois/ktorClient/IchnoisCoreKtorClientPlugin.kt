package io.ichnois.ktorClient

import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ichnois.BaggageHeader
import io.ichnois.BuildConfig
import io.ichnois.DataCategory
import io.ichnois.HttpStatusCodeRange
import io.ichnois.IScopes
import io.ichnois.ISpan
import io.ichnois.ScopesAdapter
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreDate
import io.ichnois.IchnoisCoreIntegrationPackageStorage
import io.ichnois.IchnoisCoreLevel
import io.ichnois.IchnoisCoreOptions
import io.ichnois.SpanStatus
import io.ichnois.clientreport.DiscardReason
import io.ichnois.kotlin.IchnoisCoreContext
import io.ichnois.util.ExceptionUtils
import io.ichnois.util.IntegrationUtils.addIntegrationToSdkVersion
import io.ichnois.util.Platform
import io.ichnois.util.PropagationTargetsUtils
import io.ichnois.util.SpanUtils
import io.ichnois.util.TracingUtils
import io.ichnois.util.UrlUtils
import kotlinx.coroutines.withContext

/** Configuration for the IchnoisCore Ktor client plugin. */
public class IchnoisCoreKtorClientPluginConfig {
  /** The [IScopes] instance to use. Defaults to [ScopesAdapter.getInstance]. */
  public var scopes: IScopes = ScopesAdapter.getInstance()

  /** Callback to customize or drop spans before they are created. Return null to drop the span. */
  public var beforeSpan: BeforeSpanCallback? = null

  /** Whether to capture HTTP client errors as IchnoisCore events. Defaults to true. */
  public var captureFailedRequests: Boolean = true

  /**
   * The HTTP status code ranges that should be considered as failed requests. Defaults to 500-599
   * (server errors).
   */
  public var failedRequestStatusCodes: List<HttpStatusCodeRange> =
    listOf(HttpStatusCodeRange(HttpStatusCodeRange.DEFAULT_MIN, HttpStatusCodeRange.DEFAULT_MAX))

  /**
   * The list of targets (URLs) for which failed requests should be captured. Supports regex
   * patterns. Defaults to capture all requests.
   */
  public var failedRequestTargets: List<String> = listOf(IchnoisCoreOptions.DEFAULT_PROPAGATION_TARGETS)

  /** Callback interface for customizing spans before they are created. */
  public fun interface BeforeSpanCallback {
    /**
     * Customize or drop a span before it's created.
     *
     * @param span The span to customize
     * @param request The HTTP request being executed
     * @return The customized span, or null to drop the span
     */
    public fun execute(span: ISpan, request: HttpRequest): ISpan?
  }

  /**
   * Forcefully use the passed in scope instead of relying on the one injected by [IchnoisCoreContext].
   * Used for testing.
   */
  internal var forceScopes: Boolean = false
}

internal const val ICHNOIS_KTOR_CLIENT_PLUGIN_KEY = "IchnoisCoreKtorClientPlugin"
internal const val TRACE_ORIGIN = "auto.http.ktor-client"

/**
 * IchnoisCore plugin for Ktor HTTP client that provides automatic instrumentation for HTTP requests,
 * including error capturing, request/response breadcrumbs, and distributed tracing.
 */
public val IchnoisCoreKtorClientPlugin: ClientPlugin<IchnoisCoreKtorClientPluginConfig> =
  createClientPlugin(ICHNOIS_KTOR_CLIENT_PLUGIN_KEY, ::IchnoisCoreKtorClientPluginConfig) {
    // Init
    IchnoisCoreIntegrationPackageStorage.getInstance()
      .addPackage("maven:io.ichnois:ichnois-ktor-client", BuildConfig.VERSION_NAME)
    addIntegrationToSdkVersion("KtorClient")

    // Options
    val scopes = pluginConfig.scopes
    val beforeSpan = pluginConfig.beforeSpan
    val captureFailedRequests = pluginConfig.captureFailedRequests
    val failedRequestStatusCodes = pluginConfig.failedRequestStatusCodes
    val failedRequestTargets = pluginConfig.failedRequestTargets
    val forceScopes = pluginConfig.forceScopes

    // Attributes
    // Request start time for breadcrumbs
    val requestStartTimestampKey = AttributeKey<IchnoisCoreDate>("IchnoisCoreRequestStartTimestamp")
    // Span associated with the request
    val requestSpanKey = AttributeKey<ISpan>("IchnoisCoreRequestSpan")

    onRequest { request, _ ->
      val effectiveScopes = if (forceScopes) scopes else IchnoisCore.getCurrentScopes()
      request.attributes.put(
        requestStartTimestampKey,
        effectiveScopes.options.dateProvider.now(),
      )

      val parentSpan: ISpan? =
        if (forceScopes) scopes.getSpan()
        else if (Platform.isAndroid()) effectiveScopes.transaction else effectiveScopes.span

      val spanOp = "http.client"
      val rawUrl = request.url.buildString()
      val urlDetails = UrlUtils.parse(rawUrl, effectiveScopes.options.dataCollectionResolver)
      val spanDescription = "${request.method.value.toString()} ${urlDetails.urlOrFallback}"
      val span: ISpan? = parentSpan?.startChild(spanOp, spanDescription)
      if (span != null) {
        span.spanContext.origin = TRACE_ORIGIN
        request.attributes.put(requestSpanKey, span)
      }

      if (
        !SpanUtils.isIgnored(
          effectiveScopes.options.getIgnoredSpanOrigins(),
          TRACE_ORIGIN,
        )
      ) {
        TracingUtils.traceIfAllowed(
            effectiveScopes,
            rawUrl,
            request.headers.getAll(BaggageHeader.BAGGAGE_HEADER),
            span,
          )
          ?.let { tracingHeaders ->
            request.headers[tracingHeaders.ichnoisTraceHeader.name] =
              tracingHeaders.ichnoisTraceHeader.value
            tracingHeaders.baggageHeader?.let {
              request.headers.remove(BaggageHeader.BAGGAGE_HEADER)
              request.headers[it.name] = it.value
            }
            tracingHeaders.w3cTraceparentHeader?.let { request.headers[it.name] = it.value }
          }
      }
    }

    client.requestPipeline.intercept(HttpRequestPipeline.Before) {
      try {
        proceed()
      } catch (t: Throwable) {
        context.attributes.getOrNull(requestSpanKey)?.apply {
          throwable = t
          status = SpanStatus.INTERNAL_ERROR
          finish()
        }
        throw t
      }
    }

    onResponse { response ->
      val request = response.request
      val startTimestamp = response.call.attributes.getOrNull(requestStartTimestampKey)
      val endTimestamp =
        (if (forceScopes) scopes else IchnoisCore.getCurrentScopes()).options.dateProvider.now()

      if (
        captureFailedRequests &&
          failedRequestStatusCodes.any { it.isInRange(response.status.value) } &&
          PropagationTargetsUtils.contain(failedRequestTargets, request.url.toString())
      ) {
        IchnoisCoreKtorClientUtils.captureClientError(
          if (forceScopes) scopes else IchnoisCore.getCurrentScopes(),
          request,
          response,
        )
      }

      IchnoisCoreKtorClientUtils.addBreadcrumb(
        if (forceScopes) scopes else IchnoisCore.getCurrentScopes(),
        request,
        response,
        startTimestamp,
        endTimestamp,
      )

      response.call.attributes.getOrNull(requestSpanKey)?.let { span ->
        var result: ISpan? = span

        if (beforeSpan != null) {
          val wasSampled = span.isSampled == true
          result =
            try {
              beforeSpan.execute(span, request)
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
              ExceptionUtils.rethrowIfFatal(e)
              span.spanContext.sampled = false
              if (wasSampled) {
                (if (forceScopes) scopes else IchnoisCore.getCurrentScopes())
                  .options
                  .clientReportRecorder
                  .recordLostEvent(DiscardReason.CALLBACK_ERROR, DataCategory.Span)
              }
              (if (forceScopes) scopes else IchnoisCore.getCurrentScopes())
                .options
                .logger
                .log(
                  IchnoisCoreLevel.ERROR,
                  "The beforeSpan callback threw an exception in IchnoisCoreKtorClientPlugin. Dropping span.",
                  e,
                )
              null
            }
        }

        if (result == null) {
          // span is dropped
          span.spanContext.sampled = false
        }

        val spanStatus = SpanStatus.fromHttpStatusCode(response.status.value)
        span.finish(spanStatus, endTimestamp)
      }
    }

    on(IchnoisCoreKtorClientPluginContextHook(scopes)) { block -> block() }
  }

/**
 * Context hook to manage scopes during request handling. Forks the current scope and uses
 * [IchnoisCoreContext] to ensure that the whole pipeline runs within the correct scopes.
 */
public open class IchnoisCoreKtorClientPluginContextHook(protected val scopes: IScopes) :
  ClientHook<suspend (suspend () -> Unit) -> Unit> {
  private val phase = PipelinePhase("IchnoisCoreKtorClientPluginContext")

  override fun install(client: HttpClient, handler: suspend (suspend () -> Unit) -> Unit) {
    client.requestPipeline.insertPhaseBefore(HttpRequestPipeline.Before, phase)
    client.requestPipeline.intercept(phase) {
      val scopes =
        this@IchnoisCoreKtorClientPluginContextHook.scopes.forkedCurrentScope(
          ICHNOIS_KTOR_CLIENT_PLUGIN_KEY
        )
      withContext(IchnoisCoreContext(scopes)) { proceed() }
    }
  }
}
