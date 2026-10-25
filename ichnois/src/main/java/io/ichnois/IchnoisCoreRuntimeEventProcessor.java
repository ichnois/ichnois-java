package io.ichnois;

import io.ichnois.internal.eventprocessor.IchnoisCoreEventProcessor;
import io.ichnois.protocol.IchnoisCoreRuntime;
import io.ichnois.protocol.IchnoisCoreTransaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Attaches Java vendor and version to events and transactions. */
final class IchnoisCoreRuntimeEventProcessor implements IchnoisCoreEventProcessor {
  private final @Nullable String javaVersion;
  private final @Nullable String javaVendor;

  public IchnoisCoreRuntimeEventProcessor(
      final @Nullable String javaVersion, final @Nullable String javaVendor) {
    this.javaVersion = javaVersion;
    this.javaVendor = javaVendor;
  }

  public IchnoisCoreRuntimeEventProcessor() {
    this(System.getProperty("java.version"), System.getProperty("java.vendor"));
  }

  @Override
  public @NotNull IchnoisCoreEvent process(final @NotNull IchnoisCoreEvent event, final @Nullable Hint hint) {
    return process(event);
  }

  @Override
  public @NotNull IchnoisCoreTransaction process(
      final @NotNull IchnoisCoreTransaction transaction, final @Nullable Hint hint) {
    return process(transaction);
  }

  private <T extends IchnoisCoreBaseEvent> @NotNull T process(final @NotNull T event) {
    if (event.getContexts().getRuntime() == null) {
      event.getContexts().setRuntime(new IchnoisCoreRuntime());
    }
    final IchnoisCoreRuntime runtime = event.getContexts().getRuntime();
    if (runtime != null && runtime.getName() == null && runtime.getVersion() == null) {
      runtime.setName(javaVendor);
      runtime.setVersion(javaVersion);
    }
    return event;
  }

  @Override
  public @Nullable Long getOrder() {
    return 2000L;
  }
}
