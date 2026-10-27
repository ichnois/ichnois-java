package io.ichnois.jul;

import static io.ichnois.TypeCheckHint.JUL_LOG_RECORD;
import static io.ichnois.TypeCheckHint.ICHNOIS_SYNTHETIC_EXCEPTION;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.Breadcrumb;
import io.ichnois.Hint;
import io.ichnois.InitPriority;
import io.ichnois.ScopesAdapter;
import io.ichnois.IchnoisCore;
import io.ichnois.IchnoisCoreAttribute;
import io.ichnois.IchnoisCoreAttributes;
import io.ichnois.IchnoisCoreEvent;
import io.ichnois.IchnoisCoreIntegrationPackageStorage;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreLogLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.exception.ExceptionMechanismException;
import io.ichnois.logger.IchnoisCoreLogParameters;
import io.ichnois.protocol.Mechanism;
import io.ichnois.protocol.Message;
import io.ichnois.protocol.SdkVersion;
import io.ichnois.util.CollectionUtils;
import io.ichnois.util.LoggerPropertiesUtil;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.MDC;

/** Logging handler in charge of sending the java.util.logging records to a IchnoisCore server. */
// temp fix for JDK 21: Constructors call overridable methods; making them final would break the
// public API.
@SuppressWarnings("this-escape")
@Open
public class IchnoisCoreHandler extends Handler {
  public static final String MECHANISM_TYPE = "JulIchnoisCoreHandler";

  /** Name of the {@link IchnoisCoreEvent} extra property containing the Thread id. */
  public static final String THREAD_ID = "thread_id";

  /**
   * If true, <code>String.format()</code> is used to render parameterized log messages instead of
   * <code>MessageFormat.format()</code>; Defaults to false.
   */
  private boolean printfStyle;

  private @NotNull Level minimumBreadcrumbLevel = Level.INFO;
  private @NotNull Level minimumEventLevel = Level.SEVERE;
  private @NotNull Level minimumLevel = Level.INFO;

  static {
    IchnoisCoreIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.github.ichnois:ichnois-jul", BuildConfig.VERSION_NAME);
  }

  /** Creates an instance of IchnoisCoreHandler. */
  public IchnoisCoreHandler() {
    this(new IchnoisCoreOptions());
  }

  /**
   * Creates an instance of IchnoisCoreHandler.
   *
   * @param options the IchnoisCoreOptions
   */
  public IchnoisCoreHandler(final @NotNull IchnoisCoreOptions options) {
    this(options, true, true);
  }

  /**
   * Creates an instance of IchnoisCoreHandler.
   *
   * @param options the IchnoisCoreOptions
   * @param enableExternalConfiguration whether external options like ichnois.properties and ENV vars
   *     should be parsed
   */
  public IchnoisCoreHandler(
      final @NotNull IchnoisCoreOptions options, final boolean enableExternalConfiguration) {
    this(options, true, enableExternalConfiguration);
  }

  /** Creates an instance of IchnoisCoreHandler. */
  @TestOnly
  IchnoisCoreHandler(
      final @NotNull IchnoisCoreOptions options,
      final boolean configureFromLogManager,
      final boolean enableExternalConfiguration) {
    setFilter(new DropIchnoisCoreFilter());
    if (configureFromLogManager) {
      retrieveProperties();
    }
    options.setEnableExternalConfiguration(enableExternalConfiguration);
    options.setInitPriority(InitPriority.LOWEST);
    options.setSdkVersion(createSdkVersion(options));
    IchnoisCore.init(options);
    addPackageAndIntegrationInfo();
  }

