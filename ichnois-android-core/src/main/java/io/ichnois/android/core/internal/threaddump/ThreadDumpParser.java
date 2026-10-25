/*
 * Adapted from https://cs.android.com/android/platform/superproject/+/master:development/tools/bugreport/src/com/android/bugreport/stacks/ThreadSnapshotParser.java
 *
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.ichnois.android.core.internal.threaddump;

import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreLockReason;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.IchnoisCoreStackTraceFactory;
import io.ichnois.android.core.internal.util.NativeEventUtils;
import io.ichnois.protocol.ArtContext;
import io.ichnois.protocol.DebugImage;
import io.ichnois.protocol.IchnoisCoreStackFrame;
import io.ichnois.protocol.IchnoisCoreStackTrace;
import io.ichnois.protocol.IchnoisCoreThread;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ThreadDumpParser {
  private static final Pattern BEGIN_MANAGED_THREAD_RE =
      Pattern.compile("\"(.*)\" (.*) ?prio=(\\d+)\\s+tid=(\\d+)\\s*(.*)");

  private static final Pattern BEGIN_UNMANAGED_NATIVE_THREAD_RE =
      Pattern.compile("\"(.*)\" (.*) ?sysTid=(\\d+)");

  // e.g. "----- pid 12345 at 2024-01-01 10:00:00.000000000+0000 -----"
  private static final Pattern PID_RE = Pattern.compile("----- pid (\\d+) at .*");

  // e.g. "  | sysTid=12345 nice=-10 cgrp=top-app sched=0/0 handle=0x7deceb74f8"
  private static final Pattern SYS_TID_RE = Pattern.compile("\\s*\\|\\s*sysTid=(\\d+).*");

  // For reference, see native_stack_dump.cc and tombstone_proto_to_text.cpp in Android sources
  // Groups
  // 0:entire regex
  // 1:index
  // 2:pc
  // 3:mapinfo
  // 4:filename
  // 5:mapoffset
  // 6:function
  // 7:fnoffset
  // 8:buildid
  private static final Pattern NATIVE_RE =
      Pattern.compile(
          // "  native: #12 pc 0xabcd1234"
          " *(?:native: )?#(\\d+) \\S+ ([0-9a-fA-F]+)"
              // The map info includes a filename and an optional offset into the file
              + ("\\s+("
                  // "/path/to/file.ext",
                  + "(.*?)"
                  // optional " (deleted)" suffix (deleted files) needed here to bias regex
                  // correctly
                  + "(?:\\s+\\(deleted\\))?"
                  // " (offset 0xabcd1234)", if the mapping is not into the beginning of the file
                  + "(?:\\s+\\(offset (.*?)\\))?"
                  + ")")
              // Optional function
              + ("(?:\\s+\\((?:"
                  + "\\?\\?\\?" // " (???) marks a missing function, so don't capture it in a group
                  + "|(.*?)(?:\\+(\\d+))?" // " (func+1234)", offset is
                  // optional
                  + ")\\))?")
              // Optional " (BuildId: abcd1234abcd1234abcd1234abcd1234abcd1234)"
              + "(?:\\s+\\(BuildId: (.*?)\\))?");

  private static final Pattern JAVA_RE =
      Pattern.compile(" *at (?:(.+)\\.)?([^.]+)\\.([^.]+)\\((.*):([\\d-]+)\\)");
  private static final Pattern JNI_RE =
      Pattern.compile(" *at (?:(.+)\\.)?([^.]+)\\.([^.]+)\\(Native method\\)");
  private static final Pattern LOCKED_RE =
      Pattern.compile(" *- locked \\<([0x0-9a-fA-F]{1,16})\\> \\(a (?:(.+)\\.)?([^.]+)\\)");
  private static final Pattern SLEEPING_ON_RE =
      Pattern.compile(" *- sleeping on \\<([0x0-9a-fA-F]{1,16})\\> \\(a (?:(.+)\\.)?([^.]+)\\)");
  private static final Pattern WAITING_ON_RE =
      Pattern.compile(" *- waiting on \\<([0x0-9a-fA-F]{1,16})\\> \\(a (?:(.+)\\.)?([^.]+)\\)");
  private static final Pattern WAITING_TO_LOCK_RE =
      Pattern.compile(
          " *- waiting to lock \\<([0x0-9a-fA-F]{1,16})\\> \\(a (?:(.+)\\.)?([^.]+)\\)");
  private static final Pattern WAITING_TO_LOCK_HELD_RE =
      Pattern.compile(
          " *- waiting to lock \\<([0x0-9a-fA-F]{1,16})\\> \\(a (?:(.+)\\.)?([^.]+)\\)"
              + "(?: held by thread (\\d+))");
  private static final Pattern WAITING_TO_LOCK_UNKNOWN_RE =
      Pattern.compile(" *- waiting to lock an unknown object");
  private static final Pattern BLANK_RE = Pattern.compile("\\s+");

  private final @NotNull IchnoisCoreOptions options;

  private final boolean isBackground;

  // the process id parsed from the thread dump header; on Linux/Android the main thread's kernel
  // thread id (sysTid) always equals the process id, so we use it to reliably detect the main
  // thread
  private @Nullable Long processId;

  private final @NotNull IchnoisCoreStackTraceFactory stackTraceFactory;

  private final @NotNull Map<String, DebugImage> debugImages;

  private final @NotNull List<IchnoisCoreThread> threads;

  private final @NotNull ArtContextParser artContextParser = new ArtContextParser();

  public ThreadDumpParser(final @NotNull IchnoisCoreOptions options, final boolean isBackground) {
    this.options = options;
    this.isBackground = isBackground;
    this.stackTraceFactory = new IchnoisCoreStackTraceFactory(options);
    this.debugImages = new HashMap<>();
    this.threads = new ArrayList<>();
  }

  @NotNull
  public List<DebugImage> getDebugImages() {
    return new ArrayList<>(debugImages.values());
  }

  @NotNull
  public List<IchnoisCoreThread> getThreads() {
    return threads;
  }

  @Nullable
  public ArtContext getArtContext() {
    return artContextParser.getArtContext();
  }

  public void parse(final @NotNull Lines lines) {

    final Matcher beginManagedThreadRe = BEGIN_MANAGED_THREAD_RE.matcher("");
    final Matcher beginUnmanagedNativeThreadRe = BEGIN_UNMANAGED_NATIVE_THREAD_RE.matcher("");
    final Matcher pidRe = PID_RE.matcher("");

    while (lines.hasNext()) {
      final Line line = lines.next();
      if (line == null) {
        options.getLogger().log(IchnoisCoreLevel.WARNING, "Internal error while parsing thread dump.");
        return;
      }
      final String text = line.text;
      // we only handle managed threads, as unmanaged/not attached do not have the thread id and
      // our protocol does not support this case
      if (matches(beginManagedThreadRe, text) || matches(beginUnmanagedNativeThreadRe, text)) {
        lines.rewind();

        final IchnoisCoreThread thread = parseThread(lines);
        if (thread != null) {
          threads.add(thread);
        }
      } else if (matches(pidRe, text)) {
        processId = getLong(pidRe, 1, null);
      } else {
        artContextParser.parseLine(text);
      }
    }

    markThreads();
  }

  private IchnoisCoreThread parseThread(final @NotNull Lines lines) {
    final IchnoisCoreThread ichnoisThread = new IchnoisCoreThread();

    final Matcher beginManagedThreadRe = BEGIN_MANAGED_THREAD_RE.matcher("");
    final Matcher beginUnmanagedNativeThreadRe = BEGIN_UNMANAGED_NATIVE_THREAD_RE.matcher("");

    // thread attributes
    if (!lines.hasNext()) {
      return null;
    }
    final Line line = lines.next();
    if (line == null) {
      options.getLogger().log(IchnoisCoreLevel.WARNING, "Internal error while parsing thread dump.");
      return null;
    }
    if (matches(beginManagedThreadRe, line.text)) {
      final Long tid = getLong(beginManagedThreadRe, 4, null);
      if (tid == null) {
        options.getLogger().log(IchnoisCoreLevel.DEBUG, "No thread id in the dump, skipping thread.");
        // tid is required by our protocol
        return null;
      }
      ichnoisThread.setId(tid);
      final String name = beginManagedThreadRe.group(1);
      ichnoisThread.setName(name);
      if ("main".equals(name)) {
        ichnoisThread.setMain(true);
      }
      final String state = beginManagedThreadRe.group(5);
      // sanitizing thread that have more details after their actual state, e.g.
      // "Native (still starting up)" <- we just need "Native" here
      if (state != null) {
        if (state.contains(" ")) {
          ichnoisThread.setState(state.substring(0, state.indexOf(' ')));
        } else {
          ichnoisThread.setState(state);
        }
      }
    } else if (matches(beginUnmanagedNativeThreadRe, line.text)) {
      final Long sysTid = getLong(beginUnmanagedNativeThreadRe, 3, null);
      if (sysTid == null) {
        options.getLogger().log(IchnoisCoreLevel.DEBUG, "No thread id in the dump, skipping thread.");
        // tid is required by our protocol
        return null;
      }
      ichnoisThread.setId(sysTid);
      ichnoisThread.setName(beginUnmanagedNativeThreadRe.group(1));
      if (sysTid.equals(processId)) {
        ichnoisThread.setMain(true);
      }
    }

    // thread stacktrace
    final IchnoisCoreStackTrace stackTrace = parseStacktrace(lines, ichnoisThread);
    final List<IchnoisCoreStackFrame> frames = stackTrace.getFrames();
    if (frames == null || frames.isEmpty()) {
      // skip threads without a stacktrace, they are not actionable
      return null;
    }
    ichnoisThread.setStacktrace(stackTrace);
    return ichnoisThread;
  }

  @NotNull
  private IchnoisCoreStackTrace parseStacktrace(
      final @NotNull Lines lines, final @NotNull IchnoisCoreThread thread) {
    final List<IchnoisCoreStackFrame> frames = new ArrayList<>();
    IchnoisCoreStackFrame lastJavaFrame = null;

    final Matcher nativeRe = NATIVE_RE.matcher("");
    final Matcher javaRe = JAVA_RE.matcher("");
    final Matcher jniRe = JNI_RE.matcher("");
    final Matcher lockedRe = LOCKED_RE.matcher("");
    final Matcher waitingOnRe = WAITING_ON_RE.matcher("");
    final Matcher sleepingOnRe = SLEEPING_ON_RE.matcher("");
    final Matcher waitingToLockHeldRe = WAITING_TO_LOCK_HELD_RE.matcher("");
    final Matcher waitingToLockRe = WAITING_TO_LOCK_RE.matcher("");
    final Matcher waitingToLockUnknownRe = WAITING_TO_LOCK_UNKNOWN_RE.matcher("");
    final Matcher blankRe = BLANK_RE.matcher("");
    final Matcher sysTidRe = SYS_TID_RE.matcher("");

    while (lines.hasNext()) {
      final Line line = lines.next();
      if (line == null) {
        options.getLogger().log(IchnoisCoreLevel.WARNING, "Internal error while parsing thread dump.");
        break;
      }
      final String text = line.text;
      if (matches(sysTidRe, text)) {
        final Long sysTid = getLong(sysTidRe, 1, null);
        if (sysTid != null && sysTid.equals(processId)) {
          thread.setMain(true);
        }
      } else if (matches(javaRe, text)) {
        final IchnoisCoreStackFrame frame = new IchnoisCoreStackFrame();
        final String packageName = javaRe.group(1);
        final String className = javaRe.group(2);
        final String module = toModuleName(packageName, className);
        frame.setModule(module);
        frame.setFunction(javaRe.group(3));
        frame.setFilename(javaRe.group(4));
        frame.setLineno(getUInteger(javaRe, 5, null));
        frame.setInApp(stackTraceFactory.isInApp(module));
        frames.add(frame);
        lastJavaFrame = frame;
      } else if (matches(nativeRe, text)) {
        final IchnoisCoreStackFrame frame = new IchnoisCoreStackFrame();
        frame.setPackage(nativeRe.group(3));
        frame.setFunction(nativeRe.group(6));
        frame.setLineno(getInteger(nativeRe, 7, null));
        frame.setInstructionAddr("0x" + nativeRe.group(2));
        frame.setPlatform("native");

        final String buildId = nativeRe.group(8);
        final String debugId = buildId == null ? null : NativeEventUtils.buildIdToDebugId(buildId);
        if (debugId != null) {
          if (!debugImages.containsKey(debugId)) {
            final DebugImage debugImage = new DebugImage();
            debugImage.setDebugId(debugId);
            debugImage.setType("elf");
            debugImage.setCodeFile(nativeRe.group(4));
            debugImage.setCodeId(buildId);
            debugImages.put(debugId, debugImage);
          }
          // The addresses in the thread dump are relative to the image
          frame.setAddrMode("rel:" + debugId);
        }

        frames.add(frame);
        lastJavaFrame = null;
      } else if (matches(jniRe, text)) {
        final IchnoisCoreStackFrame frame = new IchnoisCoreStackFrame();
        final String packageName = jniRe.group(1);
        final String className = jniRe.group(2);
        final String module = toModuleName(packageName, className);
        frame.setModule(module);
        frame.setFunction(jniRe.group(3));
        frame.setInApp(stackTraceFactory.isInApp(module));
        frame.setNative(true);
        frames.add(frame);
        lastJavaFrame = frame;
      } else if (matches(lockedRe, text)) {
        if (lastJavaFrame != null) {
          final IchnoisCoreLockReason lock = new IchnoisCoreLockReason();
          lock.setType(IchnoisCoreLockReason.LOCKED);
          lock.setAddress(lockedRe.group(1));
          lock.setPackageName(lockedRe.group(2));
          lock.setClassName(lockedRe.group(3));
          lastJavaFrame.setLock(lock);
          combineThreadLocks(thread, lock);
        }
      } else if (matches(waitingOnRe, text)) {
        if (lastJavaFrame != null) {
          final IchnoisCoreLockReason lock = new IchnoisCoreLockReason();
          lock.setType(IchnoisCoreLockReason.WAITING);
          lock.setAddress(waitingOnRe.group(1));
          lock.setPackageName(waitingOnRe.group(2));
          lock.setClassName(waitingOnRe.group(3));
          lastJavaFrame.setLock(lock);
          combineThreadLocks(thread, lock);
        }
      } else if (matches(sleepingOnRe, text)) {
        if (lastJavaFrame != null) {
          final IchnoisCoreLockReason lock = new IchnoisCoreLockReason();
          lock.setType(IchnoisCoreLockReason.SLEEPING);
          lock.setAddress(sleepingOnRe.group(1));
          lock.setPackageName(sleepingOnRe.group(2));
          lock.setClassName(sleepingOnRe.group(3));
          lastJavaFrame.setLock(lock);
          combineThreadLocks(thread, lock);
        }
      } else if (matches(waitingToLockHeldRe, text)) {
        if (lastJavaFrame != null) {
          final IchnoisCoreLockReason lock = new IchnoisCoreLockReason();
          lock.setType(IchnoisCoreLockReason.BLOCKED);
          lock.setAddress(waitingToLockHeldRe.group(1));
          lock.setPackageName(waitingToLockHeldRe.group(2));
          lock.setClassName(waitingToLockHeldRe.group(3));
          lock.setThreadId(getLong(waitingToLockHeldRe, 4, null));
          lastJavaFrame.setLock(lock);
          combineThreadLocks(thread, lock);
        }
      } else if (matches(waitingToLockRe, text)) {
        if (lastJavaFrame != null) {
          final IchnoisCoreLockReason lock = new IchnoisCoreLockReason();
          lock.setType(IchnoisCoreLockReason.BLOCKED);
          lock.setAddress(waitingToLockRe.group(1));
          lock.setPackageName(waitingToLockRe.group(2));
          lock.setClassName(waitingToLockRe.group(3));
          lastJavaFrame.setLock(lock);
          combineThreadLocks(thread, lock);
        }
      } else if (matches(waitingToLockUnknownRe, text)) {
        if (lastJavaFrame != null) {
          final IchnoisCoreLockReason lock = new IchnoisCoreLockReason();
          lock.setType(IchnoisCoreLockReason.BLOCKED);
          lastJavaFrame.setLock(lock);
          combineThreadLocks(thread, lock);
        }
      } else if (text.length() == 0 || matches(blankRe, text)) {
        break;
      }
    }

    // IchnoisCore expects frames to be in reverse order
    Collections.reverse(frames);
    final IchnoisCoreStackTrace stackTrace = new IchnoisCoreStackTrace(frames);
    // it's a thread dump
    stackTrace.setSnapshot(true);
    return stackTrace;
  }

  private @NotNull String toModuleName(
      final @Nullable String packageName, final @NotNull String className) {
    return packageName == null || packageName.isEmpty() ? className : packageName + "." + className;
  }

  private void markThreads() {
    for (final @NotNull IchnoisCoreThread thread : threads) {
      if (Boolean.TRUE.equals(thread.isMain())) {
        // the OS may have renamed the main thread to the (truncated) process name; normalize it
        // back to "main" so downstream consumers see a consistent name
        thread.setName("main");

        // since it's an ANR, the crashed thread will always be main
        thread.setCrashed(true);
        thread.setCurrent(!isBackground);
      } else {
        thread.setCrashed(false);
        thread.setCurrent(false);
        thread.setMain(false);
      }
    }
  }

  private boolean matches(final @NotNull Matcher matcher, final @NotNull String text) {
    matcher.reset(text);
    return matcher.matches();
  }

  private void combineThreadLocks(
      final @NotNull IchnoisCoreThread thread, final @NotNull IchnoisCoreLockReason lockReason) {
    Map<String, IchnoisCoreLockReason> heldLocks = thread.getHeldLocks();
    if (heldLocks == null) {
      heldLocks = new HashMap<>();
    }
    final IchnoisCoreLockReason prev = heldLocks.get(lockReason.getAddress());
    if (prev != null) {
      // higher type prevails as we are tagging thread with the most severe lock reason
      prev.setType(Math.max(prev.getType(), lockReason.getType()));
    } else {
      heldLocks.put(lockReason.getAddress(), new IchnoisCoreLockReason(lockReason));
    }
    thread.setHeldLocks(heldLocks);
  }

  @Nullable
  private Long getLong(
      final @NotNull Matcher matcher, final int group, final @Nullable Long defaultValue) {
    final String str = matcher.group(group);
    if (str == null || str.length() == 0) {
      return defaultValue;
    } else {
      return Long.parseLong(str);
    }
  }

  @Nullable
  private Integer getInteger(
      final @NotNull Matcher matcher, final int groupIndex, final @Nullable Integer defaultValue) {
    final String str = matcher.group(groupIndex);
    if (str == null || str.length() == 0) {
      return defaultValue;
    } else {
      return Integer.parseInt(str);
    }
  }

  @Nullable
  private Integer getUInteger(
      final @NotNull Matcher matcher, final int group, final @Nullable Integer defaultValue) {
    final String str = matcher.group(group);
    if (str == null || str.length() == 0) {
      return defaultValue;
    } else {
      final Integer parsed = Integer.parseInt(str);
      return parsed >= 0 ? parsed : defaultValue;
    }
  }
}
