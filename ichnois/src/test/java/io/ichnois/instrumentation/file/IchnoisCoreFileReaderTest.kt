package io.ichnois.instrumentation.file

import io.ichnois.IScopes
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreTracer
import io.ichnois.SpanDataConvention
import io.ichnois.SpanStatus.OK
import io.ichnois.TransactionContext
import io.ichnois.util.thread.ThreadChecker
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class IchnoisCoreFileReaderTest {
  class Fixture {
    val scopes = mock<IScopes>()
    lateinit var ichnoisTracer: IchnoisCoreTracer

    internal fun getSut(
      tmpFile: File,
      activeTransaction: Boolean = true,
      optionsConfiguration: (IchnoisCoreOptions) -> Unit = {},
    ): IchnoisCoreFileReader {
      tmpFile.writeText("TEXT")
      val options =
        IchnoisCoreOptions().apply {
          threadChecker = ThreadChecker.getInstance()
          optionsConfiguration(this)
        }
      whenever(scopes.options).thenReturn(options)
      ichnoisTracer = IchnoisCoreTracer(TransactionContext("name", "op"), scopes)
      if (activeTransaction) {
        whenever(scopes.span).thenReturn(ichnoisTracer)
      }
      return IchnoisCoreFileReader(tmpFile, scopes)
    }
  }

  @get:Rule val tmpDir = TemporaryFolder()

  private val fixture = Fixture()

  private val tmpFile: File
    get() = tmpDir.newFile("test.txt")

  private val tmpFileWithoutExtension: File
    get() = tmpDir.newFile("test")

  @Test
  fun `captures a span`() {
    val reader = fixture.getSut(tmpFile)
    reader.readText()
    reader.close()

    assertEquals(fixture.ichnoisTracer.children.size, 1)
    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(fileIOSpan.spanContext.operation, "file.read")
    assertEquals(fileIOSpan.spanContext.description, "***.txt (4 B)")
    assertEquals(fileIOSpan.data["file.size"], 4L)
    assertEquals(fileIOSpan.throwable, null)
    assertEquals(fileIOSpan.isFinished, true)
    assertEquals(fileIOSpan.data[SpanDataConvention.BLOCKED_MAIN_THREAD_KEY], true)
    assertEquals(fileIOSpan.status, OK)
  }

  @Test
  fun `captures file name in description and file path when isSendDefaultPii is true`() {
    val reader = fixture.getSut(tmpFile) { it.isSendDefaultPii = true }
    reader.readText()
    reader.close()

    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(fileIOSpan.spanContext.description, "test.txt (4 B)")
    assertNotNull(fileIOSpan.data["file.path"])
  }

  @Test
  fun `configured file paths false overrides sendDefaultPii true`() {
    val reader =
      fixture.getSut(tmpFile) {
        it.isSendDefaultPii = true
        it.dataCollection.setFilePaths(false)
      }
    reader.readText()
    reader.close()

    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(fileIOSpan.spanContext.description, "***.txt (4 B)")
    assertNull(fileIOSpan.data["file.path"])
  }

  @Test
  fun `configured file paths true overrides sendDefaultPii false`() {
    val reader =
      fixture.getSut(tmpFile) {
        it.isSendDefaultPii = false
        it.dataCollection.setFilePaths(true)
      }
    reader.readText()
    reader.close()

    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(fileIOSpan.spanContext.description, "test.txt (4 B)")
    assertNotNull(fileIOSpan.data["file.path"])
  }

  @Test
  fun `captures only file extension in description when isSendDefaultPii is false`() {
    val reader = fixture.getSut(tmpFile) { it.isSendDefaultPii = false }
    reader.readText()
    reader.close()

    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(fileIOSpan.spanContext.description, "***.txt (4 B)")
    assertNull(fileIOSpan.data["file.path"])
  }

  @Test
  fun `captures only file size if no extension is available when isSendDefaultPii is false`() {
    val reader = fixture.getSut(tmpFileWithoutExtension) { it.isSendDefaultPii = false }
    reader.readText()
    reader.close()

    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(fileIOSpan.spanContext.description, "*** (4 B)")
    assertNull(fileIOSpan.data["file.path"])
  }
}
