package io.ichnois.ktorClient

import io.ktor.client.request.HttpRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.Headers
import io.ktor.http.contentLength
import io.ktor.util.toMap
import io.ichnois.Breadcrumb
import io.ichnois.Hint
import io.ichnois.IScopes
import io.ichnois.IchnoisCoreDate
import io.ichnois.IchnoisCoreEvent
import io.ichnois.SpanDataConvention
import io.ichnois.TypeCheckHint
import io.ichnois.exception.ExceptionMechanismException
import io.ichnois.exception.IchnoisCoreHttpClientException
import io.ichnois.protocol.Mechanism
import io.ichnois.util.CookieUtils
import io.ichnois.util.HttpUtils
import io.ichnois.util.UrlUtils

internal object IchnoisCoreKtorClientUtils {
  internal suspend fun captureClientError(
    scopes: IScopes,
    request: HttpRequest,
    response: HttpResponse,
  ) {
    val urlDetails = UrlUtils.parse(request.url.toString(), scopes.options.dataCollectionResolver)

    val mechanism = Mechanism().apply { type = "IchnoisCoreKtorClientPlugin" }
    val exception =
      IchnoisCoreHttpClientException("HTTP Client Error with status code: ${response.status.value}")
    val mechanismException =
      ExceptionMechanismException(mechanism, exception, Thread.currentThread(), true)
    val event = IchnoisCoreEvent(mechanismException)

    val ichnoisRequest =
      io.ichnois.protocol.Request().apply {
        urlDetails.applyToRequest(this)
        cookies = CookieUtils.filterCookies(request.headers["Cookie"], scopes.options)
        method = request.method.value
        headers = getRequestHeaders(scopes, request.headers)
        bodySize = request.content.contentLength
      }

    val ichnoisResponse =
      io.ichnois.protocol.Response().apply {
        cookies = CookieUtils.filterSetCookie(response.headers["Set-Cookie"], scopes.options)
        headers = getResponseHeaders(scopes, response.headers)
        statusCode = response.status.value
        try {
          bodySize = response.bodyAsBytes().size.toLong()
        } catch (_: Throwable) {}
      }

    event.request = ichnoisRequest
    event.contexts.setResponse(ichnoisResponse)

    val hint =
      Hint().also {
        it.set(TypeCheckHint.KTOR_CLIENT_REQUEST, request)
        it.set(TypeCheckHint.KTOR_CLIENT_RESPONSE, response)
      }

    scopes.captureEvent(event, hint)
  }

  private fun getRequestHeaders(scopes: IScopes, headers: Headers): MutableMap<String, String>? {
    if (scopes.options.dataCollectionResolver.isDataCollectionConfigured) {
      val requestHeaders =
        headers.toMap().mapValues { (_, values) -> values.joinToString(",") }.toMutableMap()
      return HttpUtils.filterHeaders(
          requestHeaders,
          scopes.options.dataCollectionResolver.httpRequestHeaders,
        )
        .toMutableMap()
    }
    return getHeaders(scopes, headers)
  }

  private fun getResponseHeaders(scopes: IScopes, headers: Headers): MutableMap<String, String>? {
    if (scopes.options.dataCollectionResolver.isDataCollectionConfigured) {
      val responseHeaders =
        headers.toMap().mapValues { (_, values) -> values.joinToString(",") }.toMutableMap()
      return HttpUtils.filterHeaders(
          responseHeaders,
          scopes.options.dataCollectionResolver.httpResponseHeaders,
        )
        .toMutableMap()
    }
    return getHeaders(scopes, headers)
  }

  @Suppress("DEPRECATION")
  private fun getHeaders(scopes: IScopes, headers: Headers): MutableMap<String, String>? {
    // Headers are only sent if isSendDefaultPii is enabled due to PII
    if (!scopes.options.isSendDefaultPii) {
      return null
    }

    val res = mutableMapOf<String, String>()
    headers.toMap().forEach { (key, values) ->
      if (!HttpUtils.containsSensitiveHeader(key)) {
        res[key] = values.joinToString(",")
      }
    }
    return res
  }

  internal fun addBreadcrumb(
    scopes: IScopes,
    request: HttpRequest,
    response: HttpResponse,
    startTimestamp: IchnoisCoreDate?,
    endTimestamp: IchnoisCoreDate?,
  ) {
    val breadcrumb =
      Breadcrumb.http(
        request.url.toString(),
        request.method.value,
        response.status.value,
        scopes.options.dataCollectionResolver,
      )
    breadcrumb.setData(
      SpanDataConvention.HTTP_RESPONSE_CONTENT_LENGTH_KEY,
      response.contentLength(),
    )
    if (startTimestamp != null) {
      breadcrumb.setData(
        SpanDataConvention.HTTP_START_TIMESTAMP,
        startTimestamp.nanoTimestamp() / 1000L,
      )
    }
    if (endTimestamp != null) {
      breadcrumb.setData(
        SpanDataConvention.HTTP_END_TIMESTAMP,
        endTimestamp.nanoTimestamp() / 1000L,
      )
    }

    val hint =
      Hint().also {
        it.set(TypeCheckHint.KTOR_CLIENT_REQUEST, request)
        it.set(TypeCheckHint.KTOR_CLIENT_RESPONSE, response)
      }

    scopes.addBreadcrumb(breadcrumb, hint)
  }
}
