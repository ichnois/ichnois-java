package io.ichnois.clientreport;

import io.ichnois.DataCategory;
import io.ichnois.DateUtils;
import io.ichnois.IchnoisCoreEnvelope;
import io.ichnois.IchnoisCoreEnvelopeItem;
import io.ichnois.IchnoisCoreItemType;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.protocol.IchnoisCoreSpan;
import io.ichnois.protocol.IchnoisCoreTransaction;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ClientReportRecorder implements IClientReportRecorder {

  private final @NotNull IClientReportStorage storage;
  private final @NotNull IchnoisCoreOptions options;

  public ClientReportRecorder(@NotNull IchnoisCoreOptions options) {
    this.options = options;
    this.storage = new AtomicClientReportStorage();
  }

  @Override
  public @NotNull IchnoisCoreEnvelope attachReportToEnvelope(@NotNull IchnoisCoreEnvelope envelope) {
    @Nullable ClientReport clientReport = resetCountsAndGenerateClientReport();
    if (clientReport == null) {
      return envelope;
    }

    try {
      options.getLogger().log(IchnoisCoreLevel.DEBUG, "Attaching client report to envelope.");

      final List<IchnoisCoreEnvelopeItem> items = new ArrayList<>();

      for (final IchnoisCoreEnvelopeItem item : envelope.getItems()) {
        items.add(item);
      }

      items.add(IchnoisCoreEnvelopeItem.fromClientReport(options.getSerializer(), clientReport));

      return new IchnoisCoreEnvelope(envelope.getHeader(), items);
    } catch (Throwable e) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, e, "Unable to attach client report to envelope.");
      return envelope;
    }
  }

  @Override
  public void recordLostEnvelope(@NotNull DiscardReason reason, @Nullable IchnoisCoreEnvelope envelope) {
    if (envelope == null) {
      return;
    }

    try {
      for (final IchnoisCoreEnvelopeItem item : envelope.getItems()) {
        recordLostEnvelopeItem(reason, item);
      }
    } catch (Throwable e) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, e, "Unable to record lost envelope.");
    }
  }

  @Override
  public void recordLostEnvelopeItem(
      @NotNull DiscardReason reason, @Nullable IchnoisCoreEnvelopeItem envelopeItem) {
    if (envelopeItem == null) {
      return;
    }

    try {
      final @NotNull IchnoisCoreItemType itemType = envelopeItem.getHeader().getType();
      if (IchnoisCoreItemType.ClientReport.equals(itemType)) {
        try {
          final ClientReport clientReport = envelopeItem.getClientReport(options.getSerializer());
          restoreCountsFromClientReport(clientReport);
        } catch (Exception e) {
          options
              .getLogger()
              .log(IchnoisCoreLevel.ERROR, "Unable to restore counts from previous client report.");
        }
      } else {
        final @NotNull DataCategory itemCategory = categoryFromItemType(itemType);
        if (itemCategory.equals(DataCategory.Transaction)) {
          final @Nullable IchnoisCoreTransaction transaction =
              envelopeItem.getTransaction(options.getSerializer());
          if (transaction != null) {
            final @NotNull List<IchnoisCoreSpan> spans = transaction.getSpans();
            // When a transaction is dropped, we also record its spans as dropped plus one,
            // since Relay extracts an additional span from the transaction.
            recordLostEventInternal(
                reason.getReason(), DataCategory.Span.getCategory(), spans.size() + 1L);
            executeOnDiscard(reason, DataCategory.Span, spans.size() + 1L);
          }
          recordLostEventInternal(reason.getReason(), itemCategory.getCategory(), 1L);
          executeOnDiscard(reason, itemCategory, 1L);
        } else if (itemCategory.equals(DataCategory.LogItem)) {
          final long count = itemCountFromHeader(envelopeItem);
          recordLostEventInternal(reason.getReason(), itemCategory.getCategory(), count);
          final long logBytes = envelopeItem.getData().length;
          recordLostEventInternal(reason.getReason(), DataCategory.LogByte.getCategory(), logBytes);
          executeOnDiscard(reason, itemCategory, count);
        } else if (itemCategory.equals(DataCategory.TraceMetric)) {
          final long count = itemCountFromHeader(envelopeItem);
          recordLostEventInternal(reason.getReason(), itemCategory.getCategory(), count);
          final long metricBytes = envelopeItem.getData().length;
          recordLostEventInternal(
              reason.getReason(), DataCategory.TraceMetricByte.getCategory(), metricBytes);
          executeOnDiscard(reason, itemCategory, count);
        } else if (itemCategory.equals(DataCategory.Span)) {
          // Batched span items (span streaming) aren't created by this SDK yet, but hybrid SDKs
          // like ichnois-dart send them through it via InternalIchnoisCoreSdk.captureEnvelope.
          final long count = itemCountFromHeader(envelopeItem);
          recordLostEventInternal(reason.getReason(), itemCategory.getCategory(), count);
          executeOnDiscard(reason, itemCategory, count);
        } else {
          recordLostEventInternal(reason.getReason(), itemCategory.getCategory(), 1L);
          executeOnDiscard(reason, itemCategory, 1L);
        }
      }
    } catch (Throwable e) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, e, "Unable to record lost envelope item.");
    }
  }

  @Override
  public void recordLostEvent(@NotNull DiscardReason reason, @NotNull DataCategory category) {
    recordLostEvent(reason, category, 1);
  }

  @Override
  public void recordLostEvent(
      @NotNull DiscardReason reason, @NotNull DataCategory category, long count) {
    try {
      recordLostEventInternal(reason.getReason(), category.getCategory(), count);
      executeOnDiscard(reason, category, count);
    } catch (Throwable e) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, e, "Unable to record lost event.");
    }
  }

  private void executeOnDiscard(
      @NotNull DiscardReason reason, @NotNull DataCategory category, @NotNull Long countToAdd) {
    if (options.getOnDiscard() != null) {
      try {
        options.getOnDiscard().execute(reason, category, countToAdd);
      } catch (Throwable e) {
        options.getLogger().log(IchnoisCoreLevel.ERROR, "The onDiscard callback threw an exception.", e);
      }
    }
  }

  private void recordLostEventInternal(
      @NotNull String reason, @NotNull String category, @NotNull Long countToAdd) {
    final ClientReportKey key = new ClientReportKey(reason, category);
    storage.addCount(key, countToAdd);
  }

  // The number of items batched into a log, metric or span envelope item is stored in its header,
  // so we read it from there instead of deserializing the payload. Deserializing on the discard
  // path is expensive and, under sustained rate limiting, caused a CPU busy-loop (JAVA-662).
  private long itemCountFromHeader(final @NotNull IchnoisCoreEnvelopeItem envelopeItem) {
    final @Nullable Integer itemCount = envelopeItem.getHeader().getItemCount();
    return itemCount != null ? itemCount : 1L;
  }

  @Nullable
  ClientReport resetCountsAndGenerateClientReport() {
    final Date currentDate = DateUtils.getCurrentDateTime();
    final List<DiscardedEvent> discardedEvents = storage.resetCountsAndGet();

    if (discardedEvents.isEmpty()) {
      return null;
    } else {
      return new ClientReport(currentDate, discardedEvents);
    }
  }

  private void restoreCountsFromClientReport(@Nullable ClientReport clientReport) {
    if (clientReport == null) {
      return;
    }

    for (final DiscardedEvent discardedEvent : clientReport.getDiscardedEvents()) {
      recordLostEventInternal(
          discardedEvent.getReason(), discardedEvent.getCategory(), discardedEvent.getQuantity());
    }
  }

  private DataCategory categoryFromItemType(IchnoisCoreItemType itemType) {
    if (IchnoisCoreItemType.Event.equals(itemType)) {
      return DataCategory.Error;
    }
    if (IchnoisCoreItemType.Session.equals(itemType)) {
      return DataCategory.Session;
    }
    if (IchnoisCoreItemType.Transaction.equals(itemType)) {
      return DataCategory.Transaction;
    }
    if (IchnoisCoreItemType.UserFeedback.equals(itemType)) {
      return DataCategory.UserReport;
    }
    if (IchnoisCoreItemType.Feedback.equals(itemType)) {
      return DataCategory.Feedback;
    }
    if (IchnoisCoreItemType.Profile.equals(itemType)) {
      return DataCategory.Profile;
    }
    if (IchnoisCoreItemType.ProfileChunk.equals(itemType)) {
      return DataCategory.ProfileChunkUi;
    }
    if (IchnoisCoreItemType.Attachment.equals(itemType)) {
      return DataCategory.Attachment;
    }
    if (IchnoisCoreItemType.CheckIn.equals(itemType)) {
      return DataCategory.Monitor;
    }
    if (IchnoisCoreItemType.ReplayVideo.equals(itemType)) {
      return DataCategory.Replay;
    }
    if (IchnoisCoreItemType.Log.equals(itemType)) {
      return DataCategory.LogItem;
    }
    if (IchnoisCoreItemType.Span.equals(itemType)) {
      return DataCategory.Span;
    }
    if (IchnoisCoreItemType.TraceMetric.equals(itemType)) {
      return DataCategory.TraceMetric;
    }

    return DataCategory.Default;
  }
}
