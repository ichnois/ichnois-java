package io.ichnois.servlet.jakarta;

import io.ichnois.Hint;
import io.ichnois.IchnoisCoreEvent;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.internal.eventprocessor.IchnoisCoreEventProcessor;
import io.ichnois.protocol.Request;
import io.ichnois.util.HttpUtils;
import io.ichnois.util.Objects;
import io.ichnois.util.UrlUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Attaches information about HTTP request to {@link IchnoisCoreEvent}. */
final class IchnoisCoreRequestHttpServletRequestProcessor implements IchnoisCoreEventProcessor {

  private final @NotNull HttpServletRequest httpRequest;
  private final @NotNull IchnoisCoreOptions options;

  public IchnoisCoreRequestHttpServletRequestProcessor(
      @NotNull HttpServletRequest httpRequest, @NotNull IchnoisCoreOptions options) {
    this.httpRequest = Objects.requireNonNull(httpRequest, "httpRequest is required");
    this.options = Objects.requireNonNull(options, "options are required");
  }

  // httpRequest.getRequestURL() returns StringBuffer which is considered an obsolete class.
  @SuppressWarnings("JdkObsolete")
  @Override
  public @NotNull IchnoisCoreEvent process(@NotNull IchnoisCoreEvent event, @NotNull Hint hint) {
    final Request ichnoisRequest = new Request();
    ichnoisRequest.setMethod(httpRequest.getMethod());
    final @NotNull UrlUtils.UrlDetails urlDetails =
        UrlUtils.parse(httpRequest.getRequestURL().toString(), options.getDataCollectionResolver());
    urlDetails.applyToRequest(ichnoisRequest);
    ichnoisRequest.setQueryString(
        UrlUtils.filterQueryParams(
            httpRequest.getQueryString(), options.getDataCollectionResolver()));
    ichnoisRequest.setHeaders(resolveHeadersMap(httpRequest));

    event.setRequest(ichnoisRequest);
    return event;
  }

  private @NotNull Map<String, String> resolveHeadersMap(
      final @NotNull HttpServletRequest request) {
    final Map<String, String> headersMap = new HashMap<>();
    for (String headerName : Collections.list(request.getHeaderNames())) {
      if (options.getDataCollectionResolver().isDataCollectionConfigured()
          || !HttpUtils.containsSensitiveHeader(headerName.toUpperCase(Locale.ROOT))) {
        headersMap.put(headerName, toString(request.getHeaders(headerName)));
      }
    }
    if (options.getDataCollectionResolver().isDataCollectionConfigured()) {
      return HttpUtils.filterHeaders(
          headersMap, options.getDataCollectionResolver().getHttpRequestHeaders());
    }
    return headersMap;
  }

  private static @Nullable String toString(final @Nullable Enumeration<String> enumeration) {
    return enumeration != null ? String.join(",", Collections.list(enumeration)) : null;
  }

  @Override
  public @Nullable Long getOrder() {
    return 4000L;
  }
}
