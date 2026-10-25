package io.ichnois.spring7;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.IScopes;
import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.protocol.Request;
import io.ichnois.util.AutoClosableReentrantLock;
import io.ichnois.util.CookieUtils;
import io.ichnois.util.HttpUtils;
import io.ichnois.util.Objects;
import io.ichnois.util.UrlUtils;
import jakarta.servlet.ServletContext;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Open
@SuppressWarnings("deprecation")
public class IchnoisCoreRequestResolver {
  protected static final @NotNull AutoClosableReentrantLock staticLock =
      new AutoClosableReentrantLock();
  private final @NotNull IScopes scopes;
  private volatile @Nullable List<String> extraSecurityCookies;

  public IchnoisCoreRequestResolver(final @NotNull IScopes scopes) {
    this.scopes = Objects.requireNonNull(scopes, "options is required");
  }

  // httpRequest.getRequestURL() returns StringBuffer which is considered an obsolete class.
  @SuppressWarnings("JdkObsolete")
  public @NotNull Request resolveIchnoisCoreRequest(final @NotNull HttpServletRequest httpRequest) {
    final Request ichnoisRequest = new Request();
    ichnoisRequest.setMethod(httpRequest.getMethod());
    final @NotNull UrlUtils.UrlDetails urlDetails =
        UrlUtils.parse(
            httpRequest.getRequestURL().toString(),
            scopes.getOptions().getDataCollectionResolver());
    urlDetails.applyToRequest(ichnoisRequest);
    ichnoisRequest.setQueryString(
        UrlUtils.filterQueryParams(
            httpRequest.getQueryString(), scopes.getOptions().getDataCollectionResolver()));
    final @NotNull List<String> additionalSecurityCookieNames =
        extractSecurityCookieNamesOrUseCached(httpRequest);
    ichnoisRequest.setHeaders(resolveHeadersMap(httpRequest, additionalSecurityCookieNames));

    final @NotNull String cookieName = CookieUtils.COOKIE_HEADER_NAME;
    if (scopes.getOptions().getDataCollectionResolver().isDataCollectionConfigured()) {
      ichnoisRequest.setCookies(
          toString(
              CookieUtils.filterCookiesFromHeader(
                  httpRequest.getHeaders(cookieName),
                  scopes.getOptions().getDataCollectionResolver().getCookies(),
                  additionalSecurityCookieNames)));
    } else if (scopes.getOptions().isSendDefaultPii()) {
      ichnoisRequest.setCookies(
          toString(
              CookieUtils.filterOutSecurityCookiesFromHeader(
                  httpRequest.getHeaders(cookieName), cookieName, additionalSecurityCookieNames)));
    }
    return ichnoisRequest;
  }

  @NotNull
  Map<String, String> resolveHeadersMap(
      final @NotNull HttpServletRequest request,
      final @NotNull List<String> additionalSecurityCookieNames) {
    final Map<String, String> headersMap = new HashMap<>();
    for (String headerName : Collections.list(request.getHeaderNames())) {
      if (scopes.getOptions().getDataCollectionResolver().isDataCollectionConfigured()
          || scopes.getOptions().isSendDefaultPii()
          || !HttpUtils.containsSensitiveHeader(headerName)) {
        final @Nullable List<String> filteredHeaders =
            CookieUtils.filterOutSecurityCookiesFromHeader(
                request.getHeaders(headerName), headerName, additionalSecurityCookieNames);
        headersMap.put(headerName, toString(filteredHeaders));
      }
    }
    if (scopes.getOptions().getDataCollectionResolver().isDataCollectionConfigured()) {
      return HttpUtils.filterHeaders(
          headersMap, scopes.getOptions().getDataCollectionResolver().getHttpRequestHeaders());
    }
    return headersMap;
  }

  private List<String> extractSecurityCookieNamesOrUseCached(
      final @NotNull HttpServletRequest httpRequest) {
    if (extraSecurityCookies == null) {
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = staticLock.acquire()) {
        if (extraSecurityCookies == null) {
          extraSecurityCookies = extractSecurityCookieNames(httpRequest);
        }
      }
    }

    return extraSecurityCookies;
  }

  private List<String> extractSecurityCookieNames(final @NotNull HttpServletRequest httpRequest) {
    try {
      final @Nullable ServletContext servletContext = httpRequest.getServletContext();
      if (servletContext != null) {
        final @Nullable SessionCookieConfig sessionCookieConfig =
            servletContext.getSessionCookieConfig();
        if (sessionCookieConfig != null) {
          final @Nullable String cookieName = sessionCookieConfig.getName();
          if (cookieName != null) {
            return Arrays.asList(cookieName);
          }
        }
      }
    } catch (Throwable t) {
      scopes
          .getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "Failed to extract session cookie name from request.", t);
    }

    return Collections.emptyList();
  }

  private static @Nullable String toString(final @Nullable List<String> list) {
    return list != null ? String.join(",", list) : null;
  }
}
