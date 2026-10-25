package io.ichnois.spring.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.IScopes;
import io.ichnois.ISpan;
import io.ichnois.ScopesAdapter;
import io.ichnois.SpanOptions;
import io.ichnois.SpanStatus;
import io.ichnois.util.Objects;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.StringUtils;

/**
 * Creates a span from every bean method executed within {@link IchnoisCoreTransaction}. Depending on the
 * configured pointcut, method either must or can be annotated with {@link IchnoisCoreSpan}.
 */
@Open
public class IchnoisCoreSpanAdvice implements MethodInterceptor {
  private static final String TRACE_ORIGIN = "auto.function.spring.advice";
  private final @NotNull IScopes scopes;

  public IchnoisCoreSpanAdvice() {
    this(ScopesAdapter.getInstance());
  }

  public IchnoisCoreSpanAdvice(final @NotNull IScopes scopes) {
    this.scopes = Objects.requireNonNull(scopes, "scopes are required");
  }

  @SuppressWarnings("deprecation")
  @Override
  public Object invoke(final @NotNull MethodInvocation invocation) throws Throwable {
    final ISpan activeSpan = scopes.getSpan();

    if (activeSpan == null || activeSpan.isNoOp()) {
      // there is no active transaction, we do not start new span
      return invocation.proceed();
    } else {
      final Method mostSpecificMethod =
          AopUtils.getMostSpecificMethod(invocation.getMethod(), invocation.getThis().getClass());
      final Class<?> targetClass = invocation.getMethod().getDeclaringClass();
      IchnoisCoreSpan ichnoisSpan = AnnotationUtils.findAnnotation(mostSpecificMethod, IchnoisCoreSpan.class);
      if (ichnoisSpan == null) {
        ichnoisSpan =
            AnnotationUtils.findAnnotation(
                mostSpecificMethod.getDeclaringClass(), IchnoisCoreSpan.class);
      }
      final String operation = resolveSpanOperation(targetClass, mostSpecificMethod, ichnoisSpan);
      final @NotNull SpanOptions spanOptions = new SpanOptions();
      spanOptions.setOrigin(TRACE_ORIGIN);
      final ISpan span = activeSpan.startChild(operation, null, spanOptions);
      if (ichnoisSpan != null && !StringUtils.isEmpty(ichnoisSpan.description())) {
        span.setDescription(ichnoisSpan.description());
      }
      try {
        final Object result = invocation.proceed();
        span.setStatus(SpanStatus.OK);
        return result;
      } catch (Throwable e) {
        span.setStatus(SpanStatus.INTERNAL_ERROR);
        span.setThrowable(e);
        throw e;
      } finally {
        span.finish();
      }
    }
  }

  @SuppressWarnings("deprecation")
  private String resolveSpanOperation(
      Class<?> targetClass, Method method, @Nullable IchnoisCoreSpan ichnoisSpan) {
    return ichnoisSpan == null || StringUtils.isEmpty(ichnoisSpan.value())
        ? targetClass.getSimpleName() + "." + method.getName()
        : ichnoisSpan.value();
  }
}
