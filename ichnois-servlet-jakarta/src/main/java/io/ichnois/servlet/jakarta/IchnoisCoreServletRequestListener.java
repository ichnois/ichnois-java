package io.ichnois.servlet.jakarta;

import static io.ichnois.TypeCheckHint.SERVLET_REQUEST;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.Breadcrumb;
import io.ichnois.Hint;
import io.ichnois.IScopes;
import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.ScopesAdapter;
import io.ichnois.util.LifecycleHelper;
import io.ichnois.util.Objects;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;

/**
 * This request listener pushes a new scope into ichnois that enriches a IchnoisCore event with the
 * details about the current request upon sending.
 */
@Open
public class IchnoisCoreServletRequestListener implements ServletRequestListener {

  public static final String ICHNOIS_SCOPE_LIFECYCLE_TOKEN_KEY = "ichnois-scope-lifecycle";

  private final IScopes scopes;

  public IchnoisCoreServletRequestListener(@NotNull IScopes scopes) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
  }

  public IchnoisCoreServletRequestListener() {
    this(ScopesAdapter.getInstance());
  }

  @Override
  public void requestDestroyed(@NotNull ServletRequestEvent servletRequestEvent) {
    final ServletRequest servletRequest = servletRequestEvent.getServletRequest();
    LifecycleHelper.close(servletRequest.getAttribute(ICHNOIS_SCOPE_LIFECYCLE_TOKEN_KEY));
  }

  @Override
  public void requestInitialized(@NotNull ServletRequestEvent servletRequestEvent) {
    final @NotNull IIchnoisCoreLifecycleToken lifecycleToken =
        scopes.forkedScopes("IchnoisCoreServletRequestListener").makeCurrent();

    final ServletRequest servletRequest = servletRequestEvent.getServletRequest();
    servletRequest.setAttribute(ICHNOIS_SCOPE_LIFECYCLE_TOKEN_KEY, lifecycleToken);
    if (servletRequest instanceof HttpServletRequest) {
      final HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;

      final Hint hint = new Hint();
      hint.set(SERVLET_REQUEST, httpRequest);

      scopes.addBreadcrumb(
          Breadcrumb.http(httpRequest.getRequestURI(), httpRequest.getMethod()), hint);

      scopes.configureScope(
          scope -> {
            scope.addEventProcessor(
                new IchnoisCoreRequestHttpServletRequestProcessor(httpRequest, scopes.getOptions()));
          });
    }
  }
}
