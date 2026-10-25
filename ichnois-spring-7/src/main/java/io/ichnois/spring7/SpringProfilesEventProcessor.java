package io.ichnois.spring7;

import io.ichnois.Hint;
import io.ichnois.IchnoisCoreBaseEvent;
import io.ichnois.IchnoisCoreEvent;
import io.ichnois.IchnoisCoreReplayEvent;
import io.ichnois.internal.eventprocessor.IchnoisCoreEventProcessor;
import io.ichnois.protocol.IchnoisCoreTransaction;
import io.ichnois.protocol.Spring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.env.Environment;

/**
 * Attaches the list of active Spring profiles (an empty list if only the default profile is active)
 * to the {@link io.ichnois.TraceContext} associated with the event.
 */
public final class SpringProfilesEventProcessor implements IchnoisCoreEventProcessor {
  private final @NotNull Environment environment;

  @Override
  public @NotNull IchnoisCoreEvent process(final @NotNull IchnoisCoreEvent event, final @NotNull Hint hint) {
    processInternal(event);
    return event;
  }

  @Override
  public @NotNull IchnoisCoreTransaction process(
      final @NotNull IchnoisCoreTransaction transaction, final @NotNull Hint hint) {
    processInternal(transaction);
    return transaction;
  }

  @Override
  public @NotNull IchnoisCoreReplayEvent process(
      final @NotNull IchnoisCoreReplayEvent event, final @NotNull Hint hint) {
    processInternal(event);
    return event;
  }

  private void processInternal(final @NotNull IchnoisCoreBaseEvent event) {
    @Nullable String[] activeProfiles = environment.getActiveProfiles();
    @NotNull Spring springContext = new Spring();
    springContext.setActiveProfiles(activeProfiles);
    event.getContexts().setSpring(springContext);
  }

  public SpringProfilesEventProcessor(final @NotNull Environment environment) {
    this.environment = environment;
  }
}