  @Override
  public void publish(final @NotNull LogRecord record) {
    // Do not log the event if the current thread is managed by ichnois
    if (!isLoggable(record)) {
      return;
    }
    try {
      if (ScopesAdapter.getInstance().getOptions().getLogs().isEnabled()
          && record.getLevel().intValue() >= minimumLevel.intValue()) {
        captureLog(record);
      }
      if (record.getLevel().intValue() >= minimumEventLevel.intValue()) {
        final Hint hint = new Hint();
        hint.set(ICHNOIS_SYNTHETIC_EXCEPTION, record);

        IchnoisCore.captureEvent(createEvent(record), hint);
      }
      if (record.getLevel().intValue() >= minimumBreadcrumbLevel.intValue()) {
        final Hint hint = new Hint();
        hint.set(JUL_LOG_RECORD, record);

        IchnoisCore.addBreadcrumb(createBreadcrumb(record), hint);
      }
    } catch (RuntimeException e) {
      reportError(
          "An exception occurred while creating a new event in IchnoisCore",
          e,
          ErrorManager.WRITE_FAILURE);
    }
  }

  /**
   * Captures a IchnoisCore log from JULs {@link LogRecord}.
   *
   * @param loggingEvent the JUL log record
   */
  // for the Android compatibility we must use old Java Date class
  @SuppressWarnings("JdkObsolete")
  protected void captureLog(@NotNull LogRecord loggingEvent) {
    final @NotNull IchnoisCoreLogLevel ichnoisLevel = toIchnoisCoreLogLevel(loggingEvent.getLevel());

    final @Nullable Object[] arguments = loggingEvent.getParameters();
    final @NotNull IchnoisCoreAttributes attributes = IchnoisCoreAttributes.of();

    @NotNull String message = loggingEvent.getMessage();
    if (loggingEvent.getResourceBundle() != null
        && loggingEvent.getResourceBundle().containsKey(loggingEvent.getMessage())) {
      message = loggingEvent.getResourceBundle().getString(loggingEvent.getMessage());
    }

    final @NotNull String formattedMessage = maybeFormatted(arguments, message);

    if (!formattedMessage.equals(message)) {
      attributes.add(IchnoisCoreAttribute.stringAttribute("ichnois.message.template", message));
    }

    final @Nullable Map<String, String> mdcProperties = MDC.getMDCAdapter().getCopyOfContextMap();
    if (mdcProperties != null) {
      final List<String> contextTags = ScopesAdapter.getInstance().getOptions().getContextTags();
      LoggerPropertiesUtil.applyPropertiesToAttributes(attributes, contextTags, mdcProperties);
    }

    final @NotNull IchnoisCoreLogParameters params = IchnoisCoreLogParameters.create(attributes);
    params.setOrigin("auto.log.jul");

    IchnoisCore.logger().log(ichnoisLevel, params, formattedMessage, arguments);
  }

  private @NotNull String maybeFormatted(
      final @NotNull Object[] arguments, final @NotNull String message) {
    if (arguments != null) {
      try {
        return formatMessage(message, arguments);
      } catch (RuntimeException e) {
        // local formatting failed, sending raw message instead of formatted message
      }
    }

    return message;
  }

  /** Retrieves the properties of the logger. */
  private void retrieveProperties() {
    final LogManager manager = LogManager.getLogManager();
    final String className = IchnoisCoreHandler.class.getName();
    setPrintfStyle(Boolean.parseBoolean(manager.getProperty(className + ".printfStyle")));
    setLevel(parseLevelOrDefault(manager.getProperty(className + ".level")));
    final String minimumBreadCrumbLevel =
        manager.getProperty(className + ".minimumBreadcrumbLevel");
    if (minimumBreadCrumbLevel != null) {
      setMinimumBreadcrumbLevel(parseLevelOrDefault(minimumBreadCrumbLevel));
    }
    final String minimumEventLevel = manager.getProperty(className + ".minimumEventLevel");
    if (minimumEventLevel != null) {
      setMinimumEventLevel(parseLevelOrDefault(minimumEventLevel));
    }
    final String minimumLevel = manager.getProperty(className + ".minimumLevel");
    if (minimumLevel != null) {
      setMinimumLevel(parseLevelOrDefault(minimumLevel));
    }
  }

