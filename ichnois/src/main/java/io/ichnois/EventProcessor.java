package io.ichnois;

import io.ichnois.protocol.IchnoisCoreTransaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An Event Processor that may mutate or drop an event. Runs for IchnoisCoreEvent or IchnoisCoreTransaction
 */
public interface EventProcessor {

  /**
   * May mutate or drop a IchnoisCoreEvent
   *
   * @param event the IchnoisCoreEvent
   * @param hint the Hint
   * @return the event itself, a mutated IchnoisCoreEvent or null
   */
  @Nullable
  default IchnoisCoreEvent process(@NotNull IchnoisCoreEvent event, @NotNull Hint hint) {
    return event;
  }

  /**
   * May mutate or drop a IchnoisCoreTransaction
   *
   * @param transaction the IchnoisCoreTransaction
   * @param hint the Hint
   * @return the event itself, a mutated IchnoisCoreTransaction or null
   */
  @Nullable
  default IchnoisCoreTransaction process(@NotNull IchnoisCoreTransaction transaction, @NotNull Hint hint) {
    return transaction;
  }

  /**
   * May mutate or drop a IchnoisCoreEvent
   *
   * @param event the IchnoisCoreEvent
   * @param hint the Hint
   * @return the event itself, a mutated IchnoisCoreEvent or null
   */
  @Nullable
  default IchnoisCoreReplayEvent process(@NotNull IchnoisCoreReplayEvent event, @NotNull Hint hint) {
    return event;
  }

  /**
   * May mutate or drop a IchnoisCoreLogEvent
   *
   * @param event the IchnoisCoreLogEvent
   * @return the event itself, a mutated IchnoisCoreLogEvent or null
   */
  @Nullable
  default IchnoisCoreLogEvent process(@NotNull IchnoisCoreLogEvent event) {
    return event;
  }

  /**
   * May mutate or drop a IchnoisCoreMetricsEvent
   *
   * @param event the IchnoisCoreMetricsEvent
   * @return the event itself, a mutated IchnoisCoreMetricsEvent or null
   */
  @Nullable
  default IchnoisCoreMetricsEvent process(@NotNull IchnoisCoreMetricsEvent event, @NotNull Hint hint) {
    return event;
  }

  /**
   * Controls when this EventProcessor is invoked.
   *
   * @return order higher number = later, lower number = earlier (negative values may also be
   *     passed), null = latest (note: multiple event processors using null may lead to random
   *     ordering)
   */
  default @Nullable Long getOrder() {
    return null;
  }
}
