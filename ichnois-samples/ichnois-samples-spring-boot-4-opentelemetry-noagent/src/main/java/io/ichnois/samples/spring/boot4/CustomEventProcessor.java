package io.ichnois.samples.spring.boot4;

import io.ichnois.EventProcessor;
import io.ichnois.Hint;
import io.ichnois.IchnoisCoreEvent;
import io.ichnois.protocol.IchnoisCoreRuntime;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.SpringBootVersion;
import org.springframework.stereotype.Component;

/**
 * Custom {@link EventProcessor} implementation lets modifying {@link IchnoisCoreEvent}s before they are
 * sent to IchnoisCore.
 */
@Component
public class CustomEventProcessor implements EventProcessor {
  private final String springBootVersion;

  public CustomEventProcessor(String springBootVersion) {
    this.springBootVersion = springBootVersion;
  }

  public CustomEventProcessor() {
    this(SpringBootVersion.getVersion());
  }

  @Override
  public @NotNull IchnoisCoreEvent process(@NotNull IchnoisCoreEvent event, @NotNull Hint hint) {
    final IchnoisCoreRuntime runtime = new IchnoisCoreRuntime();
    runtime.setVersion(springBootVersion);
    runtime.setName("Spring Boot");
    event.getContexts().setRuntime(runtime);
    return event;
  }
}
