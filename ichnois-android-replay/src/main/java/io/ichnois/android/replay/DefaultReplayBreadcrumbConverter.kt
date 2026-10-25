package io.ichnois.android.replay

import io.ichnois.Breadcrumb
import io.ichnois.Hint
import io.ichnois.ReplayBreadcrumbConverter
import io.ichnois.IchnoisCoreLevel
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreOptions.BeforeBreadcrumbCallback
import io.ichnois.SpanDataConvention
import io.ichnois.TypeCheckHint.ICHNOIS_REPLAY_NETWORK_DETAILS
import io.ichnois.rrweb.RRWebBreadcrumbEvent
import io.ichnois.rrweb.RRWebEvent
import io.ichnois.rrweb.RRWebSpanEvent
import io.ichnois.util.network.NetworkRequestData
import java.util.Collections
import kotlin.LazyThreadSafetyMode.NONE

public open class DefaultReplayBreadcrumbConverter() : ReplayBreadcrumbConverter {
  private var options: IchnoisCoreOptions? = null

  public constructor(options: IchnoisCoreOptions) : this() {
    // We modify options, so keep it around to make that explicit.
    this.options = options
    this.options?.beforeBreadcrumb = ReplayBeforeBreadcrumbCallback(options.beforeBreadcrumb)
  }

  internal companion object {
    private const val MAX_HTTP_NETWORK_DETAILS = 32
    private val snakecasePattern by lazy(NONE) { "_[a-z]".toRegex() }

    private val supportedNetworkData =
      HashSet<String>().apply {
        add("status_code")
        add("method")
        add("response_content_length")
        add("request_content_length")
        add("http.response_content_length")
        add("http.request_content_length")
      }
  }

  /**
   * Intercept the breadcrumb to process any Network Details data on the hint. Delegate to any
   * user-provided callback to provide the actual breadcrumb to process.
   */
  private inner class ReplayBeforeBreadcrumbCallback(
    private val delegate: BeforeBreadcrumbCallback?
  ) : BeforeBreadcrumbCallback {
    override fun execute(breadcrumb: Breadcrumb, hint: Hint): Breadcrumb? {
      val resultBreadcrumb =
        if (delegate != null) {
          delegate.execute(breadcrumb, hint)
        } else {
          breadcrumb
        }

      resultBreadcrumb?.let { finalBreadcrumb ->
        extractNetworkRequestDataFromHint(finalBreadcrumb, hint)?.let { networkData ->
          httpNetworkDetails[finalBreadcrumb] = networkData
        }
      }

      return resultBreadcrumb
    }

    private fun extractNetworkRequestDataFromHint(
      breadcrumb: Breadcrumb,
      breadcrumbHint: Hint,
    ): NetworkRequestData? {
      if (breadcrumb.type != "http" && breadcrumb.category != "http") {
        return null
      }

      return breadcrumbHint.get(ICHNOIS_REPLAY_NETWORK_DETAILS) as? NetworkRequestData
    }
  }

  private var lastConnectivityState: String? = null

  private val httpNetworkDetails =
    Collections.synchronizedMap(
      object : LinkedHashMap<Breadcrumb, NetworkRequestData>() {
        override fun removeEldestEntry(
          eldest: MutableMap.MutableEntry<Breadcrumb, NetworkRequestData>?
        ): Boolean {
          return size > MAX_HTTP_NETWORK_DETAILS
        }
      }
    )

