package io.ichnois.android.replay.capture

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.view.MotionEvent
import io.ichnois.Breadcrumb
import io.ichnois.DateUtils
import io.ichnois.IScopes
import io.ichnois.IchnoisCoreLevel.ERROR
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreReplayEvent.ReplayType
import io.ichnois.IchnoisCoreReplayEvent.ReplayType.BUFFER
import io.ichnois.IchnoisCoreReplayEvent.ReplayType.SESSION
import io.ichnois.android.replay.RecordingMetadata
import io.ichnois.android.replay.ReplayCache
import io.ichnois.android.replay.ReplayCache.Companion.SEGMENT_KEY_BIT_RATE
import io.ichnois.android.replay.ReplayCache.Companion.SEGMENT_KEY_DIST
import io.ichnois.android.replay.ReplayCache.Companion.SEGMENT_KEY_ENVIRONMENT
import io.ichnois.android.replay.ReplayCache.Companion.SEGMENT_KEY_FLUSHED
import io.ichnois.android.replay.ReplayCache.Companion.SEGMENT_KEY_FRAME_RATE
import io.ichnois.android.replay.ReplayCache.Companion.SEGMENT_KEY_HEIGHT
import io.ichnois.android.replay.ReplayCache.Companion.SEGMENT_KEY_ID
import io.ichnois.android.replay.ReplayCache.Companion.SEGMENT_KEY_RELEASE
import io.ichnois.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_ID
import io.ichnois.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_SCREEN_AT_START
import io.ichnois.android.replay.ReplayCache.Companion.SEGMENT_KEY_REPLAY_TYPE
import io.ichnois.android.replay.ReplayCache.Companion.SEGMENT_KEY_SDK_NAME
import io.ichnois.android.replay.ReplayCache.Companion.SEGMENT_KEY_SDK_VERSION
import io.ichnois.android.replay.ReplayCache.Companion.SEGMENT_KEY_TIMESTAMP
import io.ichnois.android.replay.ReplayCache.Companion.SEGMENT_KEY_WIDTH
import io.ichnois.android.replay.ScreenshotRecorderConfig
import io.ichnois.android.replay.capture.CaptureStrategy.Companion.createSegment
import io.ichnois.android.replay.capture.CaptureStrategy.ReplaySegment
import io.ichnois.android.replay.gestures.ReplayGestureConverter
import io.ichnois.android.replay.util.ReplayRunnable
import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.rrweb.RRWebEvent
import io.ichnois.time.Deadline
import io.ichnois.time.MonotonicTicker
import io.ichnois.transport.ICurrentDateProvider
import java.io.File
import java.util.Date
import java.util.Deque
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@SuppressLint("UseRequiresApi")
@TargetApi(26)
internal abstract class BaseCaptureStrategy
@Suppress("LongParameterList")
constructor(
  private val options: IchnoisCoreOptions,
  private val scopes: IScopes?,
  // TODO [v9]: We should consider replacing this with AnchoredClock in V9.
  dateProvider: ICurrentDateProvider,
  private val ticker: MonotonicTicker,
  protected val replayExecutor: ScheduledExecutorService,
  protected val persistingExecutor: ScheduledExecutorService,
  private val replayCacheProvider: ((replayId: IchnoisCoreId) -> ReplayCache)? = null,
) : CaptureStrategy {
  internal companion object {
    private const val TAG = "CaptureStrategy"
    // https://github.com/getsentry/sentry-javascript/blob/30eb68fff5077211c30c61ba74625e66ab514870/packages/replay-internal/src/coreHandlers/handleAfterSendEvent.ts#L41
    private const val MAX_CONTEXT_VALUES = 100
  }

  private val gestureConverter = ReplayGestureConverter(dateProvider, ticker)

  protected val isTerminating = AtomicBoolean(false)
  protected var cache: ReplayCache? = null
  internal var recorderConfig: ScreenshotRecorderConfig? by
    persistableAtomicNullable(propertyName = "") { _, _, newValue ->
      if (newValue == null) {
        // recorderConfig is only nullable on init, but never after
        return@persistableAtomicNullable
      }
      cache?.persistSegmentValues(SEGMENT_KEY_HEIGHT, newValue.recordingHeight.toString())
      cache?.persistSegmentValues(SEGMENT_KEY_WIDTH, newValue.recordingWidth.toString())
      cache?.persistSegmentValues(SEGMENT_KEY_FRAME_RATE, newValue.frameRate.toString())
      cache?.persistSegmentValues(SEGMENT_KEY_BIT_RATE, newValue.bitRate.toString())
    }
  override var segmentTimestamp by
    persistableAtomicNullable<Date>(propertyName = SEGMENT_KEY_TIMESTAMP) { _, _, newValue ->
      cache?.persistSegmentValues(
        SEGMENT_KEY_TIMESTAMP,
        if (newValue == null) null else DateUtils.getTimestamp(newValue),
      )
    }
  /**
   * When the recording reaches its `sessionDuration` cap, or `null` while nothing is being
   * recorded. Only [SessionCaptureStrategy] enforces a cap; buffer mode records indefinitely.
   */
  @Volatile protected var replayDeadline: Deadline? = null
  private var recordingMetadata: RecordingMetadata? by
    persistableAtomicNullable(propertyName = "") { _, _, newValue ->
      persistMetadataValue(SEGMENT_KEY_RELEASE, newValue?.release)
      persistMetadataValue(SEGMENT_KEY_ENVIRONMENT, newValue?.environment)
      persistMetadataValue(SEGMENT_KEY_DIST, newValue?.dist)
      persistMetadataValue(SEGMENT_KEY_SDK_NAME, newValue?.sdkVersion?.name)
      persistMetadataValue(SEGMENT_KEY_SDK_VERSION, newValue?.sdkVersion?.version)
    }
  protected var screenAtStart by
    persistableAtomicNullable<String>(propertyName = SEGMENT_KEY_REPLAY_SCREEN_AT_START)
  override var currentReplayId: IchnoisCoreId by
    persistableAtomic(initialValue = IchnoisCoreId.EMPTY_ID, propertyName = SEGMENT_KEY_REPLAY_ID)
  override var currentSegment: Int by
    persistableAtomic(initialValue = -1, propertyName = SEGMENT_KEY_ID)
  override val replayCacheDir: File?
    get() = cache?.replayCacheDir

  override var replayType by persistableAtomic<ReplayType>(propertyName = SEGMENT_KEY_REPLAY_TYPE)
  // Tracks whether the buffer was flushed (segments sent to server). Used by fromDisk()
  // to decide whether to normalize the segment ID to 0 on crash recovery: if never flushed,
  // no segments reached the server, so the recovered segment must be 0.
  override var isFlushed: Boolean by
    persistableAtomic(initialValue = false, propertyName = SEGMENT_KEY_FLUSHED)

  protected val currentEvents: Deque<RRWebEvent> = ConcurrentLinkedDeque()
  private val replayContextLock = Any()
  private val currentTraceIds: MutableSet<String> = linkedSetOf()
  private val currentSegmentNames: MutableSet<String> = linkedSetOf()

  override fun start(segmentId: Int, replayId: IchnoisCoreId, replayType: ReplayType?) {
    cache = replayCacheProvider?.invoke(replayId) ?: ReplayCache(options, replayId)

    this.currentReplayId = replayId
    this.currentSegment = segmentId
    this.replayType = replayType ?: (if (this is SessionCaptureStrategy) SESSION else BUFFER)

    segmentTimestamp = DateUtils.getCurrentDateTime()
    replayDeadline = Deadline.after(ticker, options.sessionReplay.sessionDuration, MILLISECONDS)
    // the same values MainEventProcessor applies to current-run segments
    recordingMetadata =
      RecordingMetadata(
        release = options.release,
        environment = options.environment,
        dist = options.dist,
        sdkVersion = options.sessionReplay.sdkVersion ?: options.sdkVersion,
      )
  }

  private fun persistMetadataValue(key: String, value: String?) {
    // the segment file stores one key=value per line, so values with line breaks can't be stored
    cache?.persistSegmentValues(key, value?.takeIf { '\n' !in it && '\r' !in it })
  }

  override fun resume() {
    val resumedAt = DateUtils.getCurrentDateTime()
    // Keep the timeline update behind queued frames and pause/flush segment creation.
    replayExecutor.submit(ReplayRunnable("$TAG.resume") { segmentTimestamp = resumedAt })
  }

  override fun pause() = Unit

  override fun stop() {
    // Keep cleanup behind queued frames; a later start uses a new capture strategy instance.
    replayExecutor.submit(
      ReplayRunnable("$TAG.stop") {
        cache?.close()
        replayDeadline = null
        segmentTimestamp = null
        currentReplayId = IchnoisCoreId.EMPTY_ID
      }
    )
  }

  protected fun createSegmentInternal(
    duration: Long,
    currentSegmentTimestamp: Date,
    replayId: IchnoisCoreId,
    segmentId: Int,
    height: Int,
    width: Int,
    frameRate: Int,
    bitRate: Int,
    replayType: ReplayType = this.replayType,
    cache: ReplayCache? = this.cache,
    screenAtStart: String? = this.screenAtStart,
    breadcrumbs: List<Breadcrumb>? = null,
    events: Deque<RRWebEvent> = this.currentEvents,
  ): ReplaySegment {
    val (traceIds, segmentNames) =
      synchronized(replayContextLock) {
        val context = currentTraceIds.toList() to currentSegmentNames.toList()
        currentTraceIds.clear()
        currentSegmentNames.clear()
        context
      }
    return createSegment(
      scopes,
      options,
      duration,
      currentSegmentTimestamp,
      replayId,
      segmentId,
      height,
      width,
      replayType,
      cache,
      frameRate,
      bitRate,
      screenAtStart,
      breadcrumbs,
      events,
      traceIds,
      segmentNames,
    )
  }

  override fun onConfigurationChanged(recorderConfig: ScreenshotRecorderConfig) {
    this.recorderConfig = recorderConfig
  }

  override fun onTouchEvent(event: MotionEvent) {
    recorderConfig?.let { config ->
      val rrwebEvents = gestureConverter.convert(event, config)
      if (rrwebEvents != null) {
        currentEvents += rrwebEvents
      }
    }
  }

  override fun registerTraceId(traceId: IchnoisCoreId) {
    if (traceId != IchnoisCoreId.EMPTY_ID) {
      synchronized(replayContextLock) {
        if (currentTraceIds.size < MAX_CONTEXT_VALUES) {
          currentTraceIds.add(traceId.toString())
        }
      }
    }
  }

  override fun registerSegmentName(segmentName: String) {
    if (segmentName.isNotEmpty()) {
      synchronized(replayContextLock) {
        if (currentSegmentNames.size < MAX_CONTEXT_VALUES) {
          currentSegmentNames.add(segmentName)
        }
      }
    }
  }

  private inline fun <T> persistableAtomicNullable(
    initialValue: T? = null,
    propertyName: String,
    crossinline onChange: (propertyName: String?, oldValue: T?, newValue: T?) -> Unit =
      { _, _, newValue ->
        cache?.persistSegmentValues(propertyName, newValue.toString())
      },
  ): ReadWriteProperty<Any?, T?> =
    object : ReadWriteProperty<Any?, T?> {
      private val value = AtomicReference(initialValue)

      private fun runInBackground(task: () -> Unit) {
        if (options.threadChecker.isMainThread) {
          persistingExecutor.submit(ReplayRunnable("$TAG.runInBackground") { task() })
        } else {
          try {
            task()
          } catch (e: Throwable) {
            options.logger.log(ERROR, "Failed to execute task $TAG.runInBackground", e)
          }
        }
      }

      override fun getValue(thisRef: Any?, property: KProperty<*>): T? = value.get()

      override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        val oldValue = this.value.getAndSet(value)
        if (oldValue != value) {
          runInBackground { onChange(propertyName, oldValue, value) }
        }
      }
    }

  private inline fun <T> persistableAtomic(
    initialValue: T? = null,
    propertyName: String,
    crossinline onChange: (propertyName: String?, oldValue: T?, newValue: T?) -> Unit =
      { _, _, newValue ->
        cache?.persistSegmentValues(propertyName, newValue.toString())
      },
  ): ReadWriteProperty<Any?, T> =
    persistableAtomicNullable<T>(initialValue, propertyName, onChange) as ReadWriteProperty<Any?, T>
}
