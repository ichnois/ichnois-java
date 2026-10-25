package io.ichnois.transport.apache;

import static io.ichnois.IchnoisCoreLevel.*;

import io.ichnois.DateUtils;
import io.ichnois.Hint;
import io.ichnois.RequestDetails;
import io.ichnois.IchnoisCoreDate;
import io.ichnois.IchnoisCoreEnvelope;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.clientreport.DiscardReason;
import io.ichnois.hints.Retryable;
import io.ichnois.transport.ITransport;
import io.ichnois.transport.RateLimiter;
import io.ichnois.transport.ReusableCountLatch;
import io.ichnois.util.HintUtils;
import io.ichnois.util.Objects;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequests;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.TimeValue;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ITransport} implementation that executes request asynchronously in a non-blocking manner
 * using Apache Http Client 5.
 */
public final class ApacheHttpClientTransport implements ITransport {
  private final @NotNull IchnoisCoreOptions options;
  private final @NotNull RequestDetails requestDetails;
  private final @NotNull CloseableHttpAsyncClient httpclient;
  private final @NotNull RateLimiter rateLimiter;
  private final @NotNull ReusableCountLatch currentlyRunning;

  public ApacheHttpClientTransport(
      final @NotNull IchnoisCoreOptions options,
      final @NotNull RequestDetails requestDetails,
      final @NotNull CloseableHttpAsyncClient httpclient,
      final @NotNull RateLimiter rateLimiter) {
    this(options, requestDetails, httpclient, rateLimiter, new ReusableCountLatch());
  }

