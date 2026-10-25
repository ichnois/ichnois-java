package io.ichnois.spring.jakarta.tracing;

import static io.ichnois.TypeCheckHint.SPRING_REQUEST_INTERCEPTOR_REQUEST;
import static io.ichnois.TypeCheckHint.SPRING_REQUEST_INTERCEPTOR_REQUEST_BODY;
import static io.ichnois.TypeCheckHint.SPRING_REQUEST_INTERCEPTOR_RESPONSE;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.BaggageHeader;
import io.ichnois.Breadcrumb;
import io.ichnois.Hint;
import io.ichnois.IScopes;
import io.ichnois.ISpan;
import io.ichnois.SpanDataConvention;
import io.ichnois.SpanOptions;
import io.ichnois.SpanStatus;
import io.ichnois.W3CTraceparentHeader;
import io.ichnois.util.Objects;
import io.ichnois.util.SpanUtils;
import io.ichnois.util.TracingUtils;
import io.ichnois.util.UrlUtils;
import java.io.IOException;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

@Open
public class IchnoisCoreSpanClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {
  private static final String TRACE_ORIGIN_REST_TEMPLATE = "auto.http.spring_jakarta.resttemplate";
  private static final String TRACE_ORIGIN_REST_CLIENT = "auto.http.spring_jakarta.restclient";
  private final @NotNull IScopes scopes;
  private final @NotNull String traceOrigin;

  public IchnoisCoreSpanClientHttpRequestInterceptor(final @NotNull IScopes scopes) {
    this(scopes, true);
  }

  public IchnoisCoreSpanClientHttpRequestInterceptor(
      final @NotNull IScopes scopes, final boolean isRestTemplate) {
    this.scopes = Objects.requireNonNull(scopes, "Scopes are required");
    this.traceOrigin = isRestTemplate ? TRACE_ORIGIN_REST_TEMPLATE : TRACE_ORIGIN_REST_CLIENT;
  }

  @Override
  public @NotNull ClientHttpResponse intercept(
      @NotNull HttpRequest request,
      @NotNull byte[] body,
      @NotNull ClientHttpRequestExecution execution)
      throws IOException {
    Integer responseStatusCode = null;
    ClientHttpResponse response = null;
    try {
      final ISpan activeSpan = scopes.getSpan();
      if (activeSpan == null) {
        maybeAddTracingHeaders(request, null);
        return execution.execute(request, body);
      }
      final @NotNull SpanOptions spanOptions = new SpanOptions();
      spanOptions.setOrigin(traceOrigin);
      final ISpan span = activeSpan.startChild("http.client", null, spanOptions);
      final String methodName =
          request.getMethod() != null ? request.getMethod().name() : "unknown";
      final @NotNull UrlUtils.UrlDetails urlDetails =
          UrlUtils.parse(
              request.getURI().toString(), scopes.getOptions().getDataCollectionResolver());
      span.setDescription(methodName + " " + urlDetails.getUrlOrFallback());
      span.setData(SpanDataConvention.HTTP_METHOD_KEY, methodName.toUpperCase(Locale.ROOT));
      urlDetails.applyToSpan(span);

      maybeAddTracingHeaders(request, span);

      try {
        response = execution.execute(request, body);
        // handles both success and error responses
        span.setData(SpanDataConvention.HTTP_STATUS_CODE_KEY, response.getStatusCode().value());
        span.setStatus(SpanStatus.fromHttpStatusCode(response.getStatusCode().value()));
        responseStatusCode = response.getStatusCode().value();
        return response;
      } catch (Throwable e) {
        // handles cases like connection errors
        span.setThrowable(e);
        span.setStatus(SpanStatus.INTERNAL_ERROR);
        throw e;
      } finally {
        span.finish();
      }
    } finally {
      addBreadcrumb(request, body, responseStatusCode, response);
    }
  }

  private void maybeAddTracingHeaders(
      final @NotNull HttpRequest request, final @Nullable ISpan span) {
    if (isIgnored()) {
      return;
    }

    final @Nullable TracingUtils.TracingHeaders tracingHeaders =
        TracingUtils.traceIfAllowed(
            scopes,
            request.getURI().toString(),
            request.getHeaders().get(BaggageHeader.BAGGAGE_HEADER),
            span);

    if (tracingHeaders != null) {
      request
          .getHeaders()
          .add(
              tracingHeaders.getIchnoisCoreTraceHeader().getName(),
              tracingHeaders.getIchnoisCoreTraceHeader().getValue());

      final @Nullable BaggageHeader baggageHeader = tracingHeaders.getBaggageHeader();
      if (baggageHeader != null) {
        request.getHeaders().set(baggageHeader.getName(), baggageHeader.getValue());
      }

      final @Nullable W3CTraceparentHeader w3cTraceparentHeader =
          tracingHeaders.getW3cTraceparentHeader();
      if (w3cTraceparentHeader != null) {
        request.getHeaders().add(w3cTraceparentHeader.getName(), w3cTraceparentHeader.getValue());
      }
    }
  }

  private boolean isIgnored() {
    return SpanUtils.isIgnored(scopes.getOptions().getIgnoredSpanOrigins(), traceOrigin);
  }

  private void addBreadcrumb(
      final @NotNull HttpRequest request,
      final @NotNull byte[] body,
      final @Nullable Integer responseStatusCode,
      final @Nullable ClientHttpResponse response) {
    final String methodName = request.getMethod() != null ? request.getMethod().name() : "unknown";

    final Breadcrumb breadcrumb =
        Breadcrumb.http(
            request.getURI().toString(),
            methodName,
            responseStatusCode,
            scopes.getOptions().getDataCollectionResolver());
    breadcrumb.setData("request_body_size", body.length);

    final Hint hint = new Hint();
    hint.set(SPRING_REQUEST_INTERCEPTOR_REQUEST, request);
    hint.set(SPRING_REQUEST_INTERCEPTOR_REQUEST_BODY, body);
    if (response != null) {
      hint.set(SPRING_REQUEST_INTERCEPTOR_RESPONSE, response);
    }

    scopes.addBreadcrumb(breadcrumb, hint);
  }
}
