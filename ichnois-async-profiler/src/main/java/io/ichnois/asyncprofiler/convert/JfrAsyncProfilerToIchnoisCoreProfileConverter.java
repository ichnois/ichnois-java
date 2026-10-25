package io.ichnois.asyncprofiler.convert;

import io.ichnois.DateUtils;
import io.ichnois.ILogger;
import io.ichnois.IchnoisCore;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreStackTraceFactory;
import io.ichnois.protocol.IchnoisCoreStackFrame;
import io.ichnois.protocol.profiling.IchnoisCoreProfile;
import io.ichnois.protocol.profiling.IchnoisCoreSample;
import io.ichnois.protocol.profiling.IchnoisCoreThreadMetadata;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import one.convert.Arguments;
import one.convert.JfrConverter;
import one.jfr.JfrReader;
import one.jfr.StackTrace;
import one.jfr.event.Event;
import one.jfr.event.EventCollector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class JfrAsyncProfilerToIchnoisCoreProfileConverter extends JfrConverter {
  private static final long UNKNOWN_THREAD_ID = -1;

  private final @NotNull IchnoisCoreProfile ichnoisProfile = new IchnoisCoreProfile();
  private final @NotNull IchnoisCoreStackTraceFactory stackTraceFactory;
  private final @NotNull ILogger logger;
  private final @NotNull Map<IchnoisCoreStackFrame, Integer> frameDeduplicationMap = new HashMap<>();
  private final @NotNull Map<List<Integer>, Integer> stackDeduplicationMap = new HashMap<>();

  public JfrAsyncProfilerToIchnoisCoreProfileConverter(
      JfrReader jfr,
      Arguments args,
      @NotNull IchnoisCoreStackTraceFactory stackTraceFactory,
      @NotNull ILogger logger) {
    super(jfr, args);
    this.stackTraceFactory = stackTraceFactory;
    this.logger = logger;
  }

  @Override
  protected void convertChunk() {
    collector.forEach(new ProfileEventVisitor(ichnoisProfile, stackTraceFactory, jfr, args));
  }

  @Override
  protected EventCollector createCollector(Arguments args) {
    return new NonAggregatingEventCollector();
  }

  public static @NotNull IchnoisCoreProfile convertFromFileStatic(@NotNull String jfrFilePath)
      throws IOException {
    JfrAsyncProfilerToIchnoisCoreProfileConverter converter;
    try (JfrReader jfrReader = new JfrReader(jfrFilePath)) {
      Arguments args = new Arguments();
      args.cpu = false;
      args.wall = true;
      args.alloc = false;
      args.threads = true;
      args.lines = true;
      args.dot = true;

      IchnoisCoreStackTraceFactory stackTraceFactory =
          new IchnoisCoreStackTraceFactory(IchnoisCore.getGlobalScope().getOptions());
      ILogger logger = IchnoisCore.getGlobalScope().getOptions().getLogger();
      converter =
          new JfrAsyncProfilerToIchnoisCoreProfileConverter(jfrReader, args, stackTraceFactory, logger);
      converter.convert();
    }

    return converter.ichnoisProfile;
  }

  private class ProfileEventVisitor implements EventCollector.Visitor {
    private final @NotNull IchnoisCoreProfile ichnoisProfile;
    private final @NotNull IchnoisCoreStackTraceFactory stackTraceFactory;
    private final @NotNull JfrReader jfr;
    private final @NotNull Arguments args;

    ProfileEventVisitor(
        @NotNull IchnoisCoreProfile ichnoisProfile,
        @NotNull IchnoisCoreStackTraceFactory stackTraceFactory,
        @NotNull JfrReader jfr,
        @NotNull Arguments args) {
      this.ichnoisProfile = ichnoisProfile;
      this.stackTraceFactory = stackTraceFactory;
      this.jfr = jfr;
      this.args = args;
    }

    @Override
    public void visit(Event event, long samples, long value) {
      try {
        StackTrace stackTrace = jfr.stackTraces.get(event.stackTraceId);
        long threadId = resolveThreadId(event.tid);

        if (stackTrace != null) {
          if (args.threads) {
            processThreadMetadata(event, threadId);
          }

          processSampleWithStack(event, threadId, stackTrace);
        }
      } catch (Exception e) {
        logger.log(IchnoisCoreLevel.WARNING, "Failed to process JFR event " + event, e);
      }
    }

    private long resolveThreadId(int eventId) {
      Long javaThreadId = jfr.javaThreads.get(eventId);
      return javaThreadId != null ? javaThreadId : UNKNOWN_THREAD_ID;
    }

    private void processThreadMetadata(Event event, long threadId) {
      if (threadId == UNKNOWN_THREAD_ID) {
        return;
      }

      final String threadName = getPlainThreadName(event.tid);
      ichnoisProfile
          .getThreadMetadata()
          .computeIfAbsent(
              String.valueOf(threadId),
              k -> {
                IchnoisCoreThreadMetadata metadata = new IchnoisCoreThreadMetadata();
                metadata.setName(threadName);
                metadata.setPriority(0); // Default priority
                return metadata;
              });
    }

    private void processSampleWithStack(Event event, long threadId, StackTrace stackTrace) {
      int stackIndex = addStackTrace(stackTrace);

      IchnoisCoreSample sample = new IchnoisCoreSample();
      sample.setTimestamp(calculateTimestamp(event));
      sample.setThreadId(String.valueOf(threadId));
      sample.setStackId(stackIndex);

      ichnoisProfile.getSamples().add(sample);
    }

    private double calculateTimestamp(Event event) {
      return DateUtils.nanosToSeconds(jfr.eventTimeToNanos(event.time));
    }

    private int addStackTrace(StackTrace stackTrace) {
      List<Integer> callStack = createFramesAndCallStack(stackTrace);

      Integer existingIndex = stackDeduplicationMap.get(callStack);
      if (existingIndex != null) {
        return existingIndex;
      }

      int stackIndex = ichnoisProfile.getStacks().size();
      ichnoisProfile.getStacks().add(callStack);
      stackDeduplicationMap.put(callStack, stackIndex);
      return stackIndex;
    }

    private List<Integer> createFramesAndCallStack(StackTrace stackTrace) {
      List<Integer> callStack = new ArrayList<>();

      long[] methods = stackTrace.methods;
      byte[] types = stackTrace.types;
      int[] locations = stackTrace.locations;

      for (int i = 0; i < methods.length; i++) {
        StackTraceElement element = getStackTraceElement(methods[i], types[i], locations[i]);
        if (element.isNativeMethod() || isNativeFrame(types[i])) {
          continue;
        }

        IchnoisCoreStackFrame frame = createStackFrame(element);
        int frameIndex = getOrAddFrame(frame);
        callStack.add(frameIndex);
      }

      return callStack;
    }

    // Get existing frame index or add new frame and return its index
    private int getOrAddFrame(IchnoisCoreStackFrame frame) {
      Integer existingIndex = frameDeduplicationMap.get(frame);

      if (existingIndex != null) {
        return existingIndex;
      }

      int newIndex = ichnoisProfile.getFrames().size();
      ichnoisProfile.getFrames().add(frame);
      frameDeduplicationMap.put(frame, newIndex);
      return newIndex;
    }

    private IchnoisCoreStackFrame createStackFrame(StackTraceElement element) {
      IchnoisCoreStackFrame frame = new IchnoisCoreStackFrame();
      final String classNameWithLambdas = element.getClassName().replace("/", ".");
      frame.setFunction(element.getMethodName());

      String sanitizedClassName = extractSanitizedClassName(classNameWithLambdas);
      frame.setModule(extractModuleName(sanitizedClassName, classNameWithLambdas));

      if (shouldMarkAsSystemFrame(element, classNameWithLambdas)) {
        frame.setInApp(false);
      } else {
        frame.setInApp(stackTraceFactory.isInApp(sanitizedClassName));
      }

      frame.setLineno(extractLineNumber(element));
      frame.setFilename(classNameWithLambdas);

      return frame;
    }

    // Remove lambda suffix from class name
    private String extractSanitizedClassName(String classNameWithLambdas) {
      int firstDollar = classNameWithLambdas.indexOf('$');
      if (firstDollar != -1) {
        return classNameWithLambdas.substring(0, firstDollar);
      }
      return classNameWithLambdas;
    }

    // TODO: test difference between null and empty string for module
    private @Nullable String extractModuleName(
        String sanitizedClassName, String classNameWithLambdas) {
      if (hasPackageStructure(sanitizedClassName)) {
        return sanitizedClassName;
      } else if (isRegularClassWithoutPackage(classNameWithLambdas)) {
        return "";
      } else {
        return null;
      }
    }

    private String getPlainThreadName(int tid) {
      String threadName = jfr.threads.get(tid);
      return threadName == null ? "[tid=" + tid + ']' : threadName;
    }

    private boolean hasPackageStructure(String className) {
      return className.lastIndexOf('.') > 0;
    }

    private boolean isRegularClassWithoutPackage(String className) {
      return !className.startsWith("[");
    }

    private boolean shouldMarkAsSystemFrame(StackTraceElement element, String className) {
      return element.isNativeMethod() || className.isEmpty();
    }

    private @Nullable Integer extractLineNumber(StackTraceElement element) {
      return element.getLineNumber() != 0 ? element.getLineNumber() : null;
    }
  }
}
