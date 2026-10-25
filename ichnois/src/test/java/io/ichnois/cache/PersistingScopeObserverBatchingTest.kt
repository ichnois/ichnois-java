package io.ichnois.cache

import com.google.common.truth.Truth.assertThat
import io.ichnois.Breadcrumb
import io.ichnois.IIchnoisCoreExecutorService
import io.ichnois.ISerializer
import io.ichnois.IchnoisCoreOptions
import io.ichnois.cache.PersistingScopeObserver.BREADCRUMBS_FILENAME
import io.ichnois.cache.PersistingScopeObserver.REPLAY_FILENAME
import io.ichnois.cache.PersistingScopeObserver.TRANSACTION_FILENAME
import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.test.DeferredExecutorService
import java.io.Writer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class PersistingScopeObserverBatchingTest {
  @get:Rule val tmpDir = TemporaryFolder()

  private val options = IchnoisCoreOptions()

  private fun getSut(executor: IIchnoisCoreExecutorService): PersistingScopeObserver {
    options.executorService = executor
    options.cacheDirPath = tmpDir.newFolder().absolutePath
    return PersistingScopeObserver(options)
  }

  private fun PersistingScopeObserver.readTransaction(): String? =
    read(options, TRANSACTION_FILENAME, String::class.java)

  private fun PersistingScopeObserver.readReplayId(): String? =
    read(options, REPLAY_FILENAME, String::class.java)

  @Suppress("UNCHECKED_CAST")
  private fun PersistingScopeObserver.readBreadcrumbs(): List<Breadcrumb> =
    read(options, BREADCRUMBS_FILENAME, List::class.java) as List<Breadcrumb>

  @Test
  fun `defers writes until the flush runs`() {
    val executor = DeferredExecutorService()
    val sut = getSut(executor)

    sut.setTransaction("MainActivity")
    assertThat(sut.readTransaction()).isNull()

    executor.runAll()
    assertThat(sut.readTransaction()).isEqualTo("MainActivity")
  }

  @Test
  fun `coalesces repeated writes keeping only the latest value`() {
    val executor = DeferredExecutorService()
    val sut = getSut(executor)

    sut.setTransaction("A")
    sut.setTransaction("B")
    sut.setTransaction("C")
    executor.runAll()

    assertThat(sut.readTransaction()).isEqualTo("C")
  }

  @Test
  fun `batches breadcrumbs and persists all of them`() {
    val executor = DeferredExecutorService()
    val sut = getSut(executor)

    sut.addBreadcrumb(Breadcrumb().apply { message = "one" })
    sut.addBreadcrumb(Breadcrumb().apply { message = "two" })
    assertThat(sut.readBreadcrumbs()).isEmpty()

    executor.runAll()
    assertThat(sut.readBreadcrumbs().map { it.message }).containsExactly("one", "two").inOrder()
  }

  @Test
  fun `clearing breadcrumbs drops earlier pending adds but keeps later ones`() {
    val executor = DeferredExecutorService()
    val sut = getSut(executor)

    sut.addBreadcrumb(Breadcrumb().apply { message = "dropped" })
    sut.setBreadcrumbs(emptyList())
    sut.addBreadcrumb(Breadcrumb().apply { message = "kept" })
    executor.runAll()

    assertThat(sut.readBreadcrumbs().map { it.message }).containsExactly("kept")
  }

  @Test
  fun `a clear landing mid-flush does not wipe breadcrumbs added after it`() {
    val executor = DeferredExecutorService()
    lateinit var sut: PersistingScopeObserver
    // fires while the flush is draining, i.e. after the first breadcrumb has been written
    options.setSerializer(
      SerializerHook(options.serializer) {
        sut.setBreadcrumbs(emptyList())
        sut.addBreadcrumb(Breadcrumb().apply { message = "kept" })
      }
    )
    sut = getSut(executor)

    sut.addBreadcrumb(Breadcrumb().apply { message = "dropped" })
    executor.runAll()
    executor.runAll()

    assertThat(sut.readBreadcrumbs().map { it.message }).containsExactly("kept")
  }

  /** Delegates to [delegate], running [onFirstBreadcrumb] once, mid-serialization. */
  private class SerializerHook(
    private val delegate: ISerializer,
    private val onFirstBreadcrumb: () -> Unit,
  ) : ISerializer by delegate {
    private val fired = AtomicBoolean(false)

    override fun <T : Any> serialize(entity: T, writer: Writer) {
      if (entity is Breadcrumb && fired.compareAndSet(false, true)) {
        onFirstBreadcrumb()
      }
      delegate.serialize(entity, writer)
    }
  }

  @Test
  fun `resetCache clears the replay id left behind by the previous process`() {
    val executor = DeferredExecutorService()
    val sut = getSut(executor)

    sut.setReplayId(IchnoisCoreId("afcb46b1140ade5187c4bbb5daa804df"))
    executor.runAll()
    assertThat(sut.readReplayId()).isEqualTo("afcb46b1140ade5187c4bbb5daa804df")

    sut.resetCache()

    assertThat(sut.readReplayId()).isNull()
  }

  @Test
  fun `resetCache keeps pending mutations from the current process`() {
    val executor = DeferredExecutorService()
    val sut = getSut(executor)

    sut.setTransaction("SetDuringInit")
    sut.resetCache()
    executor.runAll()

    assertThat(sut.readTransaction()).isEqualTo("SetDuringInit")
  }
}