  /**
   * Transforms a {@link Level} into an {@link IchnoisCoreLevel}.
   *
   * @param level original level as defined in JUL.
   * @return log level used within ichnois.
   */
  private static @Nullable IchnoisCoreLevel formatLevel(final @NotNull Level level) {
    if (level.intValue() >= Level.SEVERE.intValue()) {
      return IchnoisCoreLevel.ERROR;
    } else if (level.intValue() >= Level.WARNING.intValue()) {
      return IchnoisCoreLevel.WARNING;
    } else if (level.intValue() >= Level.INFO.intValue()) {
      return IchnoisCoreLevel.INFO;
    } else if (level.intValue() >= Level.ALL.intValue()) {
      return IchnoisCoreLevel.DEBUG;
    } else {
      return null;
    }
  }

  /**
   * Transforms a {@link Level} into an {@link IchnoisCoreLogLevel}.
   *
   * @param level original level as defined in JUL.
   * @return log level used within ichnois logs.
   */
  private static @NotNull IchnoisCoreLogLevel toIchnoisCoreLogLevel(final @NotNull Level level) {
    if (level.intValue() >= Level.SEVERE.intValue()) {
      return IchnoisCoreLogLevel.ERROR;
    } else if (level.intValue() >= Level.WARNING.intValue()) {
      return IchnoisCoreLogLevel.WARN;
    } else if (level.intValue() >= Level.INFO.intValue()) {
      return IchnoisCoreLogLevel.INFO;
    } else if (level.intValue() >= Level.FINE.intValue()) {
      return IchnoisCoreLogLevel.DEBUG;
    } else {
      return IchnoisCoreLogLevel.TRACE;
    }
  }

  private @NotNull Level parseLevelOrDefault(final @NotNull String levelName) {
    try {
      return Level.parse(levelName.trim());
    } catch (RuntimeException e) {
      return Level.WARNING;
    }
  }

  private @NotNull Breadcrumb createBreadcrumb(final @NotNull LogRecord record) {
    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setLevel(formatLevel(record.getLevel()));
    breadcrumb.setCategory(record.getLoggerName());
    if (record.getParameters() != null) {
      try {
        breadcrumb.setMessage(formatMessage(record.getMessage(), record.getParameters()));
      } catch (RuntimeException e) {
        breadcrumb.setMessage(record.getMessage());
      }
    } else {
      breadcrumb.setMessage(record.getMessage());
    }
    return breadcrumb;
  }

  /**
   * Creates {@link IchnoisCoreEvent} from JUL's {@link LogRecord}.
   *
   * @param record the log record
   * @return the ichnois event
   */
  // for the Android compatibility we must use old Java Date class
  @SuppressWarnings({"JdkObsolete", "JavaUtilDate", "deprecation"})
  @NotNull
  IchnoisCoreEvent createEvent(final @NotNull LogRecord record) {
    final IchnoisCoreEvent event = new IchnoisCoreEvent(new Date(record.getMillis()));
    event.setLevel(formatLevel(record.getLevel()));
    event.setLogger(record.getLoggerName());

    final Message ichnoisMessage = new Message();
    ichnoisMessage.setParams(toParams(record.getParameters()));

    String message = record.getMessage();
    if (record.getResourceBundle() != null
        && record.getResourceBundle().containsKey(record.getMessage())) {
      message = record.getResourceBundle().getString(record.getMessage());
    }
    ichnoisMessage.setMessage(message);
    if (record.getParameters() != null) {
      try {
        ichnoisMessage.setFormatted(formatMessage(message, record.getParameters()));
      } catch (RuntimeException e) {
        // local formatting failed, send message and parameters without formatted string
      }
    }
    event.setMessage(ichnoisMessage);

    final Throwable throwable = record.getThrown();
    if (throwable != null) {
      final Mechanism mechanism = new Mechanism();
      mechanism.setType(MECHANISM_TYPE);
      final Throwable mechanismException =
          new ExceptionMechanismException(mechanism, throwable, Thread.currentThread());
      event.setThrowable(mechanismException);
    }
    Map<String, String> mdcProperties = MDC.getMDCAdapter().getCopyOfContextMap();
    if (mdcProperties != null) {
      mdcProperties =
          CollectionUtils.filterMapEntries(mdcProperties, entry -> entry.getValue() != null);
      if (!mdcProperties.isEmpty()) {
        // get tags from ScopesAdapter options to allow getting the correct tags if IchnoisCore has been
        // initialized somewhere else
        final List<String> contextTags = ScopesAdapter.getInstance().getOptions().getContextTags();
        LoggerPropertiesUtil.applyPropertiesToEvent(event, contextTags, mdcProperties);
      }
    }
    event.setExtra(THREAD_ID, record.getThreadID());
    return event;
  }