  override fun convert(breadcrumb: Breadcrumb): RRWebEvent? {
    var breadcrumbMessage: String? = null
    val breadcrumbCategory: String?
    var breadcrumbLevel: IchnoisCoreLevel? = null
    val breadcrumbData = mutableMapOf<String, Any?>()

    when {
      breadcrumb.category == "http" -> {
        return if (breadcrumb.isValidForRRWebSpan()) {
          breadcrumb.toRRWebSpanEvent()
        } else {
          null
        }
      }

      breadcrumb.type == "navigation" && breadcrumb.category == "app.lifecycle" -> {
        breadcrumbCategory = "app.${breadcrumb.data["state"]}"
      }

      breadcrumb.type == "navigation" && breadcrumb.category == "device.orientation" -> {
        breadcrumbCategory = breadcrumb.category!!
        val position = breadcrumb.data["position"]

        if (position == "landscape" || position == "portrait") {
          breadcrumbData["position"] = position
        } else {
          return null
        }
      }

      breadcrumb.type == "navigation" -> {
        breadcrumbCategory = "navigation"
        breadcrumbData["to"] =
          when {
            breadcrumb.data["state"] == "resumed" -> {
              (breadcrumb.data["screen"] as? String)?.substringAfterLast('.')
            }
            "to" in breadcrumb.data -> breadcrumb.data["to"] as? String
            else -> null
          } ?: return null
      }

      breadcrumb.category == "ui.click" -> {
        breadcrumbCategory = "ui.tap"
        breadcrumbMessage =
          (breadcrumb.data["view.id"]
            ?: breadcrumb.data["view.tag"]
            ?: breadcrumb.data["view.class"])
            as? String ?: return null

        breadcrumbData.putAll(breadcrumb.data)
      }

      breadcrumb.type == "system" && breadcrumb.category == "network.event" -> {
        breadcrumbCategory = "device.connectivity"
        breadcrumbData["state"] =
          when {
            breadcrumb.data["action"] == "NETWORK_LOST" -> "offline"
            "network_type" in breadcrumb.data -> {
              if (!(breadcrumb.data["network_type"] as? String).isNullOrEmpty()) {
                breadcrumb.data["network_type"]
              } else {
                return null
              }
            }
            else -> return null
          }

        if (lastConnectivityState == breadcrumbData["state"]) {
          // Debounce same state
          return null
        }

        lastConnectivityState = breadcrumbData["state"] as? String
      }

      breadcrumb.data["action"] == "BATTERY_CHANGED" -> {
        breadcrumbCategory = "device.battery"
        breadcrumbData.putAll(breadcrumb.data.filterKeys { it == "level" || it == "charging" })
      }

      else -> {
        breadcrumbCategory = breadcrumb.category
        breadcrumbMessage = breadcrumb.message
        breadcrumbLevel = breadcrumb.level
        breadcrumbData.putAll(breadcrumb.data)
      }
    }

    return if (!breadcrumbCategory.isNullOrEmpty()) {
      RRWebBreadcrumbEvent().apply {
        timestamp = breadcrumb.timestamp.time
        breadcrumbTimestamp = breadcrumb.timestamp.time / 1000.0
        breadcrumbType = "default"
        category = breadcrumbCategory
        message = breadcrumbMessage
        level = breadcrumbLevel
        data = breadcrumbData
      }
    } else {
      null
    }
  }

  private fun Breadcrumb.isValidForRRWebSpan(): Boolean {
    return !(data["url"] as? String).isNullOrEmpty() &&
      SpanDataConvention.HTTP_START_TIMESTAMP in data &&
      SpanDataConvention.HTTP_END_TIMESTAMP in data
  }

  private fun String.snakeToCamelCase(): String {
    return replace(snakecasePattern) { it.value.last().toString().uppercase() }
  }

  private fun Breadcrumb.toRRWebSpanEvent(): RRWebSpanEvent {
    val breadcrumb = this
    val httpStartTimestamp = breadcrumb.data[SpanDataConvention.HTTP_START_TIMESTAMP]
    val httpEndTimestamp = breadcrumb.data[SpanDataConvention.HTTP_END_TIMESTAMP]

    return RRWebSpanEvent().apply {
      timestamp = breadcrumb.timestamp.time
      op = "resource.http"
      description = breadcrumb.data["url"] as String

      // Can be double if it was serialized to disk
      startTimestamp =
        if (httpStartTimestamp is Double) {
          httpStartTimestamp / 1000.0
        } else {
          (httpStartTimestamp as Long) / 1000.0
        }

      endTimestamp =
        if (httpEndTimestamp is Double) {
          httpEndTimestamp / 1000.0
        } else {
          (httpEndTimestamp as Long) / 1000.0
        }

      val breadcrumbData = mutableMapOf<String, Any?>()

      val networkDetailData = httpNetworkDetails.remove(breadcrumb)

      // Add Network Details data when available
      networkDetailData?.let { networkData ->
        networkData.method?.let { breadcrumbData["method"] = it }
        networkData.statusCode?.let { breadcrumbData["statusCode"] = it }
        networkData.requestBodySize?.let { breadcrumbData["requestBodySize"] = it }
        networkData.responseBodySize?.let { breadcrumbData["responseBodySize"] = it }

        networkData.request?.let { request ->
          val requestData = mutableMapOf<String, Any?>()
          request.size?.let { requestData["size"] = it }
          request.body?.let {
            requestData["body"] = it.body
            it.warnings?.let { warnings ->
              requestData["warnings"] = warnings.map { warning -> warning.value }
            }
          }

          if (request.headers.isNotEmpty()) {
            requestData["headers"] = request.headers
          }

          if (requestData.isNotEmpty()) {
            breadcrumbData["request"] = requestData
          }
        }

        networkData.response?.let { response ->
          val responseData = mutableMapOf<String, Any?>()
          response.size?.let { responseData["size"] = it }
          response.body?.let {
            responseData["body"] = it.body
            it.warnings?.let { warnings ->
              responseData["warnings"] = warnings.map { warning -> warning.value }
            }
          }

          if (response.headers.isNotEmpty()) {
            responseData["headers"] = response.headers
          }

          if (responseData.isNotEmpty()) {
            breadcrumbData["response"] = responseData
          }
        }
      }

      // Original breadcrumb http data
      for ((key, value) in breadcrumb.data) {
        if (key in supportedNetworkData) {
          val formattedKey =
            key.replace("content_length", "body_size").substringAfter(".").snakeToCamelCase()
          breadcrumbData[formattedKey] = value
        }
      }

      data = breadcrumbData
    }
  }
}
