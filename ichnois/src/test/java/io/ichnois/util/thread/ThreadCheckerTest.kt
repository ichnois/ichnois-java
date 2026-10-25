package io.ichnois.util.thread

import io.ichnois.protocol.IchnoisCoreThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThreadCheckerTest {
  private val threadChecker = ThreadChecker.getInstance()

  @Test
  fun `When calling isMainThread from the same thread, it should return true`() {
    assertTrue(threadChecker.isMainThread)
  }

  @Test
  fun `When calling isMainThread with the current thread, it should return true`() {
    val thread = Thread.currentThread()
    assertTrue(threadChecker.isMainThread(thread))
  }

  @Test
  fun `When calling isMainThread from a different thread, it should return false`() {
    val thread = Thread()
    assertFalse(threadChecker.isMainThread(thread))
  }

  @Test
  fun `When calling isMainThread with the current IchnoisCoreThread, it should return true`() {
    val thread = Thread.currentThread()
    val ichnoisThread = IchnoisCoreThread().apply { id = thread.id }
    assertTrue(threadChecker.isMainThread(ichnoisThread))
  }

  @Test
  fun `When calling isMainThread from a different IchnoisCoreThread, it should return false`() {
    val thread = Thread()
    val ichnoisThread = IchnoisCoreThread().apply { id = thread.id }
    assertFalse(threadChecker.isMainThread(ichnoisThread))
  }

  @Test
  fun `currentThreadName returns the name of the current thread`() {
    val thread = Thread.currentThread()
    thread.name = "test"
    assertEquals("test", threadChecker.currentThreadName)
  }
}
