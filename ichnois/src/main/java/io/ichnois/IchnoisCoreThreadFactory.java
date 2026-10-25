package io.ichnois;

import io.ichnois.protocol.IchnoisCoreStackFrame;
import io.ichnois.protocol.IchnoisCoreStackTrace;
import io.ichnois.protocol.IchnoisCoreThread;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** class responsible for converting Java Threads to IchnoisCoreThreads */
@ApiStatus.Internal
public final class IchnoisCoreThreadFactory {

  /** the IchnoisCoreStackTraceFactory */
  private final @NotNull IchnoisCoreStackTraceFactory ichnoisStackTraceFactory;

  /**
   * ctor IchnoisCoreThreadFactory that takes a IchnoisCoreStackTraceFactory
   *
   * @param ichnoisStackTraceFactory the IchnoisCoreStackTraceFactory
   */
  public IchnoisCoreThreadFactory(final @NotNull IchnoisCoreStackTraceFactory ichnoisStackTraceFactory) {
    this.ichnoisStackTraceFactory = ichnoisStackTraceFactory;
  }

  /**
   * Converts the current thread to a IchnoisCoreThread, it assumes its being called from the captured
   * thread.
   *
   * @return a list of IchnoisCoreThread with a single item or null
   */
  @Nullable
  List<IchnoisCoreThread> getCurrentThread(final boolean attachStackTrace) {
    final Map<Thread, StackTraceElement[]> threads = new HashMap<>();
    final Thread currentThread = Thread.currentThread();
    threads.put(currentThread, currentThread.getStackTrace());

    return getCurrentThreads(threads, null, false, attachStackTrace);
  }

  /**
   * Converts a list of all current threads to a list of IchnoisCoreThread Assumes its being called from
   * the crashed thread.
   *
   * @param mechanismThreadIds list of threadIds that came from exception mechanism
   * @param ignoreCurrentThread if the current thread should be ignored when marking threads as
   *     crashed. This is the case for e.g. watchdog threads which are not the one erroring.
   * @return a list of IchnoisCoreThread
   */
  @Nullable
  List<IchnoisCoreThread> getCurrentThreads(
      final @Nullable List<Long> mechanismThreadIds,
      final boolean ignoreCurrentThread,
      final boolean attachStackTrace) {
    return getCurrentThreads(
        Thread.getAllStackTraces(), mechanismThreadIds, ignoreCurrentThread, attachStackTrace);
  }

  @Nullable
  List<IchnoisCoreThread> getCurrentThreads(
      final @Nullable List<Long> mechanismThreadIds, final boolean attachStackTrace) {
    return getCurrentThreads(
        Thread.getAllStackTraces(), mechanismThreadIds, false, attachStackTrace);
  }

  /**
   * Converts a list of all current threads to a list of IchnoisCoreThread Assumes its being called from
   * the crashed thread.
   *
   * @param threads a map with all the current threads and stacktraces
   * @param mechanismThreadIds list of threadIds that came from exception mechanism
   * @param ignoreCurrentThread if the current thread should be ignored when marking threads as
   *     crashed. This is the case for e.g. watchdog threads which are not the one erroring.
   * @return a list of IchnoisCoreThread or null if none
   */
  // temp fix for JDK 21: Identifies the current thread by identity.
  @SuppressWarnings("ReferenceEquality")
  @TestOnly
  @Nullable
  List<IchnoisCoreThread> getCurrentThreads(
      final @NotNull Map<Thread, StackTraceElement[]> threads,
      final @Nullable List<Long> mechanismThreadIds,
      final boolean ignoreCurrentThread,
      final boolean attachStackTrace) {
    List<IchnoisCoreThread> result = null;

    final Thread currentThread = Thread.currentThread();

    if (!threads.isEmpty()) {
      result = new ArrayList<>();

      // https://issuetracker.google.com/issues/64122757
      if (!threads.containsKey(currentThread)) {
        threads.put(currentThread, currentThread.getStackTrace());
      }

      for (Map.Entry<Thread, StackTraceElement[]> item : threads.entrySet()) {

        final Thread thread = item.getKey();
        final boolean crashed =
            (thread == currentThread && !ignoreCurrentThread)
                || (mechanismThreadIds != null
                    && mechanismThreadIds.contains(thread.getId())
                    && !ignoreCurrentThread);

        result.add(getIchnoisCoreThread(crashed, item.getValue(), item.getKey(), attachStackTrace));
      }
    }

    return result;
  }

  /**
   * Converts a current thread to a IchnoisCoreThread
   *
   * @param crashed if its the thread that has crashed or not
   * @param stackFramesElements the stack traces of the current thread
   * @param thread the thread to be converted
   * @return a IchnoisCoreThread
   */
  private @NotNull IchnoisCoreThread getIchnoisCoreThread(
      final boolean crashed,
      final @NotNull StackTraceElement[] stackFramesElements,
      final @NotNull Thread thread,
      final boolean attachStacktrace) {
    final IchnoisCoreThread ichnoisThread = new IchnoisCoreThread();

    ichnoisThread.setName(thread.getName());
    ichnoisThread.setPriority(thread.getPriority());
    ichnoisThread.setId(thread.getId());
    ichnoisThread.setDaemon(thread.isDaemon());
    ichnoisThread.setState(thread.getState().name());
    ichnoisThread.setCrashed(crashed);

    if (attachStacktrace) {
      final List<IchnoisCoreStackFrame> frames =
          ichnoisStackTraceFactory.getStackFrames(stackFramesElements, false);

      if (frames != null && !frames.isEmpty()) {
        final IchnoisCoreStackTrace ichnoisStackTrace = new IchnoisCoreStackTrace(frames);
        // threads are always gotten either via Thread.currentThread() or Thread.getAllStackTraces()
        ichnoisStackTrace.setSnapshot(true);

        ichnoisThread.setStacktrace(ichnoisStackTrace);
      }
    }

    return ichnoisThread;
  }
}
