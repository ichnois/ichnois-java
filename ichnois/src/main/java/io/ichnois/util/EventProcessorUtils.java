package io.ichnois.util;

import io.ichnois.EventProcessor;
import io.ichnois.internal.eventprocessor.EventProcessorAndOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class EventProcessorUtils {

  public static List<EventProcessor> unwrap(
      final @Nullable List<EventProcessorAndOrder> orderedEventProcessor) {
    final List<EventProcessor> eventProcessors = new ArrayList<>();

    if (orderedEventProcessor != null) {
      for (EventProcessorAndOrder eventProcessorAndOrder : orderedEventProcessor) {
        eventProcessors.add(eventProcessorAndOrder.getEventProcessor());
      }
    }

    return new CopyOnWriteArrayList<>(eventProcessors);
  }
}
