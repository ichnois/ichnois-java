package io.ichnois.instrumentation.file

import io.ichnois.IScopes
import io.ichnois.IchnoisCoreOptions
import io.ichnois.IchnoisCoreTracer
import io.ichnois.SpanDataConvention
import io.ichnois.SpanStatus
import io.ichnois.SpanStatus.INTERNAL_ERROR
import io.ichnois.TransactionContext
import io.ichnois.protocol.IchnoisCoreStackFrame
import io.ichnois.util.thread.ThreadChecker
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.awaitility.kotlin.await
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class IchnoisCoreFileInputStreamTest {
  class Fixture {
    val scopes = mock<IScopes>()
    lateinit var ichnoisTracer: IchnoisCoreTracer
    private val options = IchnoisCoreOptions()

    internal fun getSut(
      tmpFile: File? = null,
      activeTransaction: Boolean = true,
      fileDescriptor: FileDescriptor? = null,
      sendDefaultPii: Boolean = false,
    ): FileInputStream {
      tmpFile?.writeText("Text")
      whenever(scopes.options)
        .thenReturn(
          options.apply {
            isSendDefaultPii = sendDefaultPii
            threadChecker = ThreadChecker.getInstance()
            addInAppInclude("org.junit")
          }
        )
      ichnoisTracer = IchnoisCoreTracer(TransactionContext("name", "op"), scopes)
      if (activeTransaction) {
        whenever(scopes.span).thenReturn(ichnoisTracer)
      }
      return if (fileDescriptor == null) {
        IchnoisCoreFileInputStream(tmpFile, scopes)
      } else {
        IchnoisCoreFileInputStream(fileDescriptor, scopes)
      }
    }

    internal fun getSut(
      tmpFile: File? = null,
      delegate: FileInputStream,
      tracesSampleRate: Double? = 1.0,
    ): FileInputStream {
      options.tracesSampleRate = tracesSampleRate
      whenever(scopes.options).thenReturn(options)
      ichnoisTracer = IchnoisCoreTracer(TransactionContext("name", "op"), scopes)
      whenever(scopes.span).thenReturn(ichnoisTracer)
      return IchnoisCoreFileInputStream.Factory.create(delegate, tmpFile, scopes)
    }
  }

  @get:Rule val tmpDir = TemporaryFolder()

  private val fixture = Fixture()

  private val tmpFile: File
    get() = tmpDir.newFile("test.txt")

  private val tmpFileWithoutExtension: File
    get() = tmpDir.newFile("test")

  @Test
  fun `when no active transaction does not capture a span`() {
    fixture.getSut(tmpFile, activeTransaction = false).use { it.readAllBytes() }

    assertEquals(fixture.ichnoisTracer.children.size, 0)
  }

  @Test
  fun `when stream is not closed does not finish a span`() {
    fixture.getSut(tmpFile)

    assertEquals(fixture.ichnoisTracer.children.size, 1)
    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(fileIOSpan.isFinished, false)
  }

  @Test
  fun `when stream is closed captures a span`() {
    val fis = fixture.getSut(tmpFile)
    fis.close()

    assertEquals(fixture.ichnoisTracer.children.size, 1)
    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(fileIOSpan.data["file.size"], 0L)
    assertEquals(fileIOSpan.throwable, null)
    assertEquals(fileIOSpan.isFinished, true)
    assertEquals(fileIOSpan.status, SpanStatus.OK)
  }

  @Test
  fun `captures file name in description and file path when isSendDefaultPii is true`() {
    val fis = fixture.getSut(tmpFile, sendDefaultPii = true)
    fis.close()

    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(fileIOSpan.spanContext.description, "test.txt (0 B)")
    assertNotNull(fileIOSpan.data["file.path"])
  }

  @Test
  fun `captures only file extension in description when isSendDefaultPii is false`() {
    val fis = fixture.getSut(tmpFile, sendDefaultPii = false)
    fis.close()

    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(fileIOSpan.spanContext.description, "***.txt (0 B)")
    assertNull(fileIOSpan.data["file.path"])
  }

  @Test
  fun `captures only file size if no extension is available when isSendDefaultPii is false`() {
    val fis = fixture.getSut(tmpFileWithoutExtension, sendDefaultPii = false)
    fis.close()

    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(fileIOSpan.spanContext.description, "*** (0 B)")
    assertNull(fileIOSpan.data["file.path"])
  }

  @Test
  fun `when stream is closed, releases file descriptor`() {
    val fis = fixture.getSut(tmpFile)
    fis.use { it.readAllBytes() }
    assertFalse(fis.fd.valid())
  }

  @Test
  fun `read one byte`() {
    fixture.getSut(tmpFile).use { it.read() }

    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(fileIOSpan.spanContext.description, "***.txt (1 B)")
    assertEquals(fileIOSpan.data["file.size"], 1L)
  }

  @Test
  fun `read array of bytes`() {
    fixture.getSut(tmpFile).use { it.read(ByteArray(10)) }

    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(fileIOSpan.spanContext.description, "***.txt (4 B)")
    assertEquals(fileIOSpan.data["file.size"], 4L)
  }

  @Test
  fun `read array range of bytes`() {
    fixture.getSut(tmpFile).use { it.read(ByteArray(10), 1, 3) }

    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(fileIOSpan.spanContext.description, "***.txt (3 B)")
    assertEquals(fileIOSpan.data["file.size"], 3L)
  }

  @Test
  fun `skip bytes`() {
    fixture.getSut(tmpFile).use { it.skip(10) }

    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(fileIOSpan.spanContext.description, "***.txt (10 B)")
    assertEquals(fileIOSpan.data["file.size"], 10L)
  }

  @Test
  fun `read all bytes`() {
    fixture.getSut(tmpFile).use { it.reader().readText() }

    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(fileIOSpan.spanContext.description, "***.txt (4 B)")
    assertEquals(fileIOSpan.data["file.size"], 4L)
  }

  @Test
  fun `when IO operation throws captures Throwable and sets status to INTERNAL_ERROR`() {
    val file = tmpFile
    val delegate = ThrowingFileInputStream(file)
    try {
      fixture.getSut(file, delegate = delegate).use { it.read() }
    } catch (e: IOException) {
      // ignored
    }

    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(fileIOSpan.status, INTERNAL_ERROR)
    assertEquals(fileIOSpan.throwable, delegate.throwable)
  }

  @Test
  fun `when close() throws captures Throwable and sets status to INTERNAL_ERROR`() {
    val file = tmpFile
    val delegate = ThrowingFileInputStream(file)
    try {
      val fis = fixture.getSut(file, delegate = delegate)
      fis.close()
    } catch (e: IOException) {
      // ignored
    }

    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(fileIOSpan.status, INTERNAL_ERROR)
    assertEquals(fileIOSpan.throwable, delegate.throwable)
  }

  @Test
  fun `when instantiated with file descriptor only reports file size as description`() {
    val file = tmpFile.apply { writeText("Text") }
    val fd = FileInputStream(file).fd
    fixture.getSut(fileDescriptor = fd).use { it.read() }

    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(fileIOSpan.description, "1 B")
  }

  @Test
  fun `when run on JVM and sendDefaultPii is enabled reports file path`() {
    val file = tmpFile
    fixture.getSut(file, sendDefaultPii = true).use { it.readAllBytes() }
    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(fileIOSpan.data["file.path"], file.absolutePath)
  }

  @Test
  fun `when run on main thread, attaches call_stack with blocked_main_thread=true`() {
    val fis = fixture.getSut(tmpFile)
    fis.close()

    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(true, fileIOSpan.data[SpanDataConvention.BLOCKED_MAIN_THREAD_KEY])
    // assuming our "in-app" is org.junit, we check that only org.junit frames are in the call stack
    assertTrue {
      (fileIOSpan.data[SpanDataConvention.CALL_STACK_KEY] as List<IchnoisCoreStackFrame>).all {
        it.module?.startsWith("org.junit") == true
      }
    }
  }

  @Test
  fun `when run on a background thread, does not attach call_stack with blocked_main_thread=false`() {
    val finished = AtomicBoolean(false)
    thread {
      val fis = fixture.getSut(tmpFile)
      fis.close()
      finished.set(true)
    }

    await.untilTrue(finished)

    val fileIOSpan = fixture.ichnoisTracer.children.first()
    assertEquals(false, fileIOSpan.data[SpanDataConvention.BLOCKED_MAIN_THREAD_KEY])
    assertNull(fileIOSpan.data[SpanDataConvention.CALL_STACK_KEY])
  }

  @Test
  fun `when tracing is disabled does not instrument the stream`() {
    val file = tmpFile
    val delegate = ThrowingFileInputStream(file)
    val stream = fixture.getSut(file, delegate = delegate, tracesSampleRate = null)

    assertTrue { stream is ThrowingFileInputStream }
  }

  @Test
  fun `channels and descriptors are closed together with the stream`() {
    val fis = fixture.getSut(tmpFile)
    val channel = fis.channel

    channel.read(ByteBuffer.allocate(1))
    fis.close()
    assertFalse(channel.isOpen)
    assertFalse(fis.fd.valid())
  }
}

class ThrowingFileInputStream(file: File) : FileInputStream(file) {
  val throwable = IOException("Oops!")

  override fun read(): Int = throw throwable

  override fun close(): Unit = throw throwable
}
