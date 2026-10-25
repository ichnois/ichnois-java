package io.ichnois.spring.jakarta;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.Hint;
import io.ichnois.ILogger;
import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCoreEvent;
import io.ichnois.internal.eventprocessor.IchnoisCoreEventProcessor;
import io.ichnois.spring.jakarta.tracing.TransactionNameProvider;
import io.ichnois.util.Objects;
import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Attaches transaction name from the HTTP request to {@link IchnoisCoreEvent}. */
@Open
public class IchnoisCoreRequestHttpServletRequestProcessor implements IchnoisCoreEventProcessor {
  private final @NotNull TransactionNameProvider transactionNameProvider;
  private final @NotNull HttpServletRequest request;
  private final @NotNull ILogger logger;

  public IchnoisCoreRequestHttpServletRequestProcessor(
      final @NotNull TransactionNameProvider transactionNameProvider,
      final @NotNull HttpServletRequest request) {
    this(transactionNameProvider, request, ScopesAdapter.getInstance().getOptions().getLogger());
  }

  IchnoisCoreRequestHttpServletRequestProcessor(
      final @NotNull TransactionNameProvider transactionNameProvider,
      final @NotNull HttpServletRequest request,
      final @NotNull ILogger logger) {
    this.transactionNameProvider =
        Objects.requireNonNull(transactionNameProvider, "transactionNameProvider is required");
    this.request = Objects.requireNonNull(request, "request is required");
    this.logger = Objects.requireNonNull(logger, "logger is required");
  }

  @Override
  public @NotNull IchnoisCoreEvent process(final @NotNull IchnoisCoreEvent event, final @NotNull Hint hint) {
    if (event.getTransaction() == null) {
      event.setTransaction(
          TransactionNameProviderUtils.provideTransactionName(
              transactionNameProvider, request, logger));
    }
    return event;
  }

  @Override
  public @Nullable Long getOrder() {
    return 5000L;
  }
}
