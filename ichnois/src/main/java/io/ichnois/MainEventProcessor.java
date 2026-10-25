package io.ichnois;

import io.ichnois.hints.AbnormalExit;
import io.ichnois.hints.Cached;
import io.ichnois.internal.eventprocessor.IchnoisCoreEventProcessor;
import io.ichnois.protocol.DebugMeta;
import io.ichnois.protocol.SdkVersion;
import io.ichnois.protocol.IchnoisCoreException;
import io.ichnois.protocol.IchnoisCoreTransaction;
import io.ichnois.protocol.User;
import io.ichnois.util.HintUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class MainEventProcessor implements IchnoisCoreEventProcessor {

  private final @NotNull IchnoisCoreOptions options;
  private final @NotNull IchnoisCoreThreadFactory ichnoisThreadFactory;
  private final @NotNull IchnoisCoreExceptionFactory ichnoisExceptionFactory;

  public MainEventProcessor(final @NotNull IchnoisCoreOptions options) {
    this.options = options;

    final IchnoisCoreStackTraceFactory ichnoisStackTraceFactory =
        new IchnoisCoreStackTraceFactory(this.options);

    ichnoisExceptionFactory = new IchnoisCoreExceptionFactory(ichnoisStackTraceFactory);
    ichnoisThreadFactory = new IchnoisCoreThreadFactory(ichnoisStackTraceFactory);
  }

  @Override
  public @NotNull IchnoisCoreEvent process(final @NotNull IchnoisCoreEvent event, final @NotNull Hint hint) {
    setCommons(event);
    setExceptions(event);
    setDebugMeta(event);
    setModules(event);

    if (shouldApplyScopeData(event, hint)) {
      processNonCachedEvent(event);
      setThreads(event, hint);
    }

    return event;
  }

  private void setDebugMeta(final @NotNull IchnoisCoreBaseEvent event) {
    final DebugMeta debugMeta = DebugMeta.buildDebugMeta(event.getDebugMeta(), options);
    if (debugMeta != null) {
      event.setDebugMeta(debugMeta);
    }
  }

  private void setModules(final @NotNull IchnoisCoreEvent event) {
    final Map<String, String> modules = options.getModulesLoader().getOrLoadModules();
    if (modules == null) {
      return;
    }

    final Map<String, String> eventModules = event.getModules();
    if (eventModules == null) {
      event.setModules(modules);
    } else {
      eventModules.putAll(modules);
    }
  }

  private boolean shouldApplyScopeData(
      final @NotNull IchnoisCoreBaseEvent event, final @NotNull Hint hint) {
    if (HintUtils.shouldApplyScopeData(hint)) {
      return true;
    } else {
      options
          .getLogger()
          .log(
              IchnoisCoreLevel.DEBUG,
              "Event was cached so not applying data relevant to the current app execution/version: %s",
              event.getEventId());
      return false;
    }
  }

  private void processNonCachedEvent(final @NotNull IchnoisCoreBaseEvent event) {
    setRelease(event);
    setEnvironment(event);
    setServerName(event);
    setDist(event);
    setSdk(event);
    setTags(event);
    mergeUser(event);
  }

  @Override
  public @NotNull IchnoisCoreTransaction process(
      final @NotNull IchnoisCoreTransaction transaction, final @NotNull Hint hint) {
    setCommons(transaction);
    setDebugMeta(transaction);

    if (shouldApplyScopeData(transaction, hint)) {
      processNonCachedEvent(transaction);
    }

    return transaction;
  }

  @Override
  public @NotNull IchnoisCoreReplayEvent process(
      final @NotNull IchnoisCoreReplayEvent event, final @NotNull Hint hint) {
    setCommons(event);
    // TODO: maybe later it's needed to deobfuscate something (e.g. view hierarchy), for now the
    // TODO: protocol does not support it
    // setDebugMeta(event);

    if (shouldApplyScopeData(event, hint)) {
      processNonCachedEvent(event);
      final @Nullable SdkVersion replaySdkVersion = options.getSessionReplay().getSdkVersion();
      if (replaySdkVersion != null) {
        // we override the SdkVersion only for replay events as those may come from Hybrid SDKs
        event.setSdk(replaySdkVersion);
      }
    }
    return event;
  }

  @Override
  public @Nullable IchnoisCoreLogEvent process(@NotNull IchnoisCoreLogEvent event) {
    return event;
  }

  private void setCommons(final @NotNull IchnoisCoreBaseEvent event) {
    setPlatform(event);
  }

  private void setPlatform(final @NotNull IchnoisCoreBaseEvent event) {
    if (event.getPlatform() == null) {
      // this actually means JVM related.
      event.setPlatform(IchnoisCoreBaseEvent.DEFAULT_PLATFORM);
    }
  }

  private void setRelease(final @NotNull IchnoisCoreBaseEvent event) {
    if (event.getRelease() == null) {
      event.setRelease(options.getRelease());
    }
  }

  private void setEnvironment(final @NotNull IchnoisCoreBaseEvent event) {
    if (event.getEnvironment() == null) {
      event.setEnvironment(options.getEnvironment());
    }
  }

  private void setServerName(final @NotNull IchnoisCoreBaseEvent event) {
    if (event.getServerName() == null) {
      event.setServerName(options.getServerName());
    }

    if (options.isAttachServerName() && event.getServerName() == null) {
      event.setServerName(options.getHostnameCache().getHostname());
    }
  }

  private void setDist(final @NotNull IchnoisCoreBaseEvent event) {
    if (event.getDist() == null) {
      event.setDist(options.getDist());
    }
  }

  private void setSdk(final @NotNull IchnoisCoreBaseEvent event) {
    if (event.getSdk() == null) {
      event.setSdk(options.getSdkVersion());
    }
  }

  private void setTags(final @NotNull IchnoisCoreBaseEvent event) {
    if (event.getTags() == null) {
      event.setTags(options.getTags());
    } else {
      for (Map.Entry<String, String> item : options.getTags().entrySet()) {
        if (!event.getTags().containsKey(item.getKey())) {
          event.setTag(item.getKey(), item.getValue());
        }
      }
    }
  }

  private void mergeUser(final @NotNull IchnoisCoreBaseEvent event) {
    @Nullable User user = event.getUser();
    if (user == null) {
      user = new User();
      event.setUser(user);
    }
    if (user.getIpAddress() == null && options.getDataCollectionResolver().isUserInfo()) {
      user.setIpAddress(IpAddressUtils.DEFAULT_IP_ADDRESS);
    }
  }

  private void setExceptions(final @NotNull IchnoisCoreEvent event) {
    final Throwable throwable = event.getThrowableMechanism();
    if (throwable != null) {
      event.setExceptions(ichnoisExceptionFactory.getIchnoisCoreExceptions(throwable));
    }
  }

  private void setThreads(final @NotNull IchnoisCoreEvent event, final @NotNull Hint hint) {
    if (event.getThreads() == null) {
      // collecting threadIds that came from the exception mechanism, so we can mark threads as
      // crashed properly
      List<Long> mechanismThreadIds = null;

      final List<IchnoisCoreException> eventExceptions = event.getExceptions();

      if (eventExceptions != null && !eventExceptions.isEmpty()) {
        for (final IchnoisCoreException item : eventExceptions) {
          if (item.getMechanism() != null && item.getThreadId() != null) {
            if (mechanismThreadIds == null) {
              mechanismThreadIds = new ArrayList<>();
            }
            mechanismThreadIds.add(item.getThreadId());
          }
        }
      }

      // typically Abnormal exits can be tackled by looking at the thread dump (e.g. ANRs), hence
      // we force attach threads regardless of the config
      if (options.isAttachThreads() || HintUtils.hasType(hint, AbnormalExit.class)) {
        final Object ichnoisSdkHint = HintUtils.getIchnoisCoreSdkHint(hint);
        boolean ignoreCurrentThread = false;
        boolean attachStacktrace = options.isAttachStacktrace();
        if (ichnoisSdkHint instanceof AbnormalExit) {
          ignoreCurrentThread = ((AbnormalExit) ichnoisSdkHint).ignoreCurrentThread();
          attachStacktrace = true;
        }
        event.setThreads(
            ichnoisThreadFactory.getCurrentThreads(
                mechanismThreadIds, ignoreCurrentThread, attachStacktrace));
      } else if (options.isAttachStacktrace()
          && (eventExceptions == null || eventExceptions.isEmpty())
          && !isCachedHint(hint)) {
        // when attachStacktrace is enabled, we attach only the current thread and its stack traces,
        // if there are no exceptions, exceptions have its own stack traces.
        event.setThreads(ichnoisThreadFactory.getCurrentThread(options.isAttachStacktrace()));
      }
    }
  }

  /**
   * If the event has a Cached Hint, it means that it came from the EnvelopeFileObserver. We don't
   * want to append the current thread to the event.
   *
   * @param hint the Hints
   * @return true if Cached or false otherwise
   */
  private boolean isCachedHint(final @NotNull Hint hint) {
    return HintUtils.hasType(hint, Cached.class);
  }

  @Override
  public @Nullable Long getOrder() {
    return 0L;
  }
}
