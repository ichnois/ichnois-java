package io.ichnois.spring.exception;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.IScopes;
import io.ichnois.ScopesAdapter;
import io.ichnois.exception.ExceptionMechanismException;
import io.ichnois.protocol.Mechanism;
import io.ichnois.util.Objects;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;

/**
 * Captures an exception passed to a bean method annotated with {@link
 * IchnoisCoreCaptureExceptionParameter}.
 */
@ApiStatus.Internal
@Open
public class IchnoisCoreCaptureExceptionParameterAdvice implements MethodInterceptor {
  private static final String MECHANISM_TYPE = "IchnoisCoreSpring5CaptureExceptionParameterAdvice";
  private final @NotNull IScopes scopes;

  public IchnoisCoreCaptureExceptionParameterAdvice() {
    this(ScopesAdapter.getInstance());
  }

  public IchnoisCoreCaptureExceptionParameterAdvice(final @NotNull IScopes scopes) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
  }

  @Override
  public Object invoke(final @NotNull MethodInvocation invocation) throws Throwable {
    final Method mostSpecificMethod =
        AopUtils.getMostSpecificMethod(invocation.getMethod(), invocation.getThis().getClass());
    IchnoisCoreCaptureExceptionParameter ichnoisCaptureExceptionParameter =
        AnnotationUtils.findAnnotation(mostSpecificMethod, IchnoisCoreCaptureExceptionParameter.class);

    if (ichnoisCaptureExceptionParameter != null) {
      Object[] args = invocation.getArguments();
      for (Object arg : args) {
        if (arg instanceof Exception) {
          captureException((Exception) arg);
          break;
        }
      }
    }

    return invocation.proceed();
  }

  private void captureException(final @NotNull Throwable throwable) {
    final Mechanism mechanism = new Mechanism();
    mechanism.setType(MECHANISM_TYPE);
    mechanism.setHandled(true);
    final Throwable mechanismException =
        new ExceptionMechanismException(mechanism, throwable, Thread.currentThread());
    scopes.captureException(mechanismException);
  }
}
