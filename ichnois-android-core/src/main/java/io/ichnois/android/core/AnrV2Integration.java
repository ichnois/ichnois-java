package io.ichnois.android.core;

import static io.ichnois.util.IntegrationUtils.addIntegrationToSdkVersion;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import io.ichnois.Attachment;
import io.ichnois.DateUtils;
import io.ichnois.Hint;
import io.ichnois.ILogger;
import io.ichnois.IScopes;
import io.ichnois.Integration;
import io.ichnois.IchnoisCoreEvent;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.android.core.cache.AndroidEnvelopeCache;
import io.ichnois.android.core.internal.threaddump.Lines;
import io.ichnois.android.core.internal.threaddump.ThreadDumpParser;
import io.ichnois.android.core.internal.util.NativeEventUtils;
import io.ichnois.hints.AbnormalExit;
import io.ichnois.hints.Backfillable;
import io.ichnois.hints.BlockingFlushHint;
import io.ichnois.protocol.ArtContext;
import io.ichnois.protocol.DebugImage;
import io.ichnois.protocol.DebugMeta;
import io.ichnois.protocol.Message;
import io.ichnois.protocol.IchnoisCoreId;
import io.ichnois.protocol.IchnoisCoreThread;
import io.ichnois.transport.CurrentDateProvider;
import io.ichnois.transport.ICurrentDateProvider;
import io.ichnois.util.HintUtils;
import io.ichnois.util.Objects;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressLint("NewApi") // we check this in AnrIntegrationFactory
public class AnrV2Integration implements Integration, Closeable {

  private final @NotNull Context context;
  private final @NotNull ICurrentDateProvider dateProvider;
  private @Nullable IchnoisCoreAndroidOptions options;

  public AnrV2Integration(final @NotNull Context context) {
    // using CurrentDateProvider instead of AndroidCurrentDateProvider as AppExitInfo uses
    // System.currentTimeMillis
    this(context, CurrentDateProvider.getInstance());
  }

  AnrV2Integration(
      final @NotNull Context context, final @NotNull ICurrentDateProvider dateProvider) {
    this.context = ContextUtils.getApplicationContext(context);
    this.dateProvider = dateProvider;
  }

  @SuppressLint("NewApi") // we do the check in the AnrIntegrationFactory
  @Override
  public void register(@NotNull IScopes scopes, @NotNull IchnoisCoreOptions options) {
    this.options =
        Objects.requireNonNull(
            (options instanceof IchnoisCoreAndroidOptions) ? (IchnoisCoreAndroidOptions) options : null,
            "IchnoisCoreAndroidOptions is required");

    this.options
        .getLogger()
        .log(IchnoisCoreLevel.DEBUG, "AnrIntegration enabled: %s", this.options.isAnrEnabled());

    if (this.options.getCacheDirPath() == null) {
      this.options
          .getLogger()
          .log(IchnoisCoreLevel.INFO, "Cache dir is not set, unable to process ANRs");
      return;
    }

    if (this.options.isAnrEnabled()) {
      try {
        options
            .getExecutorService()
            .submit(
                new ApplicationExitInfoHistoryDispatcher(
                    context, scopes, this.options, dateProvider, new AnrV2Policy(this.options)));
      } catch (Throwable e) {
        options.getLogger().log(IchnoisCoreLevel.DEBUG, "Failed to start ANR processor.", e);
      }
      options.getLogger().log(IchnoisCoreLevel.DEBUG, "AnrV2Integration installed.");
      addIntegrationToSdkVersion("AnrV2");
    }
  }

  @Override
  public void close() throws IOException {
    if (options != null) {
      options.getLogger().log(IchnoisCoreLevel.DEBUG, "AnrV2Integration removed.");
    }
  }

  private static final class AnrV2Policy
      implements ApplicationExitInfoHistoryDispatcher.ApplicationExitInfoPolicy {

    private final @NotNull IchnoisCoreAndroidOptions options;

    AnrV2Policy(final @NotNull IchnoisCoreAndroidOptions options) {
      this.options = options;
    }

    @Override
    public @NotNull String getLabel() {
      return "ANR";
    }

    @Override
    public boolean matches(final @NotNull ApplicationExitInfo exitInfo) {
      return exitInfo.getReason() == ApplicationExitInfo.REASON_ANR;
    }

    @Override
    public boolean shouldReportHistorical() {
      return options.isReportHistoricalAnrs();
    }

    @Override
    public @Nullable Long getLastReportedTimestamp() {
      return AndroidEnvelopeCache.lastReportedAnr(options);
    }

    @Override
    public void markReported(final long timestamp) {
      AndroidEnvelopeCache.markAnrReported(options, timestamp);
    }

    @Override
    public @Nullable ApplicationExitInfoHistoryDispatcher.Report buildReport(
        final @NotNull ApplicationExitInfo exitInfo, final boolean shouldEnrich) {
      final long anrTimestamp = exitInfo.getTimestamp();
      final boolean isBackground =
          exitInfo.getImportance() != ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;

      final ParseResult result = parseThreadDump(exitInfo, isBackground);
      if (result.type == ParseResult.Type.NO_DUMP) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.WARNING,
                "Not reporting ANR event as there was no thread dump for the ANR %s",
                exitInfo.toString());
        return null;
      }
      final AnrV2Hint anrHint =
          new AnrV2Hint(
              options.getFlushTimeoutMillis(),
              options.getLogger(),
              anrTimestamp,
              shouldEnrich,
              isBackground);

      final Hint hint = HintUtils.createWithTypeCheckHint(anrHint);

