package io.ichnois;

import io.ichnois.exception.ExceptionMechanismException;
import io.ichnois.protocol.Mechanism;
import io.ichnois.protocol.IchnoisCoreException;
import io.ichnois.protocol.IchnoisCoreStackFrame;
import io.ichnois.protocol.IchnoisCoreStackTrace;
import io.ichnois.protocol.IchnoisCoreThread;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** class responsible for converting Java Throwable to IchnoisCoreExceptions */
@ApiStatus.Internal
public final class IchnoisCoreExceptionFactory {

  /** the IchnoisCoreStackTraceFactory */
  private final @NotNull IchnoisCoreStackTraceFactory ichnoisStackTraceFactory;

  /**
   * ctor IchnoisCoreExceptionFactory
   *
   * @param ichnoisStackTraceFactory the ichnoisStackTraceFactory
   */
  public IchnoisCoreExceptionFactory(final @NotNull IchnoisCoreStackTraceFactory ichnoisStackTraceFactory) {
    this.ichnoisStackTraceFactory = ichnoisStackTraceFactory;
  }

  @NotNull
  public List<IchnoisCoreException> getIchnoisCoreExceptionsFromThread(
      final @NotNull IchnoisCoreThread thread,
      final @NotNull Mechanism mechanism,
      final @NotNull Throwable throwable) {
    final IchnoisCoreStackTrace threadStacktrace = thread.getStacktrace();
    if (threadStacktrace == null) {
      return new ArrayList<>(0);
    }
    final List<IchnoisCoreException> exceptions = new ArrayList<>(1);
    exceptions.add(
        getIchnoisCoreException(
            throwable, mechanism, thread.getId(), threadStacktrace.getFrames(), true));
    return exceptions;
  }

  /**
   * Creates a new instance from the given {@code throwable}.
   *
   * @param throwable the {@link Throwable} to build this instance from
   */
  @NotNull
  public List<IchnoisCoreException> getIchnoisCoreExceptions(final @NotNull Throwable throwable) {
    return getIchnoisCoreExceptions(extractExceptionQueue(throwable));
  }

  /**
   * Creates a new instance from the given {@code exceptions}.
   *
   * @param exceptions a {@link Deque} of {@link IchnoisCoreException} to build this instance from
   */
  private @NotNull List<IchnoisCoreException> getIchnoisCoreExceptions(
      final @NotNull Deque<IchnoisCoreException> exceptions) {
    return new ArrayList<>(exceptions);
  }

  /**
   * Creates a IchnoisCore exception based on a Java Throwable.
   *
   * <p>The {@code childExceptionStackTrace} parameter is used to define the common frames with the
   * child exception (Exception caused by {@code throwable}).
   *
   * @param throwable Java exception to send to IchnoisCore.
   * @param exceptionMechanism The optional {@link Mechanism} of the {@code throwable}. Or null if
   *     none exist.
   * @param threadId The optional id of a {@link Thread} which the exception originated. Or null if
   *     not known.
   * @param frames stack frames that should be assigned to the stacktrace of this exception.
   * @param snapshot if the captured {@link java.lang.Thread}'s stacktrace is a snapshot, See {@link
   *     IchnoisCoreStackTrace#getSnapshot()}
   */
  private @NotNull IchnoisCoreException getIchnoisCoreException(
      @NotNull final Throwable throwable,
      @Nullable final Mechanism exceptionMechanism,
      @Nullable final Long threadId,
      @Nullable final List<IchnoisCoreStackFrame> frames,
      final boolean snapshot) {

    final Package exceptionPackage = throwable.getClass().getPackage();
    final String fullClassName = throwable.getClass().getName();

    final IchnoisCoreException exception = new IchnoisCoreException();

    final String exceptionMessage = throwable.getMessage();

    final String exceptionClassName =
        exceptionPackage != null
            ? fullClassName.replace(exceptionPackage.getName() + ".", "")
            : fullClassName;

    final String exceptionPackageName =
        exceptionPackage != null ? exceptionPackage.getName() : null;

    if (frames != null && !frames.isEmpty()) {
      final IchnoisCoreStackTrace ichnoisStackTrace = new IchnoisCoreStackTrace(frames);
      if (snapshot) {
        ichnoisStackTrace.setSnapshot(true);
      }
      exception.setStacktrace(ichnoisStackTrace);
    }

    exception.setThreadId(threadId);
    exception.setType(exceptionClassName);
    exception.setMechanism(exceptionMechanism);
    exception.setModule(exceptionPackageName);
    exception.setValue(exceptionMessage);

    return exception;
  }

