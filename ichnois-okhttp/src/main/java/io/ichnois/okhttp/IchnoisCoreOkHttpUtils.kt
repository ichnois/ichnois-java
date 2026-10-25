package io.ichnois.okhttp

import io.ichnois.Hint
import io.ichnois.IScopes
import io.ichnois.IchnoisCoreEvent
import io.ichnois.TypeCheckHint
import io.ichnois.exception.ExceptionMechanismException
import io.ichnois.exception.IchnoisCoreHttpClientException
import io.ichnois.protocol.Mechanism
import io.ichnois.util.CookieUtils
import io.ichnois.util.HttpUtils
import io.ichnois.util.UrlUtils
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response

internal object IchnoisCoreOkHttpUtils {
  internal fun captureClientError(scopes: IScopes, request: Request, response: Response) {
    // not possible to get a parameterized url, but we remove at least the
    // query string and the fragment.
    // url example: https://api.github.com/users/getichnois/repos/#fragment?query=query
    // url will be: https://api.github.com/users/getichnois/repos/
    // ideally we'd like a parameterized url: https://api.github.com/users/{user}/repos/
    // but that's not possible
    val urlDetails = UrlUtils.parse(request.url.toString(), scopes.options.dataCollectionResolver)

    val mechanism = Mechanism().apply { type = "IchnoisCoreOkHttpInterceptor" }
    val exception =
      IchnoisCoreHttpClientException("HTTP Client Error with status code: ${response.code}")
    val mechanismException =
      ExceptionMechanismException(mechanism, exception, Thread.currentThread(), true)
    val event = IchnoisCoreEvent(mechanismException)

    val hint = Hint()
    hint.set(TypeCheckHint.OKHTTP_REQUEST, request)
    hint.set(TypeCheckHint.OKHTTP_RESPONSE, response)

    val ichnoisRequest =
      io.ichnois.protocol.Request().apply {
        urlDetails.applyToRequest(this)
        cookies = CookieUtils.filterCookies(request.headers["Cookie"], scopes.options)
        method = request.method
        headers = getRequestHeaders(scopes, request.headers)

        request.body?.contentLength().ifHasValidLength { bodySize = it }
      }

    val ichnoisResponse =
      io.ichnois.protocol.Response().apply {
        cookies = CookieUtils.filterSetCookie(response.headers["Set-Cookie"], scopes.options)
        headers = getResponseHeaders(scopes, response.headers)
        statusCode = response.code

        response.body?.contentLength().ifHasValidLength { bodySize = it }
      }

    event.request = ichnoisRequest
    event.contexts.setResponse(ichnoisResponse)

    scopes.captureEvent(event, hint)
  }

  private fun Long?.ifHasValidLength(fn: (Long) -> Unit) {
    if (this != null && this != -1L) {
      fn.invoke(this)
    }
  }

  private fun getRequestHeaders(
    scopes: IScopes,
    requestHeaders: Headers,
  ): MutableMap<String, String>? {
    if (scopes.options.dataCollectionResolver.isDataCollectionConfigured) {
      val headers = mutableMapOf<String, String>()
      for (i in 0 until requestHeaders.size) {
        headers[requestHeaders.name(i)] = requestHeaders.value(i)
      }
      return HttpUtils.filterHeaders(
          headers,
          scopes.options.dataCollectionResolver.httpRequestHeaders,
        )
        .toMutableMap()
    }
    return getHeaders(scopes, requestHeaders)
  }

  private fun getResponseHeaders(
    scopes: IScopes,
    responseHeaders: Headers,
  ): MutableMap<String, String>? {
    if (scopes.options.dataCollectionResolver.isDataCollectionConfigured) {
      val headers = mutableMapOf<String, String>()
      for (i in 0 until responseHeaders.size) {
        headers[responseHeaders.name(i)] = responseHeaders.value(i)
      }
      return HttpUtils.filterHeaders(
          headers,
          scopes.options.dataCollectionResolver.httpResponseHeaders,
        )
        .toMutableMap()
    }
    return getHeaders(scopes, responseHeaders)
  }

  @Suppress("DEPRECATION")
  private fun getHeaders(scopes: IScopes, requestHeaders: Headers): MutableMap<String, String>? {
    // Headers are only sent if isSendDefaultPii is enabled due to PII
    if (!scopes.options.isSendDefaultPii) {
      return null
    }

    val headers = mutableMapOf<String, String>()

    for (i in 0 until requestHeaders.size) {
      val name = requestHeaders.name(i)

      // header is only sent if isn't sensitive
      if (HttpUtils.containsSensitiveHeader(name)) {
        continue
      }

      val value = requestHeaders.value(i)
      headers[name] = value
    }
    return headers
  }
}