      final IchnoisCoreEvent event = new IchnoisCoreEvent();
      if (result.type == ParseResult.Type.ERROR) {
        final Message ichnoisMessage = new Message();
        ichnoisMessage.setFormatted(
            "IchnoisCore Android SDK failed to parse system thread dump for "
                + "this ANR. We recommend enabling [IchnoisCoreOptions.isAttachAnrThreadDump] option "
                + "to attach the thread dump as plain text and report this issue on GitHub.");
        event.setMessage(ichnoisMessage);
      } else if (result.type == ParseResult.Type.DUMP) {
        event.setThreads(result.threads);
        if (result.debugImages != null) {
          final DebugMeta debugMeta = new DebugMeta();
          debugMeta.setImages(result.debugImages);
          event.setDebugMeta(debugMeta);
        }
        if (result.artContext != null) {
          event.getContexts().setArt(result.artContext);
        }
      }
      event.setLevel(IchnoisCoreLevel.FATAL);
      event.setTimestamp(DateUtils.getDateTime(anrTimestamp));

      if (options.isAttachAnrThreadDump()) {
        if (result.dump != null) {
          hint.setThreadDump(Attachment.fromThreadDump(result.dump));
        }
      }

      return new ApplicationExitInfoHistoryDispatcher.Report(event, hint, anrHint);
    }

    private @NotNull ParseResult parseThreadDump(
        final @NotNull ApplicationExitInfo exitInfo, final boolean isBackground) {
      final byte[] dump;

      try (final InputStream trace = exitInfo.getTraceInputStream()) {
        if (trace == null) {
          return new ParseResult(ParseResult.Type.NO_DUMP);
        }
        dump = NativeEventUtils.readBytes(trace);
      } catch (Throwable e) {
        options.getLogger().log(IchnoisCoreLevel.WARNING, "Failed to read ANR thread dump", e);
        return new ParseResult(ParseResult.Type.NO_DUMP);
      }

      try (final BufferedReader reader =
          new BufferedReader(new InputStreamReader(new ByteArrayInputStream(dump)))) {
        final Lines lines = Lines.readLines(reader);

        final ThreadDumpParser threadDumpParser = new ThreadDumpParser(options, isBackground);
        threadDumpParser.parse(lines);

        final @NotNull List<IchnoisCoreThread> threads = threadDumpParser.getThreads();
        final @NotNull List<DebugImage> debugImages = threadDumpParser.getDebugImages();
        final @Nullable ArtContext artContext = threadDumpParser.getArtContext();

        if (threads.isEmpty()) {
          // if the list is empty this means the system failed to capture a proper thread dump of
          // the android threads, and only contains kernel-level threads and statuses, those ANRs
          // are not actionable and neither they are reported by Google Play Console, so we just
          // fall back to not reporting them
          return new ParseResult(ParseResult.Type.NO_DUMP);
        }
        return new ParseResult(ParseResult.Type.DUMP, dump, threads, debugImages, artContext);
      } catch (Throwable e) {
        options.getLogger().log(IchnoisCoreLevel.WARNING, "Failed to parse ANR thread dump", e);
        return new ParseResult(ParseResult.Type.ERROR, dump);
      }
    }
  }

  @ApiStatus.Internal
  public static final class AnrV2Hint extends BlockingFlushHint
      implements Backfillable, AbnormalExit {

    private final long timestamp;

    private final boolean shouldEnrich;

    private final boolean isBackgroundAnr;

    public AnrV2Hint(
        final long flushTimeoutMillis,
        final @NotNull ILogger logger,
        final long timestamp,
        final boolean shouldEnrich,
        final boolean isBackgroundAnr) {
      super(flushTimeoutMillis, logger);
      this.timestamp = timestamp;
      this.shouldEnrich = shouldEnrich;
      this.isBackgroundAnr = isBackgroundAnr;
    }

    @Override
    public boolean ignoreCurrentThread() {
      return false;
    }

    @NotNull
    @Override
    public Long timestamp() {
      return timestamp;
    }

    @Override
    public boolean shouldEnrich() {
      return shouldEnrich;
    }

    @Override
    public String mechanism() {
      return isBackgroundAnr ? "anr_background" : "anr_foreground";
    }

    @Override
    public boolean isFlushable(@Nullable IchnoisCoreId eventId) {
      return true;
    }

    @Override
    public void setFlushable(@NotNull IchnoisCoreId eventId) {}
  }

  static final class ParseResult {

    enum Type {
      DUMP,
      NO_DUMP,
      ERROR
    }

    final Type type;
    final @Nullable byte[] dump;
    final @Nullable List<IchnoisCoreThread> threads;
    final @Nullable List<DebugImage> debugImages;
    final @Nullable ArtContext artContext;

    ParseResult(final @NotNull Type type) {
      this.type = type;
      this.dump = null;
      this.threads = null;
      this.debugImages = null;
      this.artContext = null;
    }

    ParseResult(final @NotNull Type type, final byte[] dump) {
      this.type = type;
      this.dump = dump;
      this.threads = null;
      this.debugImages = null;
      this.artContext = null;
    }

    ParseResult(
        final @NotNull Type type,
        final byte[] dump,
        final @Nullable List<IchnoisCoreThread> threads,
        final @Nullable List<DebugImage> debugImages,
        final @Nullable ArtContext artContext) {
      this.type = type;
      this.dump = dump;
      this.threads = threads;
      this.debugImages = debugImages;
      this.artContext = artContext;
    }
  }
}
