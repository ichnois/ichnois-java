package io.ichnois.metrics;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.DataCategory;
import io.ichnois.IIchnoisCoreClient;
import io.ichnois.IIchnoisCoreExecutorService;
import io.ichnois.IchnoisCoreExecutorService;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreMetricsEvent;
import io.ichnois.IchnoisCoreMetricsEvents;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.clientreport.DiscardReason;
import io.ichnois.transport.ReusableCountLatch;
import io.ichnois.util.JsonSerializationUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Open
public class MetricsBatchProcessor implements IMetricsBatchProcessor {

  public static final int FLUSH_AFTER_MS = 5000;
  public static final int MAX_BATCH_SIZE = 1000;
  public static final int MAX_QUEUE_SIZE = 10000;

  protected final @NotNull IchnoisCoreOptions options;
  private final @NotNull IIchnoisCoreClient client;
  private final @NotNull Queue<IchnoisCoreMetricsEvent> queue;
  private final @NotNull IIchnoisCoreExecutorService executorService;
  private final @NotNull AtomicBoolean hasScheduled = new AtomicBoolean(false);
  protected volatile boolean hasAcceptedItem = false;
  private volatile boolean isShuttingDown = false;

  private final @NotNull ReusableCountLatch pendingCount = new ReusableCountLatch();

  public MetricsBatchProcessor(
      final @NotNull IchnoisCoreOptions options, final @NotNull IIchnoisCoreClient client) {
    this.options = options;
    this.client = client;
    this.queue = new ConcurrentLinkedQueue<>();
    this.executorService = new IchnoisCoreExecutorService(options);
  }

  @Override
  public void add(final @NotNull IchnoisCoreMetricsEvent metricsEvent) {
    if (isShuttingDown) {
      return;
    }
    if (pendingCount.getCount() >= MAX_QUEUE_SIZE) {
      options
          .getClientReportRecorder()
          .recordLostEvent(DiscardReason.QUEUE_OVERFLOW, DataCategory.TraceMetric);
      final long lostBytes =
          JsonSerializationUtils.byteSizeOf(
              options.getSerializer(), options.getLogger(), metricsEvent);
      options
          .getClientReportRecorder()
          .recordLostEvent(DiscardReason.QUEUE_OVERFLOW, DataCategory.TraceMetricByte, lostBytes);
      return;
    }
    pendingCount.increment();
    queue.offer(metricsEvent);
    hasAcceptedItem = true;
    maybeSchedule(false);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Override
  public void close(final boolean isRestarting) {
    isShuttingDown = true;
    if (isRestarting && hasAcceptedItem) {
      maybeSchedule(true);
      executorService.submit(() -> executorService.close(options.getShutdownTimeoutMillis()));
      return;
    }
    // On restart, reaching this path means the executor never had anything scheduled, so
    // synchronous shutdown should be immediate.
    executorService.close(options.getShutdownTimeoutMillis());
    while (!queue.isEmpty()) {
      flushBatch();
    }
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void maybeSchedule(boolean immediately) {
    if (immediately) {
      // any already scheduled task may be far in the future, we want to schedule something that
      // runs right away
      hasScheduled.set(true);
    } else if (!hasScheduled.compareAndSet(false, true)) {
      // was already true, no need to schedule again
      return;
    }
    final int flushAfterMs = immediately ? 0 : FLUSH_AFTER_MS;
    try {
      executorService.schedule(new BatchRunnable(), flushAfterMs);
    } catch (RejectedExecutionException e) {
      hasScheduled.set(false);
      options
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "Metrics batch processor flush task rejected", e);
    }
  }

  @Override
  public void flush(long timeoutMillis) {
    if (!hasAcceptedItem) {
      return;
    }
    maybeSchedule(true);
    try {
      pendingCount.waitTillZero(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, "Failed to flush metrics events", e);
      Thread.currentThread().interrupt();
    }
  }

  private void flush() {
    flushInternal();
    hasScheduled.set(false);
    if (!queue.isEmpty()) {
      maybeSchedule(false);
    }
  }

  private void flushInternal() {
    do {
      flushBatch();
    } while (queue.size() >= MAX_BATCH_SIZE);
  }

  private void flushBatch() {
    final @NotNull List<IchnoisCoreMetricsEvent> metricsEvents = new ArrayList<>(MAX_BATCH_SIZE);
    do {
      final @Nullable IchnoisCoreMetricsEvent metricsEvent = queue.poll();
      if (metricsEvent != null) {
        metricsEvents.add(metricsEvent);
      }
    } while (!queue.isEmpty() && metricsEvents.size() < MAX_BATCH_SIZE);

    if (!metricsEvents.isEmpty()) {
      client.captureBatchedMetricsEvents(new IchnoisCoreMetricsEvents(metricsEvents));
      for (int i = 0; i < metricsEvents.size(); i++) {
        pendingCount.decrement();
      }
    }
  }

  private class BatchRunnable implements Runnable {

    @Override
    public void run() {
      flush();
    }
  }
}
