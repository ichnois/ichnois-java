package io.ichnois.spring.boot.jakarta;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import com.jakewharton.nopen.annotation.Open;
import io.ichnois.logback.IchnoisCoreAppender;
import io.ichnois.util.Objects;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.core.ResolvableType;

/** Registers {@link IchnoisCoreAppender} after Spring context gets refreshed. */
@Open
public class IchnoisCoreLogbackInitializer implements GenericApplicationListener {
  private final @NotNull IchnoisCoreProperties ichnoisProperties;
  private final @NotNull List<String> loggers;
  @Nullable private IchnoisCoreAppender ichnoisAppender;

  public IchnoisCoreLogbackInitializer(final @NotNull IchnoisCoreProperties ichnoisProperties) {
    this.ichnoisProperties = Objects.requireNonNull(ichnoisProperties, "properties are required");
    loggers = ichnoisProperties.getLogging().getLoggers();
  }

  @Override
  public boolean supportsEventType(final @NotNull ResolvableType eventType) {
    return eventType.getRawClass() != null
        && ContextRefreshedEvent.class.isAssignableFrom(eventType.getRawClass());
  }

  @Override
  public void onApplicationEvent(final @NotNull ApplicationEvent event) {
    this.loggers.forEach(
        loggerName -> {
          final Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
          if (!isIchnoisCoreAppenderRegistered(logger)) {
            final IchnoisCoreAppender ichnoisAppender = getIchnoisCoreAppender();

            Optional.ofNullable(ichnoisProperties.getLogging().getMinimumBreadcrumbLevel())
                .map(slf4jLevel -> Level.toLevel(slf4jLevel.name()))
                .ifPresent(ichnoisAppender::setMinimumBreadcrumbLevel);
            Optional.ofNullable(ichnoisProperties.getLogging().getMinimumEventLevel())
                .map(slf4jLevel -> Level.toLevel(slf4jLevel.name()))
                .ifPresent(ichnoisAppender::setMinimumEventLevel);
            Optional.ofNullable(ichnoisProperties.getLogging().getMinimumLevel())
                .map(slf4jLevel -> Level.toLevel(slf4jLevel.name()))
                .ifPresent(ichnoisAppender::setMinimumLevel);

            ichnoisAppender.start();
            logger.addAppender(ichnoisAppender);
          }
        });
  }

  @NotNull
  private IchnoisCoreAppender getIchnoisCoreAppender() {
    if (ichnoisAppender == null) {
      ichnoisAppender = new IchnoisCoreAppender();
      ichnoisAppender.setName("ICHNOIS_APPENDER");
      ichnoisAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
    }
    return ichnoisAppender;
  }

  private boolean isIchnoisCoreAppenderRegistered(final @NotNull Logger logger) {
    final Iterator<Appender<ILoggingEvent>> it = logger.iteratorForAppenders();
    while (it.hasNext()) {
      final Appender<ILoggingEvent> appender = it.next();

      if (appender.getClass().equals(IchnoisCoreAppender.class)) {
        return true;
      }
    }
    return false;
  }
}
