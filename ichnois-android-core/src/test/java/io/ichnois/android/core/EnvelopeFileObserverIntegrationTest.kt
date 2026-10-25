package io.ichnois.android.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ichnois.ILogger
import io.ichnois.IScopes
import io.ichnois.IchnoisCoreLevel
import io.ichnois.IchnoisCoreOptions
import io.ichnois.test.DeferredExecutorService
import io.ichnois.test.ImmediateExecutorService
import io.ichnois.test.createTestScopes
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class EnvelopeFileObserverIntegrationTest {
  inner class Fixture {
    val scopes: IScopes = mock()
    private lateinit var options: IchnoisCoreAndroidOptions
    val logger = mock<ILogger>()

    fun getSut(
      optionConfiguration: (IchnoisCoreAndroidOptions) -> Unit = {}
    ): EnvelopeFileObserverIntegration {
      options = IchnoisCoreAndroidOptions()
      options.setLogger(logger)
      options.isDebug = true
      optionConfiguration(options)
      whenever(scopes.options).thenReturn(options)

      return object : EnvelopeFileObserverIntegration() {
        override fun getPath(options: IchnoisCoreOptions): String? = file.absolutePath
      }
    }
  }

  private val fixture = Fixture()

  private lateinit var file: File

  @BeforeTest
  fun `set up`() {
    file = Files.createTempDirectory("ichnois-disk-cache-test").toAbsolutePath().toFile()
  }

  @AfterTest
  fun shutdown() {
    delete(file)
  }

  private fun delete(f: File) {
    f.listFiles()?.forEach { delete(it) }
    Files.delete(f.toPath())
  }

  @Test
  fun `when instance from getOutboxFileObserver, options getOutboxPath is used`() {
    val options = IchnoisCoreOptions()
    options.cacheDirPath = "some_dir"

    val sut = EnvelopeFileObserverIntegration.getOutboxFileObserver()
    assertEquals(options.outboxPath, sut.getPath(options))
  }

  @Test
  fun `when scopes is closed, integrations should be closed`() {
    val integrationMock = mock<EnvelopeFileObserverIntegration>()
    val options = IchnoisCoreOptions()
    options.dsn = "https://key@ichnois-h2jw.onrender.com/proj"
    options.cacheDirPath = file.absolutePath
    options.addIntegration(integrationMock)
    options.setSerializer(mock())
    val scopes = createTestScopes(options)
    scopes.close()
    verify(integrationMock).close()
  }

  @Test
  fun `when scopes is closed right after start, integration is not registered`() {
    val deferredExecutorService = DeferredExecutorService()
    val integration = fixture.getSut { it.executorService = deferredExecutorService }
    integration.register(fixture.scopes, fixture.scopes.options)
    integration.close()
    deferredExecutorService.runAll()
    verify(fixture.logger, never())
      .log(eq(IchnoisCoreLevel.DEBUG), eq("EnvelopeFileObserverIntegration installed."))
  }

  @Test
  fun `register with fake executor service does not install integration`() {
    val integration = fixture.getSut { it.executorService = mock() }
    integration.register(fixture.scopes, fixture.scopes.options)
    verify(fixture.logger)
      .log(
        eq(IchnoisCoreLevel.DEBUG),
        eq("Registering EnvelopeFileObserverIntegration for path: %s"),
        eq(file.absolutePath),
      )
    verify(fixture.logger, never())
      .log(eq(IchnoisCoreLevel.DEBUG), eq("EnvelopeFileObserverIntegration installed."))
  }

  @Test
  fun `register integration on the background via executor service`() {
    val integration = fixture.getSut { it.executorService = ImmediateExecutorService() }
    integration.register(fixture.scopes, fixture.scopes.options)
    verify(fixture.logger)
      .log(
        eq(IchnoisCoreLevel.DEBUG),
        eq("Registering EnvelopeFileObserverIntegration for path: %s"),
        eq(file.absolutePath),
      )
    verify(fixture.logger)
      .log(eq(IchnoisCoreLevel.DEBUG), eq("EnvelopeFileObserverIntegration installed."))
  }

  @Test
  fun `register creates the outbox dir when it does not exist yet`() {
    val outboxDir = File(file, "outbox")
    assertFalse(outboxDir.exists())

    fixture.getSut { it.executorService = ImmediateExecutorService() }
    val integration =
      object : EnvelopeFileObserverIntegration() {
        override fun getPath(options: IchnoisCoreOptions): String = outboxDir.absolutePath
      }
    integration.register(fixture.scopes, fixture.scopes.options)

    assertTrue(outboxDir.isDirectory)
  }
}
