package io.ichnois.android.core;

import static io.ichnois.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import io.ichnois.Attachment;
import io.ichnois.DateUtils;
import io.ichnois.Hint;
import io.ichnois.ILogger;
import io.ichnois.IScopes;
import io.ichnois.Integration;
import io.ichnois.IchnoisCoreEnvelope;
import io.ichnois.IchnoisCoreEnvelopeItem;
import io.ichnois.IchnoisCoreEvent;
import io.ichnois.IchnoisCoreItemType;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.android.core.ApplicationExitInfoHistoryDispatcher.ApplicationExitInfoPolicy;
import io.ichnois.android.core.NativeEventCollector.NativeEventData;
import io.ichnois.android.core.cache.AndroidEnvelopeCache;
import io.ichnois.android.core.internal.tombstone.NativeExceptionMechanism;
import io.ichnois.android.core.internal.tombstone.TombstoneParser;
import io.ichnois.android.core.internal.util.NativeEventUtils;
import io.ichnois.hints.Backfillable;
import io.ichnois.hints.BlockingFlushHint;
import io.ichnois.hints.NativeCrashExit;
import io.ichnois.protocol.DebugMeta;
import io.ichnois.protocol.Mechanism;
import io.ichnois.protocol.IchnoisCoreException;
import io.ichnois.protocol.IchnoisCoreId;
import io.ichnois.protocol.IchnoisCoreThread;
import io.ichnois.transport.CurrentDateProvider;
import io.ichnois.transport.ICurrentDateProvider;
import io.ichnois.util.HintUtils;
import io.ichnois.util.Objects;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class TombstoneIntegration implements Integration, Closeable {
  private final @NotNull Context context;
  private final @NotNull ICurrentDateProvider dateProvider;
  private @Nullable IchnoisCoreAndroidOptions options;

  public TombstoneIntegration(final @NotNull Context context) {
    // using CurrentDateProvider instead of AndroidCurrentDateProvider as AppExitInfo uses
    // System.currentTimeMillis
    this(context, CurrentDateProvider.getInstance());
  }

  TombstoneIntegration(
      final @NotNull Context context, final @NotNull ICurrentDateProvider dateProvider) {
    this.context = ContextUtils.getApplicationContext(context);
    this.dateProvider = dateProvider;
  }

  @Override
  public void register(@NotNull IScopes scopes, @NotNull IchnoisCoreOptions options) {
    this.options =
        Objects.requireNonNull(
            (options instanceof IchnoisCoreAndroidOptions) ? (IchnoisCoreAndroidOptions) options : null,
            "IchnoisCoreAndroidOptions is required");

    this.options
        .getLogger()
        .log(
            IchnoisCoreLevel.DEBUG,
            "TombstoneIntegration enabled: %s",
            this.options.isTombstoneEnabled());

    if (this.options.isTombstoneEnabled()) {
      if (this.options.getCacheDirPath() == null) {
        this.options
            .getLogger()
            .log(IchnoisCoreLevel.INFO, "Cache dir is not set, unable to process Tombstones");
        return;
      }

      try {
        options
            .getExecutorService()
            .submit(
                new ApplicationExitInfoHistoryDispatcher(
                    context,
                    scopes,
                    this.options,
                    dateProvider,
                    new TombstonePolicy(this.options, this.context)));
      } catch (Throwable e) {
        options.getLogger().log(IchnoisCoreLevel.DEBUG, "Failed to start tombstone processor.", e);
      }
      options.getLogger().log(IchnoisCoreLevel.DEBUG, "TombstoneIntegration installed.");
      addIntegrationToSdkVersion("Tombstone");
    }
  }

  @Override
  public void close() throws IOException {
    if (options != null) {
      options.getLogger().log(IchnoisCoreLevel.DEBUG, "TombstoneIntegration removed.");
    }
  }

  @ApiStatus.Internal
  public static class TombstonePolicy implements ApplicationExitInfoPolicy {

    private final @NotNull IchnoisCoreAndroidOptions options;
    private final @NotNull NativeEventCollector nativeEventCollector;
    @NotNull private final Context context;

    public TombstonePolicy(final @NotNull IchnoisCoreAndroidOptions options, @NotNull Context context) {
      this.options = options;
      this.nativeEventCollector = new NativeEventCollector(options);
      this.context = context;
    }

    @Override
    public @NotNull String getLabel() {
      return "Tombstone";
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public boolean matches(final @NotNull ApplicationExitInfo exitInfo) {
      return exitInfo.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE;
    }

    @Override
    public boolean shouldReportHistorical() {
      return options.isReportHistoricalTombstones();
    }

    @Override
    public @Nullable Long getLastReportedTimestamp() {
      return AndroidEnvelopeCache.lastReportedTombstone(options);
    }

    @Override
    public void markReported(final long timestamp) {
      AndroidEnvelopeCache.markTombstoneReported(options, timestamp);
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public @Nullable ApplicationExitInfoHistoryDispatcher.Report buildReport(
        final @NotNull ApplicationExitInfo exitInfo, final boolean enrich) {
      IchnoisCoreEvent event;
      @Nullable byte[] rawTombstone = null;
      try {
        final boolean attachRaw = options.isAttachRawTombstone();
        try (final InputStream tombstoneInputStream = exitInfo.getTraceInputStream()) {
          if (tombstoneInputStream == null) {
            options
                .getLogger()
                .log(
                    IchnoisCoreLevel.WARNING,
                    "No tombstone InputStream available for ApplicationExitInfo from %s",
                    DateTimeFormatter.ISO_INSTANT.format(
                        Instant.ofEpochMilli(exitInfo.getTimestamp())));
            return null;
          }

          if (attachRaw) {
            rawTombstone = NativeEventUtils.readBytes(tombstoneInputStream);
          }

          final InputStream parserInput =
              attachRaw ? new ByteArrayInputStream(rawTombstone) : tombstoneInputStream;
          try (final TombstoneParser parser =
              new TombstoneParser(
                  parserInput,
                  this.options.getInAppIncludes(),
                  this.options.getInAppExcludes(),
                  this.context.getApplicationInfo().nativeLibraryDir)) {
            event = parser.parse();
          }
        }
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.WARNING,
                "Failed to parse tombstone from %s: %s",
                DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(exitInfo.getTimestamp())),
                e.getMessage());
        return null;
      }

      final long tombstoneTimestamp = exitInfo.getTimestamp();
      event.setTimestamp(DateUtils.getDateTime(tombstoneTimestamp));

      final TombstoneHint tombstoneHint =
          new TombstoneHint(
              options.getFlushTimeoutMillis(), options.getLogger(), tombstoneTimestamp, enrich);
      final Hint hint = HintUtils.createWithTypeCheckHint(tombstoneHint);

      if (rawTombstone != null) {
        hint.setTombstone(Attachment.fromTombstone(rawTombstone));
      }

      try {
        final @Nullable IchnoisCoreEvent mergedEvent =
            mergeWithMatchingNativeEvents(tombstoneTimestamp, event, hint);
        if (mergedEvent != null) {
          event = mergedEvent;
        }
      } catch (Throwable e) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.WARNING,
                "Failed to merge native event with tombstone, continuing without merge: %s",
                e.getMessage());
      }

      return new ApplicationExitInfoHistoryDispatcher.Report(event, hint, tombstoneHint);
    }

    /**
     * Attempts to find a matching native SDK event for the tombstone and merge them.
     *
     * @return The merged native event (with tombstone data applied) if a match was found and
     *     merged, or null if no matching event was found or merge failed.
     */
    private @Nullable IchnoisCoreEvent mergeWithMatchingNativeEvents(
        long tombstoneTimestamp, IchnoisCoreEvent tombstoneEvent, Hint hint) {
      // Try to find and remove matching native event from outbox
      final @Nullable NativeEventData matchingNativeEvent =
          nativeEventCollector.findAndRemoveMatchingNativeEvent(tombstoneTimestamp);

      if (matchingNativeEvent == null) {
        // NativeEventCollector already logs why no event matched.
        return null;
      }

      options
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Found matching native event for tombstone, removing from outbox: %s",
              matchingNativeEvent.getFile().getName());

      // Delete from outbox so OutboxSender doesn't send it
      boolean deletionSuccess = nativeEventCollector.deleteNativeEventFile(matchingNativeEvent);

      if (deletionSuccess) {
        final IchnoisCoreEvent nativeEvent = matchingNativeEvent.getEvent();
        mergeNativeCrashes(nativeEvent, tombstoneEvent);
        addNativeAttachmentsToTombstoneHint(matchingNativeEvent, hint);
        return nativeEvent;
      }
      return null;
    }

    private void addNativeAttachmentsToTombstoneHint(
        @NonNull NativeEventData matchingNativeEvent, Hint hint) {
      @NotNull IchnoisCoreEnvelope nativeEnvelope = matchingNativeEvent.getEnvelope();
      for (IchnoisCoreEnvelopeItem item : nativeEnvelope.getItems()) {
        try {
          @Nullable String attachmentFileName = item.getHeader().getFileName();
          if (item.getHeader().getType() != IchnoisCoreItemType.Attachment
              || attachmentFileName == null) {
            continue;
          }
          hint.addAttachment(
              new Attachment(
                  item.getData(),
                  attachmentFileName,
                  item.getHeader().getContentType(),
                  item.getHeader().getAttachmentType(),
                  false));
        } catch (Throwable e) {
          options
              .getLogger()
              .log(IchnoisCoreLevel.DEBUG, "Failed to process envelope item: %s", e.getMessage());
        }
      }
    }

    private void mergeNativeCrashes(
        final @NotNull IchnoisCoreEvent nativeEvent, final @NotNull IchnoisCoreEvent tombstoneEvent) {
      // we take the event data verbatim from the Native SDK and only apply tombstone data where we
      // are sure that it will improve the outcome:
      // * context from the Native SDK will be closer to what users want than any backfilling
      // * the Native SDK only tracks the crashing thread  (vs. tombstone dumps all)
      // * even for the crashing  we expect a much better stack-trace (+ symbolication)
      // * tombstone adds additional exception meta-data to signal handler content
      // * we add debug-meta for consistency since the Native SDK caches memory maps early
      @Nullable List<IchnoisCoreException> tombstoneExceptions = tombstoneEvent.getExceptions();
      @Nullable DebugMeta tombstoneDebugMeta = tombstoneEvent.getDebugMeta();
      @Nullable List<IchnoisCoreThread> tombstoneThreads = tombstoneEvent.getThreads();
      if (tombstoneExceptions != null
          && !tombstoneExceptions.isEmpty()
          && tombstoneDebugMeta != null
          && tombstoneThreads != null) {
        // native crashes don't nest, we always expect one level.
        IchnoisCoreException exception = tombstoneExceptions.get(0);
        @Nullable Mechanism mechanism = exception.getMechanism();
        if (mechanism != null) {
          mechanism.setType(NativeExceptionMechanism.TOMBSTONE_MERGED.getValue());
        }

        // Don't overwrite existing messages in the native event
        if (nativeEvent.getMessage() == null
            || nativeEvent.getMessage().getMessage() == null
            || nativeEvent.getMessage().getMessage().isEmpty()) {
          nativeEvent.setMessage(tombstoneEvent.getMessage());
        }

        nativeEvent.setExceptions(tombstoneExceptions);
        nativeEvent.setDebugMeta(tombstoneDebugMeta);
        nativeEvent.setThreads(tombstoneThreads);
      }
    }
  }

  @ApiStatus.Internal
  public static final class TombstoneHint extends BlockingFlushHint
      implements Backfillable, NativeCrashExit {

    private final long tombstoneTimestamp;
    private final boolean shouldEnrich;

    public TombstoneHint(
        long flushTimeoutMillis,
        @NotNull ILogger logger,
        long tombstoneTimestamp,
        boolean shouldEnrich) {
      super(flushTimeoutMillis, logger);
      this.tombstoneTimestamp = tombstoneTimestamp;
      this.shouldEnrich = shouldEnrich;
    }

    @NotNull
    @Override
    public Long timestamp() {
      return tombstoneTimestamp;
    }

    @Override
    public boolean shouldEnrich() {
      return shouldEnrich;
    }

    @Override
    public boolean isFlushable(@Nullable IchnoisCoreId eventId) {
      return true;
    }

    @Override
    public void setFlushable(@NotNull IchnoisCoreId eventId) {}
  }
}
