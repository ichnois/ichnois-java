package io.ichnois.spring.jakarta;

import io.ichnois.Hint;
import io.ichnois.IchnoisCoreEvent;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.internal.eventprocessor.IchnoisCoreEventProcessor;
import io.ichnois.util.CollectionUtils;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.MDC;

/**
 * Attaches context tags defined in {@link IchnoisCoreOptions#getContextTags()} from {@link MDC} to
 * {@link IchnoisCoreEvent#getTags()}.
 */
public final class ContextTagsEventProcessor implements IchnoisCoreEventProcessor {
  private final IchnoisCoreOptions options;

  public ContextTagsEventProcessor(final @NotNull IchnoisCoreOptions options) {
    this.options = options;
  }

  @Override
  public @NotNull IchnoisCoreEvent process(@NotNull IchnoisCoreEvent event, @Nullable Hint hint) {
    final Map<String, String> contextMap = MDC.getCopyOfContextMap();
    if (contextMap != null) {
      final Map<String, String> mdcProperties =
          CollectionUtils.filterMapEntries(contextMap, entry -> entry.getValue() != null);
      if (!mdcProperties.isEmpty() && !options.getContextTags().isEmpty()) {
        for (final String contextTag : options.getContextTags()) {
          // if mdc tag is listed in IchnoisCoreOptions, apply as event tag
          if (mdcProperties.containsKey(contextTag)) {
            event.setTag(contextTag, mdcProperties.get(contextTag));
          }
        }
      }
    }
    return event;
  }

  @Override
  public @Nullable Long getOrder() {
    return 14000L;
  }
}
