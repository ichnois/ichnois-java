package io.ichnois.android.timber

import io.ichnois.IScopes
import io.ichnois.ITransportFactory
import io.ichnois.ScopesAdapter
import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCoreLevel
import io.ichnois.IchnoisCoreLogLevel
import io.ichnois.IchnoisCoreOptions
import io.ichnois.protocol.SdkVersion
import io.ichnois.transport.ITransport
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import timber.log.Timber

class IchnoisCoreTimberIntegrationTest {
  private class Fixture {
    val scopes = mock<IScopes>()
    val options = IchnoisCoreOptions().apply { sdkVersion = SdkVersion("test", "1.2.3") }

    init {
      whenever(scopes.options).thenReturn(options)
    }

    fun getSut(
      minEventLevel: IchnoisCoreLevel = IchnoisCoreLevel.ERROR,
      minBreadcrumbLevel: IchnoisCoreLevel = IchnoisCoreLevel.INFO,
      minLogsLevel: IchnoisCoreLogLevel = IchnoisCoreLogLevel.INFO,
    ): IchnoisCoreTimberIntegration =
      IchnoisCoreTimberIntegration(
        minEventLevel = minEventLevel,
        minBreadcrumbLevel = minBreadcrumbLevel,
        minLogsLevel = minLogsLevel,
      )
  }

  private val fixture = Fixture()

  @BeforeTest
  fun beforeTest() {
    Timber.uprootAll()
  }

  @Test
  fun `Integrations plants a tree into Timber on register`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    assertEquals(1, Timber.treeCount())

    val trees = Timber.forest()
    val first = trees.first()
    assertTrue(first is IchnoisCoreTimberTree)
  }

  @Test
  fun `Integrations plants the IchnoisCoreTimberTree tree`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    Timber.e(Throwable())
    verify(fixture.scopes).captureEvent(any())
  }

  @Test
  fun `Integrations removes a tree from Timber on close integration`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    assertEquals(1, Timber.treeCount())

    sut.close()
    assertEquals(0, Timber.treeCount())
  }

  @Test
  fun `Integrations do not throw if close is called before register`() {
    val sut = fixture.getSut()
    sut.close()

    assertEquals(0, Timber.treeCount())
  }

  @Test
  fun `Integrations pass the right min levels`() {
    val sut =
      fixture.getSut(
        minEventLevel = IchnoisCoreLevel.INFO,
        minBreadcrumbLevel = IchnoisCoreLevel.DEBUG,
        minLogsLevel = IchnoisCoreLogLevel.TRACE,
      )
    sut.register(fixture.scopes, fixture.options)

    assertEquals(sut.minEventLevel, IchnoisCoreLevel.INFO)
    assertEquals(sut.minBreadcrumbLevel, IchnoisCoreLevel.DEBUG)
    assertEquals(sut.minLogsLevel, IchnoisCoreLogLevel.TRACE)
  }

  @Test
  fun `Integration adds itself to the package list`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    assertTrue(
      fixture.options.sdkVersion!!.packageSet.any {
        it.name == "maven:io.ichnois:ichnois-android-timber" && it.version == BuildConfig.VERSION_NAME
      }
    )
  }

  @Test
  fun `Integration adds itself to the integration list`() {
    val sut = fixture.getSut()
    sut.register(fixture.scopes, fixture.options)

    assertTrue(fixture.options.sdkVersion!!.integrationSet.contains("Timber"))
  }

  @Test
  fun `a beforeSend callback that logs via Timber does not recurse`() {
    // End-to-end guard against SDK-CRASHES-JAVA-3T3H style recursion: with a real IchnoisCore instance,
    // a beforeSend callback that logs through the planted IchnoisCoreTimberTree must not loop back into
    // capture forever.
    val transport = mock<ITransport>()
    val transportFactory = mock<ITransportFactory>()
    whenever(transportFactory.create(any(), any())).thenReturn(transport)

    var beforeSendInvocations = 0
    IchnoisCore.init { options ->
      options.dsn = "https://key@ichnois.io/123"
      options.setTransportFactory(transportFactory)
      options.beforeSend = IchnoisCoreOptions.BeforeSendCallback { event, _ ->
        beforeSendInvocations++
        Timber.e("logging from beforeSend")
        event
      }
    }
    Timber.plant(
      IchnoisCoreTimberTree(
        ScopesAdapter.getInstance(),
        IchnoisCoreLevel.ERROR,
        IchnoisCoreLevel.INFO,
        IchnoisCoreLogLevel.INFO,
      )
    )

    try {
      Timber.e("outer error")

      // Without the core re-entrancy guard this recurses until a StackOverflowError. The nested
      // Timber.e is dropped before its own beforeSend, so the callback runs exactly once.
      assertEquals(1, beforeSendInvocations)
    } finally {
      IchnoisCore.close()
    }
  }
}
