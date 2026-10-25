package io.ichnois.cache

import com.google.common.truth.Truth.assertThat
import io.ichnois.DateUtils
import io.ichnois.Hint
import io.ichnois.ILogger
import io.ichnois.ISerializer
import io.ichnois.NoOpLogger
import io.ichnois.IchnoisCoreCrashLastRunState
import io.ichnois.IchnoisCoreEnvelope
import io.ichnois.IchnoisCoreEvent
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreUUID
import io.ichnois.Session
import io.ichnois.Session.State
import io.ichnois.Session.State.Ok
import io.ichnois.UncaughtExceptionHandlerIntegration.UncaughtExceptionHint
import io.ichnois.cache.EnvelopeCache.PREFIX_CURRENT_SESSION_FILE
import io.ichnois.cache.EnvelopeCache.SUFFIX_SESSION_FILE
import io.ichnois.hints.AbnormalExit
import io.ichnois.hints.NativeCrashExit
import io.ichnois.hints.SessionEndHint
import io.ichnois.hints.SessionStartHint
import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.util.HintUtils
import java.io.File
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EnvelopeCacheTest {
  private class Fixture {
    val dir: Path = Files.createTempDirectory("ichnois-session-cache-test")
    val options = IchnoisCoreOptions()
    val logger = mock<ILogger>()

    fun getSUT(optionsCallback: ((IchnoisCoreOptions) -> Unit)? = null): EnvelopeCache {
      options.cacheDirPath = dir.toAbsolutePath().toFile().absolutePath

      options.setLogger(logger)
      options.setDebug(true)

      optionsCallback?.invoke(options)

      return EnvelopeCache.create(options) as EnvelopeCache
    }
  }

  private val fixture = Fixture()

  @BeforeTest
  fun `set up`() {
    IchnoisCoreCrashLastRunState.getInstance().reset()
  }

  @Test
  fun `stores envelopes`() {
    val cache = fixture.getSUT()

    val file = File(fixture.options.cacheDirPath!!)
    val nofFiles = { file.list()?.size }

    assertEquals(0, nofFiles())

    cache.store(IchnoisCoreEnvelope.from(fixture.options.serializer, createSession(), null))

    assertEquals(1, nofFiles())

    file.deleteRecursively()
  }

  @Test
  fun `creates cache dir on store when it does not exist yet`() {
    val cache = fixture.getSUT()

    val file = File(fixture.options.cacheDirPath!!)
    assertTrue(file.deleteRecursively())
    assertFalse(file.exists())

    cache.store(IchnoisCoreEnvelope.from(fixture.options.serializer, createSession(), null))

    assertTrue(file.exists())
    assertEquals(1, file.list()?.size)

    file.deleteRecursively()
  }

  @Test
  fun `tolerates discarding unknown envelope`() {
    val cache = fixture.getSUT()

    cache.discard(IchnoisCoreEnvelope.from(fixture.options.serializer, createSession(), null))

    // no exception thrown
  }

  @Test
  fun `does not create cache dir on discard`() {
    val cache = fixture.getSUT()

    val file = File(fixture.options.cacheDirPath!!)
    assertTrue(file.deleteRecursively())
    assertFalse(file.exists())

    cache.discard(IchnoisCoreEnvelope.from(fixture.options.serializer, createSession(), null))

    assertFalse(file.exists())
  }

  @Test
  fun `creates current file on session start`() {
    val cache = fixture.getSUT()

    val file = File(fixture.options.cacheDirPath!!)

    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, createSession(), null)

    val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
    val didStore = cache.storeEnvelope(envelope, hints)

    val currentFile =
      File(fixture.options.cacheDirPath!!, "$PREFIX_CURRENT_SESSION_FILE$SUFFIX_SESSION_FILE")
    assertTrue(currentFile.exists())

    file.deleteRecursively()

    assertTrue(didStore)
  }

  @Test
  fun `deletes current file on session end`() {
    val cache = fixture.getSUT()

    val file = File(fixture.options.cacheDirPath!!)

    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, createSession(), null)

    val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
    val didStore = cache.storeEnvelope(envelope, hints)

    val currentFile =
      File(fixture.options.cacheDirPath!!, "$PREFIX_CURRENT_SESSION_FILE$SUFFIX_SESSION_FILE")
    assertTrue(currentFile.exists())

    HintUtils.setTypeCheckHint(hints, SessionEndHint())
    cache.store(envelope, hints)
    assertFalse(currentFile.exists())

    file.deleteRecursively()
    assertTrue(didStore)
  }

  @Test
  fun `delayed same SID SessionStart preserves newer unhandled snapshot`() {
    val cache = fixture.getSUT()
    val sid = IchnoisCoreUUID.generateIchnoisCoreId()
    val currentSessionFile = EnvelopeCache.getCurrentSessionFile(fixture.options.cacheDirPath!!)
    val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)
    val newerSession = createSession(sessionId = sid)
    newerSession.recordNonTerminatingUnhandledError()
    cache.persistCurrentSession(newerSession)

    val delayedStart = createSession(sessionId = sid)
    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, delayedStart, null)
    cache.storeEnvelope(envelope, HintUtils.createWithTypeCheckHint(SessionStartHint()))

    val persistedSession =
      fixture.options.serializer.deserialize(
        currentSessionFile.bufferedReader(),
        Session::class.java,
      )!!
    assertThat(persistedSession.sessionId).isEqualTo(sid)
    assertThat(persistedSession.hasNonTerminatingUnhandledError()).isTrue()
    assertThat(persistedSession.errorCount()).isEqualTo(1)
    assertThat(previousSessionFile.exists()).isFalse()
  }

  @Test
  fun `delayed same SID SessionStart preserves newer error count snapshot`() {
    val cache = fixture.getSUT()
    val sid = IchnoisCoreUUID.generateIchnoisCoreId()
    val currentSessionFile = EnvelopeCache.getCurrentSessionFile(fixture.options.cacheDirPath!!)
    val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)
    val newerSession = createSession(sessionId = sid)
    newerSession.update(null, null, true)
    cache.persistCurrentSession(newerSession)

    val delayedStart = createSession(sessionId = sid)
    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, delayedStart, null)
    cache.storeEnvelope(envelope, HintUtils.createWithTypeCheckHint(SessionStartHint()))

    val persistedSession =
      fixture.options.serializer.deserialize(
        currentSessionFile.bufferedReader(),
        Session::class.java,
      )!!
    assertThat(persistedSession.sessionId).isEqualTo(sid)
    assertThat(persistedSession.hasNonTerminatingUnhandledError()).isFalse()
    assertThat(persistedSession.errorCount()).isEqualTo(1)
    assertThat(previousSessionFile.exists()).isFalse()
  }

  @Test
  fun `failed persist stops the delayed same SID SessionStart from being skipped`() {
    val cache = fixture.getSUT()
    val sid = IchnoisCoreUUID.generateIchnoisCoreId()
    val currentSessionFile = EnvelopeCache.getCurrentSessionFile(fixture.options.cacheDirPath!!)
    val newerSession = createSession(sessionId = sid)
    newerSession.recordNonTerminatingUnhandledError()
    cache.persistCurrentSession(newerSession)

    // a directory where the session file belongs makes the write fail
    assertTrue(currentSessionFile.delete())
    assertTrue(currentSessionFile.mkdir())
    cache.persistCurrentSession(newerSession)
    assertTrue(currentSessionFile.delete())

    val delayedStart = createSession(sessionId = sid)
    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, delayedStart, null)
    cache.storeEnvelope(envelope, HintUtils.createWithTypeCheckHint(SessionStartHint()))

    val persistedSession =
      fixture.options.serializer.deserialize(
        currentSessionFile.bufferedReader(),
        Session::class.java,
      )!!
    assertThat(persistedSession.sessionId).isEqualTo(sid)
    assertThat(persistedSession.hasNonTerminatingUnhandledError()).isFalse()
    assertThat(persistedSession.errorCount()).isEqualTo(0)
  }

  @Test
  fun `null SIDs on SessionStart rotate instead of preserving as same session`() {
    val cache = fixture.getSUT()
    val currentSession = createSession(sessionId = null)
    currentSession.update(null, null, true)
    cache.persistCurrentSession(currentSession)
    val startingSession = createSession(sessionId = null)

    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, startingSession, null)
    cache.storeEnvelope(envelope, HintUtils.createWithTypeCheckHint(SessionStartHint()))

    val currentSessionFile = EnvelopeCache.getCurrentSessionFile(fixture.options.cacheDirPath!!)
    val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)
    val persistedCurrent =
      fixture.options.serializer.deserialize(
        currentSessionFile.bufferedReader(),
        Session::class.java,
      )!!
    val persistedPrevious =
      fixture.options.serializer.deserialize(
        previousSessionFile.bufferedReader(),
        Session::class.java,
      )!!
    assertThat(persistedCurrent.sessionId).isNull()
    assertThat(persistedCurrent.errorCount()).isEqualTo(0)
    assertThat(persistedPrevious.sessionId).isNull()
    assertThat(persistedPrevious.errorCount()).isEqualTo(1)
  }

  @Test
  fun `different SID SessionStart rotates current session`() {
    val cache = fixture.getSUT()
    val currentSession = createSession()
    cache.persistCurrentSession(currentSession)
    val nextSession = createSession()

    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, nextSession, null)
    cache.storeEnvelope(envelope, HintUtils.createWithTypeCheckHint(SessionStartHint()))

    val currentSessionFile = EnvelopeCache.getCurrentSessionFile(fixture.options.cacheDirPath!!)
    val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)
    val persistedCurrent =
      fixture.options.serializer.deserialize(
        currentSessionFile.bufferedReader(),
        Session::class.java,
      )!!
    val persistedPrevious =
      fixture.options.serializer.deserialize(
        previousSessionFile.bufferedReader(),
        Session::class.java,
      )!!
    assertThat(persistedCurrent.sessionId).isEqualTo(nextSession.sessionId)
    assertThat(persistedPrevious.sessionId).isEqualTo(currentSession.sessionId)
  }

  @Test
  fun `SessionEnd deleting the persisted session lets the delayed SessionStart write it again`() {
    val cache = fixture.getSUT()
    val sid = IchnoisCoreUUID.generateIchnoisCoreId()
    val currentSessionFile = EnvelopeCache.getCurrentSessionFile(fixture.options.cacheDirPath!!)
    cache.persistCurrentSession(createSession(sessionId = sid))

    // the previous session's end envelope is still queued and deletes the file the live session
    // was just written to
    val endedSession = createSession()
    cache.storeEnvelope(
      IchnoisCoreEnvelope.from(fixture.options.serializer, endedSession, null),
      HintUtils.createWithTypeCheckHint(SessionEndHint()),
    )
    assertThat(currentSessionFile.exists()).isFalse()

    val delayedStart = createSession(sessionId = sid)
    cache.storeEnvelope(
      IchnoisCoreEnvelope.from(fixture.options.serializer, delayedStart, null),
      HintUtils.createWithTypeCheckHint(SessionStartHint()),
    )

    val persistedSession =
      fixture.options.serializer.deserialize(
        currentSessionFile.bufferedReader(),
        Session::class.java,
      )!!
    assertThat(persistedSession.sessionId).isEqualTo(sid)
  }

  @Test
  fun `failed persist lets the delayed SessionStart write the session`() {
    val sid = IchnoisCoreUUID.generateIchnoisCoreId()
    val liveSession = createSession(sessionId = sid)
    val delayedStart = createSession(sessionId = sid)
    val serializer = mock<ISerializer>()
    whenever(serializer.serialize(same(liveSession), any<Writer>()))
      .thenThrow(RuntimeException("forced ex"))
    whenever(serializer.deserialize(any(), eq(Session::class.java))).thenReturn(delayedStart)
    val cache = fixture.getSUT { options -> options.setSerializer(serializer) }

    cache.persistCurrentSession(liveSession)

    val envelope = IchnoisCoreEnvelope.from(IchnoisCoreOptions.empty().serializer, delayedStart, null)
    cache.storeEnvelope(envelope, HintUtils.createWithTypeCheckHint(SessionStartHint()))

    verify(serializer).serialize(same(delayedStart), any<Writer>())
  }

  @Test
  fun `updates current file on session update and read it back`() {
    val cache = fixture.getSUT()

    val file = File(fixture.options.cacheDirPath!!)

    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, createSession(), null)

    val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
    val didStore = cache.storeEnvelope(envelope, hints)

    val currentFile =
      File(fixture.options.cacheDirPath!!, "$PREFIX_CURRENT_SESSION_FILE$SUFFIX_SESSION_FILE")
    assertTrue(currentFile.exists())

    val session =
      fixture.options.serializer.deserialize(
        currentFile.bufferedReader(Charsets.UTF_8),
        Session::class.java,
      )
    assertNotNull(session)

    currentFile.delete()

    file.deleteRecursively()
    assertTrue(didStore)
  }

  @Test
  fun `when native crash marker file exist, mark isCrashedLastRun`() {
    val cache = fixture.getSUT()

    val file = File(fixture.options.cacheDirPath!!)
    val markerFile = File(fixture.options.cacheDirPath!!, EnvelopeCache.NATIVE_CRASH_MARKER_FILE)
    markerFile.mkdirs()
    assertTrue(markerFile.exists())

    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, createSession(), null)

    val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
    val didStore = cache.storeEnvelope(envelope, hints)

    val newEnvelope = IchnoisCoreEnvelope.from(fixture.options.serializer, createSession(), null)

    // since the first store call would set as readCrashedLastRun=true
    IchnoisCoreCrashLastRunState.getInstance().reset()

    cache.store(newEnvelope, hints)
    file.deleteRecursively()

    // passing empty string since readCrashedLastRun is already set
    assertTrue(IchnoisCoreCrashLastRunState.getInstance().isCrashedLastRun("", false)!!)
    assertTrue(didStore)
  }

  @Test
  fun `when java crash marker file exist, mark isCrashedLastRun`() {
    val cache = fixture.getSUT()

    val markerFile = File(fixture.options.cacheDirPath!!, EnvelopeCache.CRASH_MARKER_FILE)
    markerFile.mkdirs()
    assertTrue(markerFile.exists())

    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, createSession(), null)

    val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
    val didStore = cache.storeEnvelope(envelope, hints)

    // passing empty string since readCrashedLastRun is already set
    assertTrue(IchnoisCoreCrashLastRunState.getInstance().isCrashedLastRun("", false)!!)
    assertFalse(markerFile.exists())
    assertTrue(didStore)
  }

  @Test
  fun `write java marker file to disk when uncaught exception hint`() {
    val cache = fixture.getSUT()

    val markerFile = File(fixture.options.cacheDirPath!!, EnvelopeCache.CRASH_MARKER_FILE)
    assertFalse(markerFile.exists())

    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null)

    val hints =
      HintUtils.createWithTypeCheckHint(UncaughtExceptionHint(0, NoOpLogger.getInstance()))
    val didStore = cache.storeEnvelope(envelope, hints)

    assertTrue(markerFile.exists())
    assertTrue(didStore)
  }

  @Test
  fun `store with StartSession hint flushes previous session`() {
    val cache = fixture.getSUT()

    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null)
    val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
    cache.storeEnvelope(envelope, hints)

    assertTrue(cache.waitPreviousSessionFlush())
  }

  @Test
  fun `SessionStart hint saves unfinished session to previous_session file`() {
    val cache = fixture.getSUT()

    val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)
    val currentSessionFile = EnvelopeCache.getCurrentSessionFile(fixture.options.cacheDirPath!!)
    val session = createSession()
    fixture.options.serializer.serialize(session, currentSessionFile.bufferedWriter())

    assertFalse(previousSessionFile.exists())

    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null)
    val hints = HintUtils.createWithTypeCheckHint(SessionStartHint())
    cache.storeEnvelope(envelope, hints)

    assertTrue(previousSessionFile.exists())
    val persistedSession =
      fixture.options.serializer.deserialize(
        previousSessionFile.bufferedReader(),
        Session::class.java,
      )
    assertEquals("dis", persistedSession!!.distinctId)
  }

  @Test
  fun `AbnormalExit hint marks previous session as abnormal with abnormal mechanism and current timestamp`() {
    val cache = fixture.getSUT()

    val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)
    val session = createSession()
    fixture.options.serializer.serialize(session, previousSessionFile.bufferedWriter())

    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null)
    val abnormalHint =
      object : AbnormalExit {
        override fun mechanism(): String? = "abnormal_mechanism"

        override fun ignoreCurrentThread(): Boolean = false

        override fun timestamp(): Long? = null
      }
    val hints = HintUtils.createWithTypeCheckHint(abnormalHint)
    cache.storeEnvelope(envelope, hints)

    val updatedSession =
      fixture.options.serializer.deserialize(
        previousSessionFile.bufferedReader(),
        Session::class.java,
      )
    assertEquals(State.Abnormal, updatedSession!!.status)
    assertEquals("abnormal_mechanism", updatedSession.abnormalMechanism)
    assertTrue { updatedSession.timestamp!!.time - DateUtils.getCurrentDateTime().time < 1000 }
  }

  @Test
  fun `previous session uses AbnormalExit hint timestamp when available`() {
    val cache = fixture.getSUT()

    val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)
    val sessionStarted = Date(2023, 10, 1)
    val sessionExitedWithAbnormal = sessionStarted.time + TimeUnit.HOURS.toMillis(3)
    val session = createSession(sessionStarted)
    fixture.options.serializer.serialize(session, previousSessionFile.bufferedWriter())

    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null)
    val abnormalHint =
      object : AbnormalExit {
        override fun mechanism(): String = "abnormal_mechanism"

        override fun ignoreCurrentThread(): Boolean = false

        override fun timestamp(): Long = sessionExitedWithAbnormal
      }
    val hints = HintUtils.createWithTypeCheckHint(abnormalHint)
    cache.storeEnvelope(envelope, hints)

    val updatedSession =
      fixture.options.serializer.deserialize(
        previousSessionFile.bufferedReader(),
        Session::class.java,
      )
    assertEquals(sessionExitedWithAbnormal, updatedSession!!.timestamp!!.time)
  }

  @Test
  fun `AbnormalExit hint keeps persisted unhandled session as abnormal`() {
    val cache = fixture.getSUT()

    val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)
    val session = createSession().apply { recordNonTerminatingUnhandledError() }
    fixture.options.serializer.serialize(session, previousSessionFile.bufferedWriter())

    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null)
    val abnormalHint =
      object : AbnormalExit {
        override fun mechanism(): String = "abnormal_mechanism"

        override fun ignoreCurrentThread(): Boolean = false

        override fun timestamp(): Long = session.started!!.time + TimeUnit.HOURS.toMillis(1)
      }
    val hints = HintUtils.createWithTypeCheckHint(abnormalHint)
    cache.storeEnvelope(envelope, hints)

    val updatedSession =
      fixture.options.serializer.deserialize(
        previousSessionFile.bufferedReader(),
        Session::class.java,
      )
    assertEquals(State.Abnormal, updatedSession!!.status)
    assertEquals("abnormal_mechanism", updatedSession.abnormalMechanism)
    assertFalse(updatedSession.hasNonTerminatingUnhandledError())
  }

  @Test
  fun `when AbnormalExit happened before previous session start, does not mark as abnormal`() {
    val cache = fixture.getSUT()

    val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)
    val sessionStarted = Date(2023, 10, 1)
    val sessionExitedWithAbnormal = sessionStarted.time - TimeUnit.HOURS.toMillis(3)
    val session = createSession(sessionStarted)
    fixture.options.serializer.serialize(session, previousSessionFile.bufferedWriter())

    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null)
    val abnormalHint =
      object : AbnormalExit {
        override fun mechanism(): String = "abnormal_mechanism"

        override fun ignoreCurrentThread(): Boolean = false

        override fun timestamp(): Long = sessionExitedWithAbnormal
      }
    val hints = HintUtils.createWithTypeCheckHint(abnormalHint)
    cache.storeEnvelope(envelope, hints)

    val updatedSession =
      fixture.options.serializer.deserialize(
        previousSessionFile.bufferedReader(),
        Session::class.java,
      )
    assertEquals(Ok, updatedSession!!.status)
    assertEquals(null, updatedSession.abnormalMechanism)
  }

  @Test
  fun `NativeCrashExit hint marks previous session as crashed with crash timestamp`() {
    val cache = fixture.getSUT()

    val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)
    val session = createSession()
    fixture.options.serializer.serialize(session, previousSessionFile.bufferedWriter())

    val nativeCrashTimestamp = session.started!!.time + TimeUnit.HOURS.toMillis(3)
    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null)
    val nativeCrashHint = NativeCrashExit { nativeCrashTimestamp }
    val hints = HintUtils.createWithTypeCheckHint(nativeCrashHint)
    cache.storeEnvelope(envelope, hints)

    val updatedSession =
      fixture.options.serializer.deserialize(
        previousSessionFile.bufferedReader(),
        Session::class.java,
      )
    assertEquals(State.Crashed, updatedSession!!.status)
    assertEquals(nativeCrashTimestamp, updatedSession.timestamp!!.time)
  }

  @Test
  fun `NativeCrashExit hint keeps persisted unhandled session as crashed`() {
    val cache = fixture.getSUT()

    val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)
    val session = createSession().apply { recordNonTerminatingUnhandledError() }
    fixture.options.serializer.serialize(session, previousSessionFile.bufferedWriter())

    val nativeCrashTimestamp = session.started!!.time + TimeUnit.HOURS.toMillis(1)
    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null)
    val hints = HintUtils.createWithTypeCheckHint(NativeCrashExit { nativeCrashTimestamp })
    cache.storeEnvelope(envelope, hints)

    val updatedSession =
      fixture.options.serializer.deserialize(
        previousSessionFile.bufferedReader(),
        Session::class.java,
      )
    assertEquals(State.Crashed, updatedSession!!.status)
    assertEquals(nativeCrashTimestamp, updatedSession.timestamp!!.time)
    assertFalse(updatedSession.hasNonTerminatingUnhandledError())
  }

  @Test
  fun `when NativeCrashExit happened before previous session start, does not mark as crashed`() {
    val cache = fixture.getSUT()

    val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)
    val session = createSession()
    val nativeCrashTimestamp = session.started!!.time - TimeUnit.HOURS.toMillis(3)
    fixture.options.serializer.serialize(session, previousSessionFile.bufferedWriter())

    val envelope = IchnoisCoreEnvelope.from(fixture.options.serializer, IchnoisCoreEvent(), null)
    val nativeCrashHint = NativeCrashExit { nativeCrashTimestamp }
    val hints = HintUtils.createWithTypeCheckHint(nativeCrashHint)
    cache.storeEnvelope(envelope, hints)

    val updatedSession =
      fixture.options.serializer.deserialize(
        previousSessionFile.bufferedReader(),
        Session::class.java,
      )
    assertEquals(Ok, updatedSession!!.status)
    assertTrue(nativeCrashTimestamp < updatedSession.started!!.time)
    assertTrue(nativeCrashTimestamp < updatedSession.timestamp!!.time)
  }

  @Test
  fun `failing to store returns false`() {
    val serializer = mock<ISerializer>()
    val envelope = IchnoisCoreEnvelope.from(IchnoisCoreOptions.empty().serializer, createSession(), null)

    whenever(serializer.serialize(same(envelope), any())).thenThrow(RuntimeException("forced ex"))

    val cache = fixture.getSUT { options -> options.setSerializer(serializer) }

    val didStore = cache.storeEnvelope(envelope, Hint())

    assertFalse(didStore)
  }

  private fun createSession(
    started: Date? = null,
    sessionId: String? = IchnoisCoreUUID.generateIchnoisCoreId(),
  ): Session =
    Session(
      Ok,
      started ?: DateUtils.getCurrentDateTime(),
      DateUtils.getCurrentDateTime(),
      0,
      "dis",
      sessionId,
      true,
      null,
      null,
      null,
      null,
      "env",
      "rel",
      null,
    )

  @Test
  fun `two items with the same event id can be stored side-by-side`() {
    val cache = fixture.getSUT()

    val eventId = IchnoisCoreId()

    val envelopeA =
      IchnoisCoreEnvelope.from(
        fixture.options.serializer,
        IchnoisCoreEvent().apply { setEventId(eventId) },
        null,
      )

    val envelopeB =
      IchnoisCoreEnvelope.from(
        fixture.options.serializer,
        IchnoisCoreEvent().apply { setEventId(eventId) },
        null,
      )

    cache.store(envelopeA, Hint())
    cache.store(envelopeB, Hint())

    assertEquals(2, cache.directory.file.list()?.size)
  }

  @Test
  fun `movePreviousSession moves current session file to previous session file`() {
    val cache = fixture.getSUT()

    val currentSessionFile = EnvelopeCache.getCurrentSessionFile(fixture.options.cacheDirPath!!)
    val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)

    // Create a current session file
    currentSessionFile.createNewFile()
    currentSessionFile.writeText("current session content")

    assertTrue(currentSessionFile.exists())
    assertFalse(previousSessionFile.exists())

    // Call movePreviousSession directly
    cache.movePreviousSession(currentSessionFile, previousSessionFile)

    // Current file should be moved to previous
    assertFalse(currentSessionFile.exists())
    assertTrue(previousSessionFile.exists())
    assertEquals("current session content", previousSessionFile.readText())
  }

  @Test
  fun `movePreviousSession does nothing when current session file does not exist`() {
    val cache = fixture.getSUT()

    val currentSessionFile = EnvelopeCache.getCurrentSessionFile(fixture.options.cacheDirPath!!)
    val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)

    assertFalse(currentSessionFile.exists())
    assertFalse(previousSessionFile.exists())

    // Call movePreviousSession when no files exist
    cache.movePreviousSession(currentSessionFile, previousSessionFile)

    // Nothing should happen
    assertFalse(currentSessionFile.exists())
    assertFalse(previousSessionFile.exists())
  }

  @Test
  fun `movePreviousSession is idempotent wrt the previous session`() {
    val cache = fixture.getSUT()

    val currentSessionFile = EnvelopeCache.getCurrentSessionFile(fixture.options.cacheDirPath!!)
    val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)

    // Create a current session file
    currentSessionFile.createNewFile()
    currentSessionFile.writeText("session content from last run")

    assertTrue(currentSessionFile.exists())
    assertFalse(previousSessionFile.exists())

    // First call: moves session.json -> previous_session.json
    cache.movePreviousSession(currentSessionFile, previousSessionFile)

    assertFalse(currentSessionFile.exists())
    assertTrue(previousSessionFile.exists())
    assertEquals("session content from last run", previousSessionFile.readText())

    // Second call should be idempotent: previous_session.json should be preserved
    // This simulates the race where both MovePreviousSession runnable and
    // storeInternal (SessionStart) both call movePreviousSession
    cache.movePreviousSession(currentSessionFile, previousSessionFile)

    // previous_session.json should still exist with original content
    assertTrue(previousSessionFile.exists())
    assertEquals("session content from last run", previousSessionFile.readText())
  }

  @Test
  fun `movePreviousSession deletes file and moves session when previous session file already exists`() {
    val cache = fixture.getSUT()

    val currentSessionFile = EnvelopeCache.getCurrentSessionFile(fixture.options.cacheDirPath!!)
    val previousSessionFile = EnvelopeCache.getPreviousSessionFile(fixture.options.cacheDirPath!!)

    // Create both files
    currentSessionFile.createNewFile()
    currentSessionFile.writeText("current session content")
    previousSessionFile.createNewFile()
    previousSessionFile.writeText("existing previous content")

    assertTrue(currentSessionFile.exists())
    assertTrue(previousSessionFile.exists())

    // Call movePreviousSession when previous already exists
    cache.movePreviousSession(currentSessionFile, previousSessionFile)

    // Current session should be moved to previous
    assertFalse(currentSessionFile.exists())
    assertTrue(previousSessionFile.exists())
    assertEquals("current session content", previousSessionFile.readText())
  }
}
