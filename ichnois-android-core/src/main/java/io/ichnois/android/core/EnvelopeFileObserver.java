package io.ichnois.android.core;

import static io.ichnois.IchnoisCoreLevel.ERROR;

import android.os.FileObserver;
import io.ichnois.Hint;
import io.ichnois.IEnvelopeSender;
import io.ichnois.ILogger;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.hints.ApplyScopeData;
import io.ichnois.hints.Cached;
import io.ichnois.hints.Flushable;
import io.ichnois.hints.Resettable;
import io.ichnois.hints.Retryable;
import io.ichnois.hints.SubmissionResult;
import io.ichnois.util.HintUtils;
import io.ichnois.util.Objects;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class EnvelopeFileObserver extends FileObserver {

  private final String rootPath;
  private final IEnvelopeSender envelopeSender;
  private @NotNull final ILogger logger;
  private final long flushTimeoutMillis;

  // The preferred overload (Taking File instead of String) is only available from API 29
  @SuppressWarnings("deprecation")
  EnvelopeFileObserver(
      String path,
      IEnvelopeSender envelopeSender,
      @NotNull ILogger logger,
      final long flushTimeoutMillis) {
    super(path);
    this.rootPath = path;
    this.envelopeSender = Objects.requireNonNull(envelopeSender, "Envelope sender is required.");
    this.logger = Objects.requireNonNull(logger, "Logger is required.");
    this.flushTimeoutMillis = flushTimeoutMillis;
  }

  @Override
  public void onEvent(int eventType, @Nullable String relativePath) {
    if (relativePath == null || eventType != FileObserver.CLOSE_WRITE) {
      return;
    }

    logger.log(
        IchnoisCoreLevel.DEBUG,
        "onEvent fired for EnvelopeFileObserver with event type %d on path: %s for file %s.",
        eventType,
        this.rootPath,
        relativePath);

    // TODO: Only some event types should be pass through?

    final CachedEnvelopeHint cachedHint = new CachedEnvelopeHint(flushTimeoutMillis, logger);

    final Hint hint = HintUtils.createWithTypeCheckHint(cachedHint);

    envelopeSender.processEnvelopeFile(this.rootPath + File.separator + relativePath, hint);
  }

  private static final class CachedEnvelopeHint
      implements Cached, Retryable, SubmissionResult, Flushable, ApplyScopeData, Resettable {
    boolean retry;
    boolean succeeded;

    private @NotNull CountDownLatch latch;
    private final long flushTimeoutMillis;
    private final @NotNull ILogger logger;

    public CachedEnvelopeHint(final long flushTimeoutMillis, final @NotNull ILogger logger) {
      reset();
      this.flushTimeoutMillis = flushTimeoutMillis;
      this.logger = Objects.requireNonNull(logger, "ILogger is required.");
    }

    @Override
    public boolean waitFlush() {
      try {
        return latch.await(flushTimeoutMillis, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        logger.log(ERROR, "Exception while awaiting on lock.", e);
      }
      return false;
    }

    @Override
    public boolean isRetry() {
      return retry;
    }

    @Override
    public void setRetry(boolean retry) {
      this.retry = retry;
    }

    @Override
    public void setResult(boolean succeeded) {
      this.succeeded = succeeded;
      latch.countDown();
    }

    @Override
    public boolean isSuccess() {
      return succeeded;
    }

    @Override
    public void reset() {
      latch = new CountDownLatch(1);
      retry = false;
      succeeded = false;
    }
  }
}
