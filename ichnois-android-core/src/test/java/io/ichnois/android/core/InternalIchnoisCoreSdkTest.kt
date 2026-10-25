package io.ichnois.android.core

import android.app.Application
import android.content.ContentProvider
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.ichnois.Breadcrumb
import io.ichnois.Hint
import io.ichnois.IScope
import io.ichnois.Scope
import io.ichnois.ScopeType
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreEnvelope
import io.ichnois.IchnoisCoreEnvelopeHeader
import io.ichnois.IchnoisCoreEnvelopeItem
import io.ichnois.IchnoisCoreEvent
import io.ichnois.IchnoisCoreExceptionFactory
import io.ichnois.IchnoisCoreItemType
import io.ichnois.IchnoisCoreOptions
import io.ichnois.Session
import io.ichnois.SpanId
import io.ichnois.android.core.performance.ActivityLifecycleTimeSpan
import io.ichnois.android.core.performance.AppStartMetrics
import io.ichnois.cache.EnvelopeCache
import io.ichnois.exception.ExceptionMechanismException
import io.ichnois.protocol.App
import io.ichnois.protocol.Contexts
import io.ichnois.protocol.Mechanism
import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.protocol.User
import io.ichnois.test.createTestScopes
import io.ichnois.transport.ITransport
import io.ichnois.transport.RateLimiter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.awaitility.kotlin.await
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class InternalIchnoisCoreSdkTest {

  private lateinit var context: Context

  class Fixture {
    val capturedEnvelopes = mutableListOf<IchnoisCoreEnvelope>()
    lateinit var options: IchnoisCoreOptions

    fun init(context: Context) {
      initForTest(context) { options ->
        this@Fixture.options = options
        options.dsn = "https://key@host/proj"
        options.setTransportFactory { _, _ ->
          object : ITransport {
            override fun close(isRestarting: Boolean) {
              // no-op
            }

            override fun close() {
              // no-op
            }

            override fun send(envelope: IchnoisCoreEnvelope, hint: Hint) {
              capturedEnvelopes.add(envelope)
            }

            override fun flush(timeoutMillis: Long) {
              // no-op
            }

            override fun getRateLimiter(): RateLimiter? {
              return null
            }
          }
        }
      }

      // IchnoisCore.init moves any leftover session.json to previous_session.json on the executor. Wait
      // for that to finish, otherwise it can move the session a test has just persisted. Test
      // options set sessionFlushTimeoutMillis to 0, so waitPreviousSessionFlush() only polls.
      val cache = options.envelopeDiskCache as EnvelopeCache
      await.until { cache.waitPreviousSessionFlush() }

      capturedEnvelopes.clear()
    }

    fun captureEnvelopeWithEvent(
      event: IchnoisCoreEvent = IchnoisCoreEvent(),
      maybeStartNewSession: Boolean = false,
    ) {
      // create an envelope with session data
      val options = IchnoisCore.getCurrentScopes().options
      val eventId = IchnoisCoreId()
      val header = IchnoisCoreEnvelopeHeader(eventId)
      val eventItem = IchnoisCoreEnvelopeItem.fromEvent(options.serializer, event)

      val envelope = IchnoisCoreEnvelope(header, listOf(eventItem))

      // serialize to byte array
      val outputStream = ByteArrayOutputStream()
      options.serializer.serialize(envelope, outputStream)
      val data = outputStream.toByteArray()

      InternalIchnoisCoreSdk.captureEnvelope(data, maybeStartNewSession)
    }

    fun captureEnvelopeNonTerminatingWithEvent(event: IchnoisCoreEvent = IchnoisCoreEvent()) {
      val options = IchnoisCore.getCurrentScopes().options
      val eventId = IchnoisCoreId()
      val header = IchnoisCoreEnvelopeHeader(eventId)
      val eventItem = IchnoisCoreEnvelopeItem.fromEvent(options.serializer, event)

      val envelope = IchnoisCoreEnvelope(header, listOf(eventItem))

      val outputStream = ByteArrayOutputStream()
      options.serializer.serialize(envelope, outputStream)
      val data = outputStream.toByteArray()

      InternalIchnoisCoreSdk.captureEnvelopeNonTerminating(data)
    }

    fun createIchnoisCoreEventWithUnhandledException(): IchnoisCoreEvent {
      return IchnoisCoreEvent(RuntimeException()).apply {
        val mechanism = Mechanism()
        mechanism.isHandled = false

        val factory = IchnoisCoreExceptionFactory(mock())
        val ichnoisExceptions =
          factory.getIchnoisCoreExceptions(ExceptionMechanismException(mechanism, Throwable(), Thread()))
        exceptions = ichnoisExceptions
      }
    }

    fun createIchnoisCoreEventWithHandledException(): IchnoisCoreEvent {
      return IchnoisCoreEvent(RuntimeException()).apply {
        val mechanism = Mechanism()
        mechanism.isHandled = true

        val factory = IchnoisCoreExceptionFactory(mock())
        exceptions =
          factory.getIchnoisCoreExceptions(ExceptionMechanismException(mechanism, Throwable(), Thread()))
      }
    }

    fun mockFinishedAppStart() {
      val metrics = AppStartMetrics.getInstance()

      metrics.appStartType = AppStartMetrics.AppStartType.WARM

      metrics.appStartTimeSpan.setStartedAt(
        20
      ) // Can't be 0, as that's the default value if not set
      metrics.appStartTimeSpan.setStartUnixTimeMs(
        20
      ) // The order matters, unix time must be set after started at in tests to avoid overwrite
      metrics.appStartTimeSpan.setStoppedAt(200)
      metrics.classLoadedUptimeMs = 100

      AppStartMetrics.onApplicationCreate(mock<Application>())
      metrics.applicationOnCreateTimeSpan.description = "Application created"
      metrics.applicationOnCreateTimeSpan.setStartedAt(
        30
      ) // Can't be 0, as that's the default value if not set
      metrics.applicationOnCreateTimeSpan.setStartUnixTimeMs(
        30
      ) // The order matters, unix time must be set after started at in tests to avoid overwrite
      metrics.applicationOnCreateTimeSpan.setStoppedAt(40)

      val activityLifecycleSpan = ActivityLifecycleTimeSpan()
      activityLifecycleSpan.onCreate.description = "Test Activity Lifecycle onCreate"
      activityLifecycleSpan.onCreate.setStartedAt(
        50
      ) // Can't be 0, as that's the default value if not set
      activityLifecycleSpan.onCreate.setStartUnixTimeMs(
        50
      ) // The order matters, unix time must be set after started at in tests to avoid overwrite
      activityLifecycleSpan.onCreate.setStoppedAt(60)

      activityLifecycleSpan.onStart.description = "Test Activity Lifecycle onStart"
      activityLifecycleSpan.onStart.setStartedAt(
        70
      ) // Can't be 0, as that's the default value if not set
      activityLifecycleSpan.onStart.setStartUnixTimeMs(
        70
      ) // The order matters, unix time must be set after started at in tests to avoid overwrite
      activityLifecycleSpan.onStart.setStoppedAt(80)
      metrics.addActivityLifecycleTimeSpans(activityLifecycleSpan)

      AppStartMetrics.onContentProviderCreate(mock<ContentProvider>())
      metrics.contentProviderOnCreateTimeSpans[0].description = "Test Content Provider created"
      metrics.contentProviderOnCreateTimeSpans[0].setStartedAt(90)
      metrics.contentProviderOnCreateTimeSpans[0].setStartUnixTimeMs(90)
      metrics.contentProviderOnCreateTimeSpans[0].setStoppedAt(100)

      metrics.appStartProfiler = mock()
      metrics.appStartSamplingDecision = mock()
    }

    fun mockMinimumFinishedAppStart() {
      val metrics = AppStartMetrics.getInstance()

      metrics.appStartType = AppStartMetrics.AppStartType.WARM

      metrics.appStartTimeSpan.setStartedAt(
        20
      ) // Can't be 0, as that's the default value if not set
      metrics.appStartTimeSpan.setStartUnixTimeMs(
        20
      ) // The order matters, unix time must be set after started at in tests to avoid overwrite
      metrics.appStartTimeSpan.setStoppedAt(200)
      metrics.classLoadedUptimeMs = 100

      AppStartMetrics.onApplicationCreate(mock<Application>())
      metrics.applicationOnCreateTimeSpan.description = "Application created"
      metrics.applicationOnCreateTimeSpan.setStartedAt(
        30
      ) // Can't be 0, as that's the default value if not set
      metrics.applicationOnCreateTimeSpan.setStartUnixTimeMs(
        30
      ) // The order matters, unix time must be set after started at in tests to avoid overwrite
      metrics.applicationOnCreateTimeSpan.setStoppedAt(40)
    }

    fun mockUnfinishedAppStart() {
      val metrics = AppStartMetrics.getInstance()

      metrics.appStartType = AppStartMetrics.AppStartType.WARM

      metrics.appStartTimeSpan.setStartedAt(
        20
      ) // Can't be 0, as that's the default value if not set
      metrics.appStartTimeSpan.setStartUnixTimeMs(
        20
      ) // The order matters, unix time must be set after started at in tests to avoid overwrite
      metrics.appStartTimeSpan.setStoppedAt(200)
      metrics.classLoadedUptimeMs = 100

      AppStartMetrics.onApplicationCreate(mock<Application>())
      metrics.applicationOnCreateTimeSpan.description = "Application created"
      metrics.applicationOnCreateTimeSpan.setStartedAt(
        30
      ) // Can't be 0, as that's the default value if not set

      val activityLifecycleSpan =
        ActivityLifecycleTimeSpan() // Expect the created spans are not started nor stopped
      activityLifecycleSpan.onCreate.description = "Test Activity Lifecycle onCreate"
      activityLifecycleSpan.onStart.description = "Test Activity Lifecycle onStart"
      metrics.addActivityLifecycleTimeSpans(activityLifecycleSpan)
    }
  }

  @BeforeTest
  fun `set up`() {
    IchnoisCore.close()
    context = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `current scope returns null when scopes is no-op`() {
    IchnoisCore.setCurrentScopes(createTestScopes(enabled = false))
    val scope = InternalIchnoisCoreSdk.getCurrentScope()
    assertNull(scope)
  }

  @Test
  fun `current scope returns obj when scopes is active`() {
    val fixture = Fixture()
    fixture.init(context)
    val scope = InternalIchnoisCoreSdk.getCurrentScope()
    assertNotNull(scope)
  }

  @Test
  fun `current scope returns a copy of the scope`() {
    val fixture = Fixture()
    fixture.init(context)
    IchnoisCore.addBreadcrumb("test")
    IchnoisCore.configureScope(ScopeType.CURRENT) { scope ->
      scope.addBreadcrumb(Breadcrumb("currentBreadcrumb"))
    }
    IchnoisCore.configureScope(ScopeType.ISOLATION) { scope ->
      scope.addBreadcrumb(Breadcrumb("isolationBreadcrumb"))
    }
    IchnoisCore.configureScope(ScopeType.GLOBAL) { scope ->
      scope.addBreadcrumb(Breadcrumb("globalBreadcrumb"))
    }

    // when the clone is modified
    val clonedScope = InternalIchnoisCoreSdk.getCurrentScope()!!
    clonedScope.clearBreadcrumbs()

    // then modifications should not be reflected
    IchnoisCore.configureScope { scope -> assertEquals(3, scope.breadcrumbs.size) }

    // Ensure we don't interfere with other tests
    IchnoisCore.configureScope(ScopeType.GLOBAL) { scope -> scope.clear() }
  }

  @Test
  fun `serializeScope correctly creates top level map`() {
    val options = IchnoisCoreAndroidOptions()
    val scope = Scope(options)

    scope.user = User().apply { name = "John" }
    scope.addBreadcrumb(Breadcrumb.ui("ui.click", "button_login"))
    scope.contexts.setApp(App().apply { appName = "Example App" })
    scope.setTag("variant", "yellow")

    val serializedScope = InternalIchnoisCoreSdk.serializeScope(context, options, scope)

    assertTrue(serializedScope.containsKey("user"))
    assertTrue(serializedScope.containsKey("contexts"))
    assertTrue((serializedScope["contexts"] as Map<*, *>).containsKey("device"))

    assertTrue(serializedScope.containsKey("tags"))
    assertTrue(serializedScope.containsKey("extras"))
    assertTrue(serializedScope.containsKey("fingerprint"))
    assertTrue(serializedScope.containsKey("level"))
    assertTrue(serializedScope.containsKey("breadcrumbs"))
  }

  @Test
  fun `serializeScope returns empty map in case scope is null`() {
    val options = IchnoisCoreAndroidOptions()
    val serializedScope = InternalIchnoisCoreSdk.serializeScope(context, options, null)
    assertTrue(serializedScope.isEmpty())
  }

  @Test
  fun `serializeScope returns empty map in case scope serialization fails`() {
    val options = IchnoisCoreAndroidOptions()
    val scope = mock<IScope>()

    whenever(scope.contexts).thenReturn(Contexts())
    whenever(scope.user).thenThrow(IllegalStateException("something is off"))

    val serializedScope = InternalIchnoisCoreSdk.serializeScope(context, options, scope)
    assertTrue(serializedScope.isEmpty())
  }

  @Test
  fun `serializeScope provides fallback user if none is set`() {
    val options = IchnoisCoreAndroidOptions()
    val scope = Scope(options)
    scope.user = null

    val serializedScope = InternalIchnoisCoreSdk.serializeScope(context, options, scope)
    assertTrue((serializedScope["user"] as Map<*, *>).containsKey("id"))
  }

  @Test
  fun `serializeScope provides fallback user id when user info is disabled`() {
    val options = IchnoisCoreAndroidOptions().apply { dataCollection.setUserInfo(false) }
    val scope = Scope(options)
    scope.user = null

    val serializedScope = InternalIchnoisCoreSdk.serializeScope(context, options, scope)

    assertTrue((serializedScope["user"] as Map<*, *>).containsKey("id"))
  }

  @Test
  fun `serializeScope provides fallback user id when user info is enabled`() {
    val options = IchnoisCoreAndroidOptions().apply { dataCollection.setUserInfo(true) }
    val scope = Scope(options)
    scope.user = null

    val serializedScope = InternalIchnoisCoreSdk.serializeScope(context, options, scope)

    assertTrue((serializedScope["user"] as Map<*, *>).containsKey("id"))
  }

  @Test
  fun `serializeScope does not override user-id`() {
    val options = IchnoisCoreAndroidOptions()
    val scope = Scope(options)
    scope.user = User().apply { id = "abc" }

    val serializedScope = InternalIchnoisCoreSdk.serializeScope(context, options, scope)
    assertEquals("abc", (serializedScope["user"] as Map<*, *>)["id"])
  }

  @Test
  fun `serializeScope provides fallback app data if none is set`() {
    val options = IchnoisCoreAndroidOptions()
    val scope = Scope(options)
    scope.setContexts("app", null as Any?)

    val serializedScope = InternalIchnoisCoreSdk.serializeScope(context, options, scope)
    assertTrue(
      ((serializedScope["contexts"] as Map<*, *>)["app"] as Map<*, *>).containsKey("app_name")
    )
  }

  @Test
  fun `captureEnvelope fails if payload is invalid`() {
    assertNull(InternalIchnoisCoreSdk.captureEnvelope(ByteArray(8), false))
  }

  @Test
  fun `captureEnvelope correctly captures the original envelope data`() {
    val fixture = Fixture()
    fixture.init(context)

    // when capture envelope is called
    fixture.captureEnvelopeWithEvent()

    // then one envelope should be captured
    val capturedEnvelopes = fixture.capturedEnvelopes
    assertEquals(1, capturedEnvelopes.size)

    val capturedEnvelope = capturedEnvelopes.first()
    val capturedEnvelopeItems = capturedEnvelope.items.toList()

    // and it should contain the original event / attachment
    assertEquals(1, capturedEnvelopeItems.size)
    assertEquals(IchnoisCoreItemType.Event, capturedEnvelopeItems[0].header.type)
  }

  @Test
  fun `captureEnvelope correctly enriches the envelope with session data and does not start new session`() {
    val fixture = Fixture()
    fixture.init(context)

    // keep reference for current session for later assertions
    // we need to get the reference now as it will be removed from the scope
    val sessionRef = AtomicReference<Session>()
    IchnoisCore.configureScope { scope -> sessionRef.set(scope.session) }

    // when capture envelope is called with an crashed event
    fixture.captureEnvelopeWithEvent(fixture.createIchnoisCoreEventWithUnhandledException())

    val capturedEnvelope = fixture.capturedEnvelopes.first()
    val capturedEnvelopeItems = capturedEnvelope.items.toList()

    // then it should contain the original event + session
    assertEquals(2, capturedEnvelopeItems.size)
    assertEquals(IchnoisCoreItemType.Event, capturedEnvelopeItems[0].header.type)
    assertEquals(IchnoisCoreItemType.Session, capturedEnvelopeItems[1].header.type)

    // and then the sent session should be marked as crashed
    val capturedSession =
      fixture.options.serializer.deserialize(
        InputStreamReader(ByteArrayInputStream(capturedEnvelopeItems[1].data)),
        Session::class.java,
      )!!
    assertEquals(Session.State.Crashed, capturedSession.status)

    assertEquals(Session.State.Crashed, sessionRef.get().status)
    assertEquals(capturedSession.sessionId, sessionRef.get().sessionId)
  }

  @Test
  fun `captureEnvelope starts new session when enabled`() {
    val fixture = Fixture()
    fixture.init(context)

    // when capture envelope is called with an crashed event
    fixture.captureEnvelopeWithEvent(fixture.createIchnoisCoreEventWithUnhandledException(), true)

    val scopeRef = AtomicReference<IScope>()
    IchnoisCore.configureScope { scope -> scopeRef.set(scope) }

    // first envelope is the new session start
    val capturedStartSessionEnvelope = fixture.capturedEnvelopes.first()
    val capturedNewSessionStart =
      fixture.options.serializer.deserialize(
        InputStreamReader(
          ByteArrayInputStream(capturedStartSessionEnvelope.items.toList()[0].data)
        ),
        Session::class.java,
      )!!
    assertEquals(capturedNewSessionStart.sessionId, scopeRef.get().session!!.sessionId)
    assertEquals(Session.State.Ok, capturedNewSessionStart.status)

    val capturedEnvelope = fixture.capturedEnvelopes.last()
    val capturedEnvelopeItems = capturedEnvelope.items.toList()

    // there should be two envelopes session start and captured crash
    assertEquals(2, fixture.capturedEnvelopes.size)

    // then it should contain the original event + session
    assertEquals(2, capturedEnvelopeItems.size)
    assertEquals(IchnoisCoreItemType.Event, capturedEnvelopeItems[0].header.type)
    assertEquals(IchnoisCoreItemType.Session, capturedEnvelopeItems[1].header.type)

    // and then the sent session should be marked as crashed
    val capturedSession =
      fixture.options.serializer.deserialize(
        InputStreamReader(ByteArrayInputStream(capturedEnvelopeItems[1].data)),
        Session::class.java,
      )!!
    assertEquals(Session.State.Crashed, capturedSession.status)

    // and the local session should be a new session
    assertNotEquals(capturedSession.sessionId, scopeRef.get().session!!.sessionId)
  }

  @Test
  fun `captureEnvelopeNonTerminating keeps the session Ok and flags the unhandled error`() {
    val fixture = Fixture()
    fixture.init(context)

    val originalSid = AtomicReference<String>()
    IchnoisCore.configureScope { scope -> originalSid.set(scope.session!!.sessionId) }

    // when capture envelope is called with an unhandled event through the non-terminating API
    fixture.captureEnvelopeNonTerminatingWithEvent(
      fixture.createIchnoisCoreEventWithUnhandledException()
    )

    // then only the original event envelope is captured, without a session item
    assertThat(fixture.capturedEnvelopes).hasSize(1)
    val capturedEnvelopeItems = fixture.capturedEnvelopes.first().items.toList()
    assertThat(capturedEnvelopeItems).hasSize(1)
    assertThat(capturedEnvelopeItems[0].header.type).isEqualTo(IchnoisCoreItemType.Event)

    // and the session stays alive on the scope, same id, flagged with the unhandled error
    val scopeSession = AtomicReference<Session>()
    IchnoisCore.configureScope { scope -> scopeSession.set(scope.session) }
    assertThat(scopeSession.get().status).isEqualTo(Session.State.Ok)
    assertThat(scopeSession.get().hasNonTerminatingUnhandledError()).isTrue()
    assertThat(scopeSession.get().sessionId).isEqualTo(originalSid.get())

    // and it is persisted so the flag survives process death. The fixture leaves the real executor
    // service in place, so this only holds if the write happened on the calling thread.
    val sessionFile = EnvelopeCache.getCurrentSessionFile(fixture.options.cacheDirPath!!)
    val persistedSession =
      fixture.options.serializer.deserialize(sessionFile.reader(), Session::class.java)!!
    assertThat(persistedSession.status).isEqualTo(Session.State.Ok)
    assertThat(persistedSession.hasNonTerminatingUnhandledError()).isTrue()
    assertThat(persistedSession.sessionId).isEqualTo(originalSid.get())
  }

  @Test
  fun `captureEnvelopeNonTerminating does not record onto an already terminated session`() {
    val fixture = Fixture()
    fixture.init(context)

    // given a session already finalized on the scope, as an ANR leaves it
    val terminatedSession = AtomicReference<Session>()
    IchnoisCore.configureScope { scope ->
      scope.withSession { session ->
        session!!.update(Session.State.Abnormal, null, false, "anr_foreground")
        session.end()
      }
      terminatedSession.set(scope.session)
    }
    val errorCountBefore = terminatedSession.get().errorCount()
    val sessionFile = EnvelopeCache.getCurrentSessionFile(fixture.options.cacheDirPath!!)
    sessionFile.delete()

    // when a handled error arrives through the non-terminating API
    fixture.captureEnvelopeNonTerminatingWithEvent(fixture.createIchnoisCoreEventWithHandledException())

    // then the finalized session is left alone and never written back to the session file
    assertThat(terminatedSession.get().status).isEqualTo(Session.State.Abnormal)
    assertThat(terminatedSession.get().errorCount()).isEqualTo(errorCountBefore)
    assertThat(sessionFile.exists()).isFalse()
  }

  @Test
  fun `captureEnvelopeNonTerminating then endSession finalizes the session as unhandled`() {
    val fixture = Fixture()
    fixture.init(context)

    fixture.captureEnvelopeNonTerminatingWithEvent(
      fixture.createIchnoisCoreEventWithUnhandledException()
    )
    fixture.capturedEnvelopes.clear()

    // when the session is ended by normal lifecycle
    IchnoisCore.endSession()

    // then the ended session is captured as unhandled
    val sessionItems =
      fixture.capturedEnvelopes
        .flatMap { it.items.toList() }
        .filter {
          it.header.type == IchnoisCoreItemType.Session
        }
    assertThat(sessionItems).hasSize(1)
    val endedSession =
      fixture.options.serializer.deserialize(
        InputStreamReader(ByteArrayInputStream(sessionItems[0].data)),
        Session::class.java,
      )!!
    assertThat(endedSession.status).isEqualTo(Session.State.Unhandled)
  }

  @Test
  fun `captureEnvelopeNonTerminating then a crash finalizes old session and starts a new one`() {
    val fixture = Fixture()
    fixture.init(context)

    fixture.captureEnvelopeNonTerminatingWithEvent(
      fixture.createIchnoisCoreEventWithUnhandledException()
    )
    val unhandledSession = AtomicReference<Session>()
    IchnoisCore.configureScope { scope -> unhandledSession.set(scope.session) }
    val oldSid = unhandledSession.get().sessionId
    assertThat(unhandledSession.get().hasNonTerminatingUnhandledError()).isTrue()
    fixture.capturedEnvelopes.clear()

    // when a subsequent hard crash is captured through the existing terminating API
    fixture.captureEnvelopeWithEvent(fixture.createIchnoisCoreEventWithUnhandledException(), true)

    // then the crash envelope contains the finalized old session
    assertThat(fixture.capturedEnvelopes).hasSize(2)
    val crashEnvelopeItems = fixture.capturedEnvelopes.last().items.toList()
    assertThat(crashEnvelopeItems).hasSize(2)
    assertThat(crashEnvelopeItems[0].header.type).isEqualTo(IchnoisCoreItemType.Event)
    assertThat(crashEnvelopeItems[1].header.type).isEqualTo(IchnoisCoreItemType.Session)
    val crashedSession =
      fixture.options.serializer.deserialize(
        InputStreamReader(ByteArrayInputStream(crashEnvelopeItems[1].data)),
        Session::class.java,
      )!!
    assertThat(crashedSession.status).isEqualTo(Session.State.Crashed)
    assertThat(crashedSession.hasNonTerminatingUnhandledError()).isFalse()
    assertThat(crashedSession.sessionId).isEqualTo(oldSid)

    // and a new Ok session with a different id is active
    val activeSession = AtomicReference<Session>()
    IchnoisCore.configureScope { scope -> activeSession.set(scope.session) }
    assertThat(activeSession.get().status).isEqualTo(Session.State.Ok)
    assertThat(activeSession.get().hasNonTerminatingUnhandledError()).isFalse()
    assertThat(activeSession.get().sessionId).isNotEqualTo(oldSid)
  }

  @Test
  fun `updateSessionForDroppedEventNonTerminating flags an unhandled error without sending an envelope`() {
    val fixture = Fixture()
    fixture.init(context)

    val originalSid = AtomicReference<String>()
    IchnoisCore.configureScope { scope -> originalSid.set(scope.session!!.sessionId) }
    fixture.capturedEnvelopes.clear()

    InternalIchnoisCoreSdk.updateSessionForDroppedEventNonTerminating(true)

    assertThat(fixture.capturedEnvelopes).isEmpty()

    val scopeSession = AtomicReference<Session>()
    IchnoisCore.configureScope { scope -> scopeSession.set(scope.session) }
    assertThat(scopeSession.get().status).isEqualTo(Session.State.Ok)
    assertThat(scopeSession.get().hasNonTerminatingUnhandledError()).isTrue()
    assertThat(scopeSession.get().errorCount()).isEqualTo(1)
    assertThat(scopeSession.get().sessionId).isEqualTo(originalSid.get())

    val sessionFile = EnvelopeCache.getCurrentSessionFile(fixture.options.cacheDirPath!!)
    val persistedSession =
      fixture.options.serializer.deserialize(sessionFile.reader(), Session::class.java)!!
    assertThat(persistedSession.status).isEqualTo(Session.State.Ok)
    assertThat(persistedSession.hasNonTerminatingUnhandledError()).isTrue()
    assertThat(persistedSession.errorCount()).isEqualTo(1)
    assertThat(persistedSession.sessionId).isEqualTo(originalSid.get())
  }

  @Test
  fun `updateSessionForDroppedEventNonTerminating increments errors for a handled error without sending an envelope`() {
    val fixture = Fixture()
    fixture.init(context)

    val originalSid = AtomicReference<String>()
    IchnoisCore.configureScope { scope -> originalSid.set(scope.session!!.sessionId) }
    fixture.capturedEnvelopes.clear()

    InternalIchnoisCoreSdk.updateSessionForDroppedEventNonTerminating(false)

    assertThat(fixture.capturedEnvelopes).isEmpty()

    val scopeSession = AtomicReference<Session>()
    IchnoisCore.configureScope { scope -> scopeSession.set(scope.session) }
    assertThat(scopeSession.get().status).isEqualTo(Session.State.Ok)
    assertThat(scopeSession.get().hasNonTerminatingUnhandledError()).isFalse()
    assertThat(scopeSession.get().errorCount()).isEqualTo(1)
    assertThat(scopeSession.get().sessionId).isEqualTo(originalSid.get())

    val sessionFile = EnvelopeCache.getCurrentSessionFile(fixture.options.cacheDirPath!!)
    val persistedSession =
      fixture.options.serializer.deserialize(sessionFile.reader(), Session::class.java)!!
    assertThat(persistedSession.status).isEqualTo(Session.State.Ok)
    assertThat(persistedSession.hasNonTerminatingUnhandledError()).isFalse()
    assertThat(persistedSession.errorCount()).isEqualTo(1)
  }

  @Test
  fun `updateSessionForDroppedEventNonTerminating then endSession finalizes the session as unhandled`() {
    val fixture = Fixture()
    fixture.init(context)

    InternalIchnoisCoreSdk.updateSessionForDroppedEventNonTerminating(true)
    fixture.capturedEnvelopes.clear()

    IchnoisCore.endSession()

    val sessionItems =
      fixture.capturedEnvelopes
        .flatMap { it.items.toList() }
        .filter { it.header.type == IchnoisCoreItemType.Session }
    assertThat(sessionItems).hasSize(1)
    val endedSession =
      fixture.options.serializer.deserialize(
        InputStreamReader(ByteArrayInputStream(sessionItems[0].data)),
        Session::class.java,
      )!!
    assertThat(endedSession.status).isEqualTo(Session.State.Unhandled)
  }

  @Test
  fun `getAppStartMeasurement returns correct serialized data from the app start instance`() {
    Fixture().mockFinishedAppStart()

    val serializedAppStart = InternalIchnoisCoreSdk.getAppStartMeasurement()

    assertEquals("warm", serializedAppStart["type"])
    assertEquals(20.toLong(), serializedAppStart["app_start_timestamp_ms"])

    val actualSpans = serializedAppStart["spans"] as List<*>
    assertEquals(5, actualSpans.size)

    val actualProcessSpan = actualSpans[0] as Map<*, *>
    assertEquals("Process Initialization", actualProcessSpan["description"])
    assertEquals(20.toLong(), actualProcessSpan["start_timestamp_ms"])
    assertEquals(100.toLong(), actualProcessSpan["end_timestamp_ms"])

    val actualAppSpan = actualSpans[1] as Map<*, *>
    assertEquals("Application created", actualAppSpan["description"])
    assertEquals(30.toLong(), actualAppSpan["start_timestamp_ms"])
    assertEquals(40.toLong(), actualAppSpan["end_timestamp_ms"])

    val actualContentProviderSpan = actualSpans[2] as Map<*, *>
    assertEquals("Test Content Provider created", actualContentProviderSpan["description"])
    assertEquals(90.toLong(), actualContentProviderSpan["start_timestamp_ms"])
    assertEquals(100.toLong(), actualContentProviderSpan["end_timestamp_ms"])

    val actualActivityOnCreateSpan = actualSpans[3] as Map<*, *>
    assertEquals("Test Activity Lifecycle onCreate", actualActivityOnCreateSpan["description"])
    assertEquals(50.toLong(), actualActivityOnCreateSpan["start_timestamp_ms"])
    assertEquals(60.toLong(), actualActivityOnCreateSpan["end_timestamp_ms"])

    val actualActivityOnStartSpan = actualSpans[4] as Map<*, *>
    assertEquals("Test Activity Lifecycle onStart", actualActivityOnStartSpan["description"])
    assertEquals(70.toLong(), actualActivityOnStartSpan["start_timestamp_ms"])
    assertEquals(80.toLong(), actualActivityOnStartSpan["end_timestamp_ms"])
  }

  @Test
  fun `getAppStartMeasurement returns correct serialized data from the minimum app start instance`() {
    Fixture().mockMinimumFinishedAppStart()

    val serializedAppStart = InternalIchnoisCoreSdk.getAppStartMeasurement()

    assertEquals("warm", serializedAppStart["type"])
    assertEquals(20.toLong(), serializedAppStart["app_start_timestamp_ms"])

    val actualSpans = serializedAppStart["spans"] as List<*>
    assertEquals(2, actualSpans.size)

    val actualProcessSpan = actualSpans[0] as Map<*, *>
    assertEquals("Process Initialization", actualProcessSpan["description"])
    assertEquals(20.toLong(), actualProcessSpan["start_timestamp_ms"])
    assertEquals(100.toLong(), actualProcessSpan["end_timestamp_ms"])

    val actualAppSpan = actualSpans[1] as Map<*, *>
    assertEquals("Application created", actualAppSpan["description"])
    assertEquals(30.toLong(), actualAppSpan["start_timestamp_ms"])
    assertEquals(40.toLong(), actualAppSpan["end_timestamp_ms"])
  }

  @Test
  fun `getAppStartMeasurement returns only stopped spans in serialized data`() {
    Fixture().mockUnfinishedAppStart()

    val serializedAppStart = InternalIchnoisCoreSdk.getAppStartMeasurement()

    assertEquals("warm", serializedAppStart["type"])
    assertEquals(20.toLong(), serializedAppStart["app_start_timestamp_ms"])

    val actualSpans = serializedAppStart["spans"] as List<*>
    assertEquals(1, actualSpans.size)

    val actualProcessSpan = actualSpans[0] as Map<*, *>
    assertEquals("Process Initialization", actualProcessSpan["description"])
    assertEquals(20.toLong(), actualProcessSpan["start_timestamp_ms"])
    assertEquals(100.toLong(), actualProcessSpan["end_timestamp_ms"])
  }

  @Test
  fun `setTrace sets correct propagation context`() {
    val fixture = Fixture()
    fixture.init(context)

    val traceId = "771a43a4192642f0b136d5159a501700"
    val spanId = "771a43a4192642f0"
    val sampleRate = 0.5
    val sampleRand = 0.3

    InternalIchnoisCoreSdk.setTrace(traceId, spanId, sampleRate, sampleRand)

    IchnoisCore.configureScope { scope ->
      val propagationContext = scope.propagationContext
      assertEquals(IchnoisCoreId(traceId), propagationContext.traceId)
      assertEquals(SpanId(spanId), propagationContext.parentSpanId)
      assertEquals(sampleRate, propagationContext.baggage.sampleRate!!, 0.0001)
      assertEquals(sampleRand, propagationContext.baggage.sampleRand!!, 0.0001)
    }
  }
}
