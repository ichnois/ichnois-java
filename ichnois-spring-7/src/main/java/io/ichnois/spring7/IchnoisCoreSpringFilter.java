package io.ichnois.spring7;

import static io.ichnois.IchnoisCoreOptions.RequestSize.*;
import static io.ichnois.TypeCheckHint.SPRING_REQUEST_FILTER_REQUEST;
import static io.ichnois.TypeCheckHint.SPRING_REQUEST_FILTER_RESPONSE;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.Breadcrumb;
import io.ichnois.Hint;
import io.ichnois.IScopes;
import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCoreEvent;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.IchnoisCoreOptions.RequestSize;
import io.ichnois.internal.eventprocessor.IchnoisCoreEventProcessor;
import io.ichnois.spring7.tracing.SpringMvcTransactionNameProvider;
import io.ichnois.spring7.tracing.TransactionNameProvider;
import io.ichnois.util.Objects;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.util.InvalidMimeTypeException;
import org.springframework.util.MimeType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

@Open
public class IchnoisCoreSpringFilter extends OncePerRequestFilter {
  private final @NotNull IScopes scopesBeforeForking;
  private final @NotNull IchnoisCoreRequestResolver requestResolver;
  private final @NotNull TransactionNameProvider transactionNameProvider;

  public IchnoisCoreSpringFilter(
      final @NotNull IScopes scopes,
      final @NotNull IchnoisCoreRequestResolver requestResolver,
      final @NotNull TransactionNameProvider transactionNameProvider) {
    this.scopesBeforeForking = Objects.requireNonNull(scopes, "scopes are required");
    this.requestResolver = Objects.requireNonNull(requestResolver, "requestResolver is required");
    this.transactionNameProvider =
        Objects.requireNonNull(transactionNameProvider, "transactionNameProvider is required");
  }

  public IchnoisCoreSpringFilter(final @NotNull IScopes scopes) {
    this(scopes, new IchnoisCoreRequestResolver(scopes), new SpringMvcTransactionNameProvider());
  }

  public IchnoisCoreSpringFilter() {
    this(ScopesAdapter.getInstance());
  }

  @Override
  protected void doFilterInternal(
      final @NotNull HttpServletRequest servletRequest,
      final @NotNull HttpServletResponse response,
      final @NotNull FilterChain filterChain)
      throws ServletException, IOException {
    if (scopesBeforeForking.isEnabled()) {
      // request may qualify for caching request body, if so resolve cached request
      final HttpServletRequest request =
          resolveHttpServletRequest(scopesBeforeForking, servletRequest);
      final @NotNull IScopes forkedScopes = scopesBeforeForking.forkedScopes("IchnoisCoreSpringFilter");
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = forkedScopes.makeCurrent()) {
        final Hint hint = new Hint();
        hint.set(SPRING_REQUEST_FILTER_REQUEST, servletRequest);
        hint.set(SPRING_REQUEST_FILTER_RESPONSE, response);

        forkedScopes.addBreadcrumb(
            Breadcrumb.http(request.getRequestURI(), request.getMethod()), hint);
        configureScope(forkedScopes, request);
        filterChain.doFilter(request, response);
      }
    } else {
      filterChain.doFilter(servletRequest, response);
    }
  }

  private void configureScope(
      final @NotNull IScopes scopes, final @NotNull HttpServletRequest request) {
    try {
      scopes.configureScope(
          scope -> {
            // set basic request information on the scope
            scope.setRequest(requestResolver.resolveIchnoisCoreRequest(request));
            // transaction name is known at the later stage of request processing, thus it cannot
            // be set on the scope
            scope.addEventProcessor(
                new IchnoisCoreRequestHttpServletRequestProcessor(
                    transactionNameProvider, request, scopes.getOptions().getLogger()));
            // only if request caches body, add an event processor that sets body on the event
            // body is not on the scope, to avoid using memory when no event is triggered during
            // request processing
            if (request instanceof ContentCachingRequestWrapper) {
              scope.addEventProcessor(
                  new RequestBodyExtractingEventProcessor(request, scopes.getOptions()));
            }
          });
    } catch (Throwable e) {
      scopes
          .getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.ERROR, "Failed to set scope for HTTP request", e);
    }
  }

  private @NotNull HttpServletRequest resolveHttpServletRequest(
      final @NotNull IScopes scopes, final @NotNull HttpServletRequest request) {
    if (scopes.getOptions().getDataCollectionResolver().isIncomingRequestBody()
        && qualifiesForCaching(request, scopes.getOptions().getMaxRequestBodySize())) {
      return new ContentCachingRequestWrapper(request, 0);
    }
    return request;
  }

  private static boolean qualifiesForCaching(
      final @NotNull HttpServletRequest request, final @NotNull RequestSize maxRequestBodySize) {
    final int contentLength = request.getContentLength();
    final String contentType = request.getContentType();

    return maxRequestBodySize != RequestSize.NONE
        && contentLength != -1
        && contentType != null
        && shouldCacheMimeType(contentType)
        && ((maxRequestBodySize == SMALL && contentLength < 1000)
            || (maxRequestBodySize == MEDIUM && contentLength < 10000)
            || maxRequestBodySize == ALWAYS);
  }

  private static boolean shouldCacheMimeType(String contentType) {
    try {
      return MimeType.valueOf(contentType).isCompatibleWith(MediaType.APPLICATION_JSON)
          || MimeType.valueOf(contentType).isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED);
    } catch (InvalidMimeTypeException e) {
      return false;
    }
  }

  static final class RequestBodyExtractingEventProcessor implements IchnoisCoreEventProcessor {
    private final @NotNull RequestPayloadExtractor requestPayloadExtractor =
        new RequestPayloadExtractor();
    private final @NotNull HttpServletRequest request;
    private final @NotNull IchnoisCoreOptions options;

    public RequestBodyExtractingEventProcessor(
        final @NotNull HttpServletRequest request, final @NotNull IchnoisCoreOptions options) {
      this.request = Objects.requireNonNull(request, "request is required");
      this.options = Objects.requireNonNull(options, "options is required");
    }

    @Override
    public @NotNull IchnoisCoreEvent process(@NotNull IchnoisCoreEvent event, @NotNull Hint hint) {
      if (event.getRequest() != null
          && options.getDataCollectionResolver().isIncomingRequestBody()
          && qualifiesForCaching(request, options.getMaxRequestBodySize())) {
        event.getRequest().setData(requestPayloadExtractor.extract(request, options));
      }
      return event;
    }

    @Override
    public @Nullable Long getOrder() {
      return 3000L;
    }
  }
}
