package io.ichnois.spring.jakarta;

import static io.ichnois.TransactionContext.DEFAULT_TRANSACTION_NAME;

import io.ichnois.ILogger;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.spring.jakarta.tracing.TransactionNameProvider;
import io.ichnois.spring.jakarta.tracing.TransactionNameWithSource;
import io.ichnois.util.ExceptionUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class TransactionNameProviderUtils {
  private TransactionNameProviderUtils() {}

  public static @Nullable String provideTransactionName(
      final @NotNull TransactionNameProvider provider,
      final @NotNull HttpServletRequest request,
      final @NotNull ILogger logger) {
    try {
      return provider.provideTransactionName(request);
    } catch (Throwable e) {
      ExceptionUtils.rethrowIfFatal(e);
      logCallbackError(logger, e);
      return DEFAULT_TRANSACTION_NAME;
    }
  }

  public static @NotNull TransactionNameWithSource provideTransactionNameAndSource(
      final @NotNull TransactionNameProvider provider,
      final @NotNull HttpServletRequest request,
      final @NotNull ILogger logger,
      final @NotNull TransactionNameWithSource fallbackOnError) {
    try {
      return provider.provideTransactionNameAndSource(request);
    } catch (Throwable e) {
      ExceptionUtils.rethrowIfFatal(e);
      logCallbackError(logger, e);
      return fallbackOnError;
    }
  }

  private static void logCallbackError(
      final @NotNull ILogger logger, final @NotNull Throwable throwable) {
    logger.log(
        IchnoisCoreLevel.ERROR, "The TransactionNameProvider callback threw an exception.", throwable);
  }
}
