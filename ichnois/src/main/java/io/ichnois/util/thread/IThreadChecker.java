package io.ichnois.util.thread;

import io.ichnois.protocol.IchnoisCoreThread;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface IThreadChecker {

  boolean isMainThread(final long threadId);

  /**
   * Checks if a given thread is the Main/UI thread
   *
   * @param thread the Thread
   * @return true if it is the main thread or false otherwise
   */
  boolean isMainThread(final @NotNull Thread thread);

  /**
   * Checks if the calling/current thread is the Main/UI thread
   *
   * @return true if it is the main thread or false otherwise
   */
  boolean isMainThread();

  /**
   * Checks if a given thread is the Main/UI thread
   *
   * @param ichnoisThread the IchnoisCoreThread
   * @return true if it is the main thread or false otherwise
   */
  boolean isMainThread(final @NotNull IchnoisCoreThread ichnoisThread);

  /**
   * Returns the name of the current thread
   *
   * @return the name of the current thread
   */
  @NotNull
  String getCurrentThreadName();

  /**
   * Returns the system id of the current thread. Currently only used for Android.
   *
   * @return the current thread system id.
   */
  long currentThreadSystemId();
}