  /**
   * Transforms a {@link Throwable} into a Queue of {@link IchnoisCoreException}.
   *
   * <p>Multiple values represent chained exceptions and should be sorted oldest to newest.
   *
   * @param throwable throwable to transform in a queue of exceptions.
   * @return a queue of exception with StackTrace.
   */
  @TestOnly
  @NotNull
  Deque<IchnoisCoreException> extractExceptionQueue(final @NotNull Throwable throwable) {
    return extractExceptionQueueInternal(
        throwable, new AtomicInteger(-1), new HashSet<>(), new ArrayDeque<>(), null);
  }

  Deque<IchnoisCoreException> extractExceptionQueueInternal(
      final @NotNull Throwable throwable,
      final @NotNull AtomicInteger exceptionId,
      final @NotNull Set<Throwable> circularityDetector,
      final @NotNull Deque<IchnoisCoreException> exceptions,
      @Nullable String mechanismTypeOverride) {
    Mechanism exceptionMechanism;
    Thread thread;

    Throwable currentThrowable = throwable;
    int parentId = exceptionId.get();

    // Stack the exceptions to send them in the reverse order
    while (currentThrowable != null && circularityDetector.add(currentThrowable)) {
      boolean snapshot = false;
      final @NotNull String mechanismType =
          mechanismTypeOverride == null ? "chained" : mechanismTypeOverride;
      if (currentThrowable instanceof ExceptionMechanismException) {
        // this is for ANR I believe
        ExceptionMechanismException exceptionMechanismThrowable =
            (ExceptionMechanismException) currentThrowable;
        exceptionMechanism = exceptionMechanismThrowable.getExceptionMechanism();
        currentThrowable = exceptionMechanismThrowable.getThrowable();
        thread = exceptionMechanismThrowable.getThread();
        snapshot = exceptionMechanismThrowable.isSnapshot();
      } else {
        exceptionMechanism = new Mechanism();
        thread = Thread.currentThread();
      }

      final boolean includeIchnoisCoreFrames = Boolean.FALSE.equals(exceptionMechanism.isHandled());
      final List<IchnoisCoreStackFrame> frames =
          ichnoisStackTraceFactory.getStackFrames(
              currentThrowable.getStackTrace(), includeIchnoisCoreFrames);
      final @Nullable Long threadId = thread != null ? thread.getId() : null;
      IchnoisCoreException exception =
          getIchnoisCoreException(currentThrowable, exceptionMechanism, threadId, frames, snapshot);
      exceptions.addFirst(exception);

      if (exceptionMechanism.getType() == null) {
        exceptionMechanism.setType(mechanismType);
      }

      if (exceptionId.get() >= 0) {
        exceptionMechanism.setParentId(parentId);
      }

      final int currentExceptionId = exceptionId.incrementAndGet();
      exceptionMechanism.setExceptionId(currentExceptionId);

      Throwable[] suppressed = currentThrowable.getSuppressed();
      if (suppressed != null && suppressed.length > 0) {
        // Disabled for now as it causes grouping in IchnoisCore to sometimes use
        // the suppressed exception as main exception.
        // exceptionMechanism.setExceptionGroup(true);
        for (Throwable suppressedThrowable : suppressed) {
          extractExceptionQueueInternal(
              suppressedThrowable, exceptionId, circularityDetector, exceptions, "suppressed");
        }
      }
      currentThrowable = currentThrowable.getCause();
      parentId = currentExceptionId;
      mechanismTypeOverride = null;
    }

    return exceptions;
  }
}