  private @NotNull List<String> toParams(final @Nullable Object[] arguments) {
    final List<String> result = new ArrayList<>();
    if (arguments != null) {
      for (Object argument : arguments) {
        if (argument != null) {
          result.add(argument.toString());
        }
      }
    }
    return result;
  }

  /**
   * Returns formatted Event message when provided the message template and parameters.
   *
   * @param message Message template body.
   * @param parameters Array of parameters for the message.
   * @return Formatted message.
   */
  private @NotNull String formatMessage(
      final @NotNull String message, final @Nullable Object[] parameters) {
    String formatted;
    if (printfStyle) {
      formatted = String.format(message, parameters);
    } else {
      formatted = MessageFormat.format(message, parameters);
    }
    return formatted;
  }

  @Override
  public void flush() {}

  @Override
  public void close() throws SecurityException {
    try {
      IchnoisCore.close();
    } catch (RuntimeException e) {
      reportError(
          "An exception occurred while closing the IchnoisCore connection",
          e,
          ErrorManager.CLOSE_FAILURE);
    }
  }

  private @NotNull SdkVersion createSdkVersion(final @NotNull IchnoisCoreOptions ichnoisOptions) {
    SdkVersion sdkVersion = ichnoisOptions.getSdkVersion();

    final String name = BuildConfig.ICHNOIS_JUL_SDK_NAME;
    final String version = BuildConfig.VERSION_NAME;

    sdkVersion = SdkVersion.updateSdkVersion(sdkVersion, name, version);

    return sdkVersion;
  }

  private void addPackageAndIntegrationInfo() {
    IchnoisCoreIntegrationPackageStorage.getInstance().addIntegration("Jul");
  }

  public void setPrintfStyle(final boolean printfStyle) {
    this.printfStyle = printfStyle;
  }

  public void setMinimumBreadcrumbLevel(final @Nullable Level minimumBreadcrumbLevel) {
    if (minimumBreadcrumbLevel != null) {
      this.minimumBreadcrumbLevel = minimumBreadcrumbLevel;
    }
  }

  public @NotNull Level getMinimumBreadcrumbLevel() {
    return minimumBreadcrumbLevel;
  }

  public void setMinimumEventLevel(final @Nullable Level minimumEventLevel) {
    if (minimumEventLevel != null) {
      this.minimumEventLevel = minimumEventLevel;
    }
  }

  public @NotNull Level getMinimumEventLevel() {
    return minimumEventLevel;
  }

  public void setMinimumLevel(final @Nullable Level minimumLevel) {
    if (minimumLevel != null) {
      this.minimumLevel = minimumLevel;
    }
  }

  public @NotNull Level getMinimumLevel() {
    return minimumLevel;
  }

  public boolean isPrintfStyle() {
    return printfStyle;
  }

  private static final class DropIchnoisCoreFilter implements Filter {
    @Override
    public boolean isLoggable(final @NotNull LogRecord record) {
      final String loggerName = record.getLoggerName();
      return loggerName == null
          || !loggerName.startsWith("io.ichnois")
          || loggerName.startsWith("io.ichnois.samples");
    }
  }
}
