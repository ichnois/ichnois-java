package io.ichnois.spring.jakarta.webflux;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.IScopes;
import io.ichnois.protocol.Request;
import io.ichnois.util.CookieUtils;
import io.ichnois.util.HttpUtils;
import io.ichnois.util.Objects;
import io.ichnois.util.UrlUtils;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

@Open
@SuppressWarnings("deprecation")
@ApiStatus.Experimental
public class IchnoisCoreRequestResolver {
  private final @NotNull IScopes scopes;

  public IchnoisCoreRequestResolver(final @NotNull IScopes scopes) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
  }

  public @NotNull Request resolveIchnoisCoreRequest(final @NotNull ServerHttpRequest httpRequest) {
    final Request ichnoisRequest = new Request();
    final String methodName =
        httpRequest.getMethod() != null ? httpRequest.getMethod().name() : "unknown";
    ichnoisRequest.setMethod(methodName);
    final @NotNull URI uri = httpRequest.getURI();
    final @NotNull UrlUtils.UrlDetails urlDetails =
        UrlUtils.parse(uri.toString(), scopes.getOptions().getDataCollectionResolver());
    urlDetails.applyToRequest(ichnoisRequest);
    ichnoisRequest.setHeaders(resolveHeadersMap(httpRequest.getHeaders()));

    final @NotNull String headerName = CookieUtils.COOKIE_HEADER_NAME;
    if (scopes.getOptions().getDataCollectionResolver().isDataCollectionConfigured()) {
      ichnoisRequest.setCookies(
          toString(
              CookieUtils.filterCookiesFromHeader(
                  httpRequest.getHeaders().get(headerName),
                  scopes.getOptions().getDataCollectionResolver().getCookies(),
                  Collections.emptyList())));
    } else if (scopes.getOptions().isSendDefaultPii()) {
      ichnoisRequest.setCookies(
          toString(
              CookieUtils.filterOutSecurityCookiesFromHeader(
                  httpRequest.getHeaders().get(headerName), headerName, Collections.emptyList())));
    }
    return ichnoisRequest;
  }

  @NotNull
  Map<String, String> resolveHeadersMap(final HttpHeaders request) {
    final Map<String, String> headersMap = new HashMap<>();
    for (Map.Entry<String, List<String>> entry : request.entrySet()) {
      String headerName = entry.getKey();
      if (scopes.getOptions().getDataCollectionResolver().isDataCollectionConfigured()
          || scopes.getOptions().isSendDefaultPii()
          || !HttpUtils.containsSensitiveHeader(headerName)) {
        headersMap.put(
            headerName,
            toString(
                CookieUtils.filterOutSecurityCookiesFromHeader(
                    entry.getValue(), headerName, Collections.emptyList())));
      }
    }
    if (scopes.getOptions().getDataCollectionResolver().isDataCollectionConfigured()) {
      return HttpUtils.filterHeaders(
          headersMap, scopes.getOptions().getDataCollectionResolver().getHttpRequestHeaders());
    }
    return headersMap;
  }

  private static @Nullable String toString(final @Nullable List<String> enumeration) {
    return enumeration != null ? String.join(",", enumeration) : null;
  }
}
