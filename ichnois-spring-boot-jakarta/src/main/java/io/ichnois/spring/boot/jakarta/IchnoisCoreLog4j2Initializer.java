package io.ichnois.spring.boot.jakarta;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.ScopesAdapter;
import io.ichnois.log4j2.IchnoisCoreAppender;
import io.ichnois.util.Objects;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.GenericApplicationListener;
import org.springframework.core.ResolvableType;

/** Registers {@link IchnoisCoreAppender} after Spring context gets refreshed. */
@Open
public class IchnoisCoreLog4j2Initializer implements GenericApplicationListener {
  private static final Logger logger = LoggerFactory.getLogger(IchnoisCoreLog4j2Initializer.class);
  private static final String ICHNOIS_APPENDER_NAME = "ICHNOIS_APPENDER";

  private final @NotNull IchnoisCoreProperties ichnoisProperties;
  private final @NotNull List<String> loggers;
  @Nullable private IchnoisCoreAppender ichnoisAppender;

  public IchnoisCoreLog4j2Initializer(final @NotNull IchnoisCoreProperties ichnoisProperties) {
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
    final Object context = LogManager.getContext(false);
    if (!(context instanceof LoggerContext loggerContext)) {
      logger.info(
          "IchnoisCore Log4j2 appender was not configured because Log4j2 Core is not the active logging backend. Log4j2 API calls may be routed through SLF4J.");
      return;
    }

    final Configuration configuration = loggerContext.getConfiguration();

    boolean changed = false;
    for (final String loggerName : normalizeLoggerNames(loggers)) {
      final LoggerConfig loggerConfig = getOrCreateLoggerConfig(configuration, loggerName);
      if (!isIchnoisCoreAppenderRegistered(loggerConfig)) {
        loggerConfig.addAppender(getIchnoisCoreAppender(configuration), null, null);
        changed = true;
      }
    }

    if (changed) {
      loggerContext.updateLoggers(configuration);
    }
  }

  private @NotNull LoggerConfig getOrCreateLoggerConfig(
      final @NotNull Configuration configuration, final @NotNull String loggerName) {
    if (LogManager.ROOT_LOGGER_NAME.equals(loggerName)) {
      return configuration.getRootLogger();
    }

    final LoggerConfig loggerConfig = configuration.getLoggerConfig(loggerName);
    if (loggerName.equals(loggerConfig.getName())) {
      return loggerConfig;
    }

    final LoggerConfig newLoggerConfig = new LoggerConfig(loggerName, null, true);
    newLoggerConfig.setParent(loggerConfig);
    configuration.addLogger(loggerName, newLoggerConfig);
    return newLoggerConfig;
  }

  private @NotNull IchnoisCoreAppender getIchnoisCoreAppender(final @NotNull Configuration configuration) {
    if (ichnoisAppender == null) {
      ichnoisAppender =
          new IchnoisCoreAppender(
              ICHNOIS_APPENDER_NAME,
              null,
              null,
              toLog4jLevel(ichnoisProperties.getLogging().getMinimumBreadcrumbLevel()),
              toLog4jLevel(ichnoisProperties.getLogging().getMinimumEventLevel()),
              toLog4jLevel(ichnoisProperties.getLogging().getMinimumLevel()),
              null,
              null,
              ScopesAdapter.getInstance(),
              null);
      ichnoisAppender.start();
      configuration.addAppender(ichnoisAppender);
    }
    return ichnoisAppender;
  }

  private @NotNull Set<String> normalizeLoggerNames(final @NotNull List<String> loggerNames) {
    final Set<String> normalized = new LinkedHashSet<>();
    for (final String loggerName : loggerNames) {
      if (loggerName == null || loggerName.trim().isEmpty()) {
        continue;
      }
      normalized.add(normalizeLoggerName(loggerName.trim()));
    }
    return normalized;
  }

  private boolean isIchnoisCoreAppenderRegistered(final @NotNull LoggerConfig loggerConfig) {
    return loggerConfig.getAppenders().values().stream()
        .anyMatch(appender -> appender.getClass().equals(IchnoisCoreAppender.class));
  }

  private @NotNull String normalizeLoggerName(final @NotNull String loggerName) {
    if (org.slf4j.Logger.ROOT_LOGGER_NAME.equals(loggerName)) {
      return LogManager.ROOT_LOGGER_NAME;
    }
    return loggerName;
  }

  private @Nullable Level toLog4jLevel(final @Nullable org.slf4j.event.Level slf4jLevel) {
    return slf4jLevel == null ? null : Level.getLevel(slf4jLevel.name());
  }
}
