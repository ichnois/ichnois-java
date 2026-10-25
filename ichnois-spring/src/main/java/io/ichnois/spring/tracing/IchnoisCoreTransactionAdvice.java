package io.ichnois.spring.tracing;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.IScopes;
import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.ITransaction;
import io.ichnois.ScopesAdapter;
import io.ichnois.SpanStatus;
import io.ichnois.TransactionContext;
import io.ichnois.TransactionOptions;
import io.ichnois.protocol.TransactionNameSource;
import io.ichnois.util.Objects;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.StringUtils;

/**
 * Reports execution of every bean method annotated with {@link IchnoisCoreTransaction} or a execution of
 * a bean method within a class annotated with {@link IchnoisCoreTransaction}.
 */
@ApiStatus.Internal
@Open
public class IchnoisCoreTransactionAdvice implements MethodInterceptor {
  private static final String TRACE_ORIGIN = "auto.function.spring.advice";
  private final @NotNull IScopes scopesBeforeForking;

  public IchnoisCoreTransactionAdvice() {
    this(ScopesAdapter.getInstance());
  }

  public IchnoisCoreTransactionAdvice(final @NotNull IScopes scopes) {
    this.scopesBeforeForking = Objects.requireNonNull(scopes, "scopes are required");
  }

  @SuppressWarnings("deprecation")
  @Override
  public Object invoke(final @NotNull MethodInvocation invocation) throws Throwable {
    final Method mostSpecificMethod =
        AopUtils.getMostSpecificMethod(invocation.getMethod(), invocation.getThis().getClass());

    @Nullable
    IchnoisCoreTransaction ichnoisTransaction =
        AnnotationUtils.findAnnotation(mostSpecificMethod, IchnoisCoreTransaction.class);
    if (ichnoisTransaction == null) {
      ichnoisTransaction =
          AnnotationUtils.findAnnotation(
              mostSpecificMethod.getDeclaringClass(), IchnoisCoreTransaction.class);
    }

    final TransactionNameAndSource nameAndSource =
        resolveTransactionName(invocation, ichnoisTransaction);

    final boolean isTransactionActive = isTransactionActive(scopesBeforeForking);

    if (isTransactionActive) {
      // transaction is already active, we do not start new transaction
      return invocation.proceed();
    } else {
      String operation;
      if (ichnoisTransaction != null && !StringUtils.isEmpty(ichnoisTransaction.operation())) {
        operation = ichnoisTransaction.operation();
      } else {
        operation = "bean";
      }
      final @NotNull IScopes forkedScopes =
          scopesBeforeForking.forkedScopes("IchnoisCoreTransactionAdvice");
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = forkedScopes.makeCurrent()) {
        final TransactionOptions transactionOptions = new TransactionOptions();
        transactionOptions.setBindToScope(true);
        transactionOptions.setOrigin(TRACE_ORIGIN);
        final ITransaction transaction =
            forkedScopes.startTransaction(
                new TransactionContext(nameAndSource.name, nameAndSource.source, operation),
                transactionOptions);
        try {
          final Object result = invocation.proceed();
          transaction.setStatus(SpanStatus.OK);
          return result;
        } catch (Throwable e) {
          transaction.setStatus(SpanStatus.INTERNAL_ERROR);
          transaction.setThrowable(e);
          throw e;
        } finally {
          transaction.finish();
        }
      }
    }
  }

  @SuppressWarnings("deprecation")
  private @NotNull TransactionNameAndSource resolveTransactionName(
      MethodInvocation invocation, @Nullable IchnoisCoreTransaction ichnoisTransaction) {
    if (ichnoisTransaction == null || StringUtils.isEmpty(ichnoisTransaction.value())) {
      final String name =
          invocation.getMethod().getDeclaringClass().getSimpleName()
              + "."
              + invocation.getMethod().getName();
      return new TransactionNameAndSource(name, TransactionNameSource.COMPONENT);
    } else {
      return new TransactionNameAndSource(ichnoisTransaction.value(), TransactionNameSource.CUSTOM);
    }
  }

  private boolean isTransactionActive(final @NotNull IScopes scopes) {
    return scopes.getSpan() != null;
  }

  private static class TransactionNameAndSource {
    private final @NotNull String name;
    private final @NotNull TransactionNameSource source;

    TransactionNameAndSource(
        final @NotNull String name, final @NotNull TransactionNameSource source) {
      this.name = name;
      this.source = source;
    }
  }
}
