package io.ichnois.asyncprofiler.profiling;

import static io.ichnois.DataCategory.All;
import static java.util.concurrent.TimeUnit.SECONDS;

import io.ichnois.DataCategory;
import io.ichnois.IContinuousProfiler;
import io.ichnois.ILogger;
import io.ichnois.IScopes;
import io.ichnois.IIchnoisCoreExecutorService;
import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.NoOpScopes;
import io.ichnois.ProfileChunk;
import io.ichnois.ProfileLifecycle;
import io.ichnois.IchnoisCore;
import io.ichnois.IchnoisCoreDate;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreNanotimeDate;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.IchnoisCoreUUID;
import io.ichnois.TracesSampler;
import io.ichnois.profiling.ProfileRecordingState;
import io.ichnois.protocol.IchnoisCoreId;
import io.ichnois.transport.RateLimiter;
import io.ichnois.util.AutoClosableReentrantLock;
import io.ichnois.util.IchnoisCoreRandom;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import one.profiler.AsyncProfiler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

@ApiStatus.Internal
public final class JavaContinuousProfiler
    implements IContinuousProfiler, RateLimiter.IRateLimitObserver {
  private static final long MAX_CHUNK_DURATION_MILLIS = 10000;
  private final List<String> invalidFilenameChars =
      Arrays.asList(
          ".", ",", ":", ";", "|", "\\", "=", "%", "&", "[", "]", "(", ")", "<", ">", "{", "}", "!",
          "*", "?", "~", "\"", "'", "$", "`", "^");

  private final @NotNull ILogger logger;
  private final @Nullable String profilingTracesDirPath;
  private final int profilingTracesHz;
  private final @NotNull IIchnoisCoreExecutorService executorService;
  private boolean isInitialized = false;
  private boolean isRunning = false;
  private @Nullable IScopes scopes;
  private @Nullable Future<?> stopFuture;
  private final @NotNull List<ProfileChunk.Builder> payloadBuilders = new ArrayList<>();
  private @NotNull IchnoisCoreId profilerId = IchnoisCoreId.EMPTY_ID;
  private final @NotNull AtomicBoolean isClosed = new AtomicBoolean(false);
  private @NotNull IchnoisCoreDate startProfileChunkTimestamp = new IchnoisCoreNanotimeDate();

  private volatile @NotNull String filename = "";

  private @NotNull AsyncProfiler profiler;
  private volatile boolean shouldSample = true;
  private boolean isSampled = false;
  private int rootSpanCounter = 0;

  private final AutoClosableReentrantLock lock = new AutoClosableReentrantLock();
  private final AutoClosableReentrantLock payloadLock = new AutoClosableReentrantLock();

  public JavaContinuousProfiler(
      final @NotNull ILogger logger,
      final @Nullable String profilingTracesDirPath,
      final int profilingTracesHz,
      final @NotNull IIchnoisCoreExecutorService executorService)
      throws Exception {
    this.logger = logger;
    this.profilingTracesDirPath = profilingTracesDirPath;
    this.profilingTracesHz = profilingTracesHz;
    this.executorService = executorService;
    initializeProfiler();
  }

  private void initializeProfiler() throws Exception {
    this.profiler = AsyncProfiler.getInstance();
    // Check version to verify profiler is working
    String version = profiler.execute("version");
    logger.log(IchnoisCoreLevel.DEBUG, "AsyncProfiler initialized successfully. Version: " + version);
  }

  private boolean init() {
    if (isInitialized) {
      return true;
    }
    isInitialized = true;

    if (isInvalidDirectory()) {
      return false;
    }

    if (profilingTracesHz <= 0) {
      logger.log(
          IchnoisCoreLevel.WARNING,
          "Disabling profiling because trace rate is set to %d",
          profilingTracesHz);
      return false;
    }
    return true;
  }

  private boolean isInvalidDirectory() {
    if (profilingTracesDirPath == null) {
      logger.log(
          IchnoisCoreLevel.WARNING,
          "Disabling profiling because no profiling traces dir path is defined in options.");
      return true;
    }

    for (String invalidChar : invalidFilenameChars) {
      if (profilingTracesDirPath.contains(invalidChar)) {
        logger.log(
            IchnoisCoreLevel.WARNING,
            "Disabling profiling because traces directory path contains invalid character: %s",
            invalidChar);
        return true;
      }
    }

    File profileDir = new File(profilingTracesDirPath);

    if (!profileDir.canWrite() || !profileDir.exists()) {
      logger.log(
          IchnoisCoreLevel.WARNING,
          "Disabling profiling because traces directory is not writable or does not exist: %s (writable=%b, exists=%b)",
          profilingTracesDirPath,
          profileDir.canWrite(),
          profileDir.exists());
      return true;
    }
    return false;
  }

  @Override
  public void startProfiler(
      final @NotNull ProfileLifecycle profileLifecycle,
      final @NotNull TracesSampler tracesSampler) {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
      if (shouldSample) {
        isSampled = tracesSampler.sampleSessionProfile(IchnoisCoreRandom.current().nextDouble());
        shouldSample = false;
      }
      if (!isSampled) {
        logger.log(IchnoisCoreLevel.DEBUG, "Profiler was not started due to sampling decision.");
        return;
      }

      switch (profileLifecycle) {
        case TRACE:
          // rootSpanCounter should never be negative, unless the user changed profile lifecycle
          // while
          // the profiler is running or close() is called. This is just a safety check.
          if (rootSpanCounter < 0) {
            rootSpanCounter = 0;
          }
          rootSpanCounter++;
          break;
        case MANUAL:
          // We check if the profiler is already running and log a message only in manual mode,
          // since
          // in trace mode we can have multiple concurrent traces
          if (isRunning()) {
            logger.log(IchnoisCoreLevel.DEBUG, "Profiler is already running.");
            return;
          }
          break;
      }

      if (!isRunning()) {
        logger.log(IchnoisCoreLevel.DEBUG, "Started Profiler.");
        start();
      }
    } catch (Exception e) {
      logger.log(IchnoisCoreLevel.ERROR, "Error starting profiler: ", e);
    }
  }

  private void initScopes() {
    if ((scopes == null || scopes == NoOpScopes.getInstance())
        && IchnoisCore.getCurrentScopes() != NoOpScopes.getInstance()) {
      this.scopes = IchnoisCore.getCurrentScopes();
      final @Nullable RateLimiter rateLimiter = scopes.getRateLimiter();
      if (rateLimiter != null) {
        rateLimiter.addRateLimitObserver(this);
      }
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void start() {
    initScopes();

    if (!init()) {
      return;
    }

    if (scopes != null) {
      final @Nullable RateLimiter rateLimiter = scopes.getRateLimiter();
      if (rateLimiter != null
          && (rateLimiter.isActiveForCategory(All)
              || rateLimiter.isActiveForCategory(DataCategory.ProfileChunk))) {
        logger.log(IchnoisCoreLevel.WARNING, "SDK is rate limited. Stopping profiler.");
        // Let's stop and reset profiler id, as the profile is now broken anyway
        stop(false);
        return;
      }

      startProfileChunkTimestamp = scopes.getOptions().getDateProvider().now();
    } else {
      startProfileChunkTimestamp = new IchnoisCoreNanotimeDate();
    }

    filename = profilingTracesDirPath + File.separator + IchnoisCoreUUID.generateIchnoisCoreId() + ".jfr";

    File jfrFile = new File(filename);

    try {
      final String profilingIntervalMicros =
          String.format("%dus", (int) SECONDS.toMicros(1) / profilingTracesHz);
      // Example command: start,jfr,event=wall,interval=9900us,file=/path/to/trace.jfr
      final String command =
          String.format(
              "start,jfr,event=wall,nobatch,interval=%s,file=%s",
              profilingIntervalMicros, filename);

      profiler.execute(command);
    } catch (Exception e) {
      logger.log(IchnoisCoreLevel.ERROR, "Failed to start profiling: ", e);
      filename = "";
      // Try to clean up the file if it was created
      safelyRemoveFile(jfrFile);
      return;
    }

    isRunning = true;

    if (profilerId == IchnoisCoreId.EMPTY_ID) {
      profilerId = new IchnoisCoreId();
    }

    try {
      stopFuture = executorService.schedule(() -> stop(true), MAX_CHUNK_DURATION_MILLIS);
    } catch (RejectedExecutionException e) {
      logger.log(
          IchnoisCoreLevel.ERROR,
          "Failed to schedule profiling chunk finish. Did you call IchnoisCore.close()?",
          e);
      // If we can't schedule the auto-stop, stop immediately without restart
      stop(false);
    }
  }

  @Override
  public void stopProfiler(final @NotNull ProfileLifecycle profileLifecycle) {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
      switch (profileLifecycle) {
        case TRACE:
          rootSpanCounter--;
          // If there are active spans, and profile lifecycle is trace, we don't stop the profiler
          if (rootSpanCounter > 0) {
            return;
          }
          // rootSpanCounter should never be negative, unless the user changed profile lifecycle
          // while the profiler is running or close() is called. This is just a safety check.
          if (rootSpanCounter < 0) {
            rootSpanCounter = 0;
          }
          // Stop immediately without restart
          stop(false);
          break;
        case MANUAL:
          // Stop immediately without restart
          stop(false);
          break;
      }
    }
  }

  private void stop(final boolean restartProfiler) {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
      if (stopFuture != null) {
        stopFuture.cancel(true);
      }
      // check if profiler was created and it's running
      if (!isRunning) {
        // When the profiler is stopped due to an error (e.g. offline or rate limited), reset the
        // id
        profilerId = IchnoisCoreId.EMPTY_ID;
        return;
      }

      File jfrFile = new File(filename);

      try {
        profiler.execute("stop,jfr");
      } catch (Exception e) {
        logger.log(IchnoisCoreLevel.ERROR, "Error stopping profiler, attempting cleanup: ", e);
        // Clean up file if it exists
        safelyRemoveFile(jfrFile);
      }

      // The scopes can be null if the profiler is started before the SDK is initialized (app
      // start profiling), meaning there's no scopes to send the chunks. In that case, we store
      // the data in a list and send it when the next chunk is finished.
      if (jfrFile.exists() && jfrFile.canRead() && jfrFile.length() > 0) {
        try (final @NotNull IIchnoisCoreLifecycleToken ignored2 = payloadLock.acquire()) {
          jfrFile.deleteOnExit();
          payloadBuilders.add(
              new ProfileChunk.Builder(
                  profilerId,
                  new IchnoisCoreId(),
                  new HashMap<>(),
                  jfrFile,
                  startProfileChunkTimestamp,
                  ProfileChunk.PLATFORM_JAVA));
        }
      } else {
        logger.log(
            IchnoisCoreLevel.WARNING,
            "JFR file is invalid or empty: exists=%b, readable=%b, size=%d",
            jfrFile.exists(),
            jfrFile.canRead(),
            jfrFile.length());
        safelyRemoveFile(jfrFile);
      }

      // Always clean up state, even if stop failed
      isRunning = false;
      filename = "";

      if (scopes != null) {
        sendChunks(scopes, scopes.getOptions());
      }

      if (restartProfiler) {
        logger.log(IchnoisCoreLevel.DEBUG, "Profile chunk finished. Starting a new one.");
        start();
      } else {
        // When the profiler is stopped manually, we have to reset its id
        profilerId = IchnoisCoreId.EMPTY_ID;
        logger.log(IchnoisCoreLevel.DEBUG, "Profile chunk finished.");
      }
    } catch (Exception e) {
      logger.log(IchnoisCoreLevel.ERROR, "Error stopping profiler: ", e);
    }
  }

  @Override
  public void reevaluateSampling() {
    shouldSample = true;
  }

  @Override
  public void close(final boolean isTerminating) {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
      rootSpanCounter = 0;
      stop(false);
      if (isTerminating) {
        isClosed.set(true);
      }
    }
  }

  @Override
  public @NotNull IchnoisCoreId getProfilerId() {
    return profilerId;
  }

  // This is currently not used in the JVM profiler, thus we return an empty id for now
  @Override
  public @NotNull IchnoisCoreId getChunkId() {
    return IchnoisCoreId.EMPTY_ID;
  }

  /**
   * This profiler does not track the outcome of its profiling requests, so the answer is always
   * unknown.
   */
  @Override
  public @NotNull ProfileRecordingState getProfileRecordingState(
      final @NotNull IchnoisCoreId profilerId,
      final @NotNull IchnoisCoreDate startTime,
      final @NotNull IchnoisCoreDate endTime) {
    return ProfileRecordingState.UNKNOWN;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void sendChunks(final @NotNull IScopes scopes, final @NotNull IchnoisCoreOptions options) {
    try {
      options
          .getExecutorService()
          .submit(
              () -> {
                // SDK is closed, we don't send the chunks
                if (isClosed.get()) {
                  return;
                }
                final ArrayList<ProfileChunk> payloads = new ArrayList<>(payloadBuilders.size());
                try (final @NotNull IIchnoisCoreLifecycleToken ignored = payloadLock.acquire()) {
                  for (ProfileChunk.Builder builder : payloadBuilders) {
                    payloads.add(builder.build(options));
                  }
                  payloadBuilders.clear();
                }
                for (ProfileChunk payload : payloads) {
                  scopes.captureProfileChunk(payload);
                }
              });
    } catch (Throwable e) {
      options.getLogger().log(IchnoisCoreLevel.DEBUG, "Failed to send profile chunks.", e);
    }
  }

  private void safelyRemoveFile(File file) {
    try {
      if (file.exists()) {
        file.delete();
      }
    } catch (Exception e) {
      logger.log(IchnoisCoreLevel.INFO, "Failed to remove jfr file %s.", file.getAbsolutePath(), e);
    }
  }

  @Override
  public boolean isRunning() {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = lock.acquire()) {
      return isRunning && !filename.isEmpty();
    }
  }

  @VisibleForTesting
  @Nullable
  Future<?> getStopFuture() {
    return stopFuture;
  }

  @VisibleForTesting
  public int getRootSpanCounter() {
    return rootSpanCounter;
  }

  @Override
  public void onRateLimitChanged(@NotNull RateLimiter rateLimiter) {
    // We stop the profiler as soon as we are rate limited, to avoid the performance overhead
    if (rateLimiter.isActiveForCategory(All)
        || rateLimiter.isActiveForCategory(DataCategory.ProfileChunk)) {
      logger.log(IchnoisCoreLevel.WARNING, "SDK is rate limited. Stopping profiler.");
      stop(false);
    }
    // If we are not rate limited anymore, we don't do anything: the profile is broken, so it's
    // useless to restart it automatically
  }
}
