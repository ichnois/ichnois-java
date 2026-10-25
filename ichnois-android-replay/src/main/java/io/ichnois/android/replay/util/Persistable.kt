package io.ichnois.android.replay.util

import android.annotation.SuppressLint
import android.annotation.TargetApi
import io.ichnois.ReplayRecording
import io.ichnois.IchnoisCoreOptions
import io.ichnois.android.replay.ReplayCache
import io.ichnois.rrweb.RRWebEvent
import java.io.BufferedWriter
import java.io.StringWriter
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ScheduledExecutorService

// TODO: enable this back after we are able to serialize individual touches to disk to not overload
// cpu
@SuppressLint("UseRequiresApi")
@Suppress("unused")
@TargetApi(26)
internal class PersistableLinkedList(
  private val propertyName: String,
  private val options: IchnoisCoreOptions,
  private val persistingExecutor: ScheduledExecutorService,
  private val cacheProvider: () -> ReplayCache?,
) : ConcurrentLinkedDeque<RRWebEvent>() {
  // only overriding methods that we use, to observe the collection
  override fun addAll(elements: Collection<RRWebEvent>): Boolean {
    val result = super.addAll(elements)
    persistRecording()
    return result
  }

  override fun add(element: RRWebEvent): Boolean {
    val result = super.add(element)
    persistRecording()
    return result
  }

  override fun remove(): RRWebEvent {
    val result = super.remove()
    persistRecording()
    return result
  }

  private fun persistRecording() {
    val cache = cacheProvider() ?: return
    val recording = ReplayRecording().apply { payload = ArrayList(this@PersistableLinkedList) }
    if (options.threadChecker.isMainThread) {
      persistingExecutor.submit {
        val stringWriter = StringWriter()
        options.serializer.serialize(recording, BufferedWriter(stringWriter))
        cache.persistSegmentValues(propertyName, stringWriter.toString())
      }
    } else {
      val stringWriter = StringWriter()
      options.serializer.serialize(recording, BufferedWriter(stringWriter))
      cache.persistSegmentValues(propertyName, stringWriter.toString())
    }
  }
}