  ApacheHttpClientTransport(
      final @NotNull IchnoisCoreOptions options,
      final @NotNull RequestDetails requestDetails,
      final @NotNull CloseableHttpAsyncClient httpclient,
      final @NotNull RateLimiter rateLimiter,
      final @NotNull ReusableCountLatch currentlyRunning) {
    this.options = Objects.requireNonNull(options, "options is required");
    this.requestDetails = Objects.requireNonNull(requestDetails, "requestDetails is required");
    this.httpclient = Objects.requireNonNull(httpclient, "httpclient is required");
    this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter is required");
    this.currentlyRunning =
        Objects.requireNonNull(currentlyRunning, "currentlyRunning is required");
    this.httpclient.start();
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public void send(final @NotNull IchnoisCoreEnvelope envelope, final @NotNull Hint hint)
      throws IOException {
    if (isSchedulingAllowed()) {
      final IchnoisCoreEnvelope filteredEnvelope = rateLimiter.filter(envelope, hint);

      if (filteredEnvelope != null) {
        final IchnoisCoreEnvelope envelopeWithClientReport =
            options.getClientReportRecorder().attachReportToEnvelope(filteredEnvelope);

        if (envelopeWithClientReport != null) {

          @NotNull IchnoisCoreDate now = options.getDateProvider().now();
          envelopeWithClientReport
              .getHeader()
              .setSentAt(DateUtils.nanosToDate(now.nanoTimestamp()));

          currentlyRunning.increment();

          try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
              final GZIPOutputStream gzip = new GZIPOutputStream(outputStream)) {
            options.getSerializer().serialize(envelopeWithClientReport, gzip);

            final SimpleHttpRequest request =
                SimpleHttpRequests.post(requestDetails.getUrl().toString());
            request.setBody(
                outputStream.toByteArray(), ContentType.create("application/x-sentry-envelope"));
            request.setHeader("Content-Encoding", "gzip");
            request.setHeader("Accept", "application/json");

            for (Map.Entry<String, String> header : requestDetails.getHeaders().entrySet()) {
              request.setHeader(header.getKey(), header.getValue());
            }

            if (options.getLogger().isEnabled(DEBUG)) {
              options
                  .getLogger()
                  .log(DEBUG, "Currently running %d requests", currentlyRunning.getCount());
            }

            httpclient.execute(
                request,
                new FutureCallback<SimpleHttpResponse>() {
                  @Override
                  public void completed(SimpleHttpResponse response) {
                    if (response.getCode() != 200) {
                      if (response.getCode() == 413) {
                        options
                            .getLogger()
                            .log(
                                ERROR,
                                "Envelope was discarded by the server because it was too large."
                                    + " Consider reducing the size of events, breadcrumbs,"
                                    + " or attachments."
                                    + " You can use the `IchnoisCoreOptions.onOversizedEvent`"
                                    + " callback to customize how oversized events"
                                    + " are handled.");
                      } else {
                        options
                            .getLogger()
                            .log(ERROR, "Request failed, API returned %s", response.getCode());
                      }

                      if (response.getCode() >= 400 && response.getCode() != 429) {
                        if (!HintUtils.hasType(hint, Retryable.class)) {
                          options
                              .getClientReportRecorder()
                              .recordLostEnvelope(
                                  DiscardReason.SEND_ERROR, envelopeWithClientReport);
                        }
                      }
                    } else {
                      options.getLogger().log(INFO, "Envelope sent successfully.");
                    }
                    final Header retryAfter = response.getFirstHeader("Retry-After");
                    final Header rateLimits = response.getFirstHeader("X-IchnoisCore-Rate-Limits");
                    rateLimiter.updateRetryAfterLimits(
                        rateLimits != null ? rateLimits.getValue() : null,
                        retryAfter != null ? retryAfter.getValue() : null,
                        response.getCode());
                    currentlyRunning.decrement();
                  }

                  @Override
                  public void failed(Exception ex) {
                    options.getLogger().log(ERROR, "Error while sending an envelope", ex);
                    if (!HintUtils.hasType(hint, Retryable.class)) {
                      options
                          .getClientReportRecorder()
                          .recordLostEnvelope(
                              DiscardReason.NETWORK_ERROR, envelopeWithClientReport);
                    }
                    currentlyRunning.decrement();
                  }

                  @Override
                  public void cancelled() {
                    options.getLogger().log(WARNING, "Request cancelled");
                    if (!HintUtils.hasType(hint, Retryable.class)) {
                      options
                          .getClientReportRecorder()
                          .recordLostEnvelope(
                              DiscardReason.NETWORK_ERROR, envelopeWithClientReport);
                    }
                    currentlyRunning.decrement();
                  }
                });
          } catch (Throwable e) {
            options.getLogger().log(ERROR, "Error when sending envelope", e);
            if (!HintUtils.hasType(hint, Retryable.class)) {
              options
                  .getClientReportRecorder()
                  .recordLostEnvelope(DiscardReason.NETWORK_ERROR, envelopeWithClientReport);
            }
          }
        }
      }
    } else {
      options.getLogger().log(IchnoisCoreLevel.WARNING, "Submit cancelled");
      options.getClientReportRecorder().recordLostEnvelope(DiscardReason.QUEUE_OVERFLOW, envelope);
    }
  }

  @Override
  public void flush(long timeoutMillis) {
    try {
      if (!currentlyRunning.waitTillZero(timeoutMillis, TimeUnit.MILLISECONDS)) {
        options.getLogger().log(WARNING, "Failed to flush all events within %s ms", timeoutMillis);
      }
    } catch (InterruptedException e) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, "Failed to flush events", e);
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public @NotNull RateLimiter getRateLimiter() {
    return rateLimiter;
  }

  @Override
  public void close() throws IOException {
    close(false);
  }

  @Override
  public void close(final boolean isRestarting) throws IOException {
    options.getLogger().log(DEBUG, "Shutting down");
    try {
      httpclient.awaitShutdown(TimeValue.ofSeconds(isRestarting ? 0 : 1));
    } catch (InterruptedException e) {
      options.getLogger().log(DEBUG, "Thread interrupted while closing the connection.");
      Thread.currentThread().interrupt();
    } finally {
      httpclient.close(CloseMode.GRACEFUL);
    }
  }

  private boolean isSchedulingAllowed() {
    return currentlyRunning.getCount() < options.getMaxQueueSize();
  }
}
