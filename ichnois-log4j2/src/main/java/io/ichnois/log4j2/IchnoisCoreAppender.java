package io.ichnois.log4j2;

import static io.ichnois.TypeCheckHint.LOG4J_LOG_EVENT;
import static io.ichnois.TypeCheckHint.ICHNOIS_SYNTHETIC_EXCEPTION;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.Breadcrumb;
import io.ichnois.DateUtils;
import io.ichnois.Hint;
import io.ichnois.IScopes;
import io.ichnois.ITransportFactory;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Appender for Log4j2 in charge of sending the logged events to a IchnoisCore server. */
@Plugin(name = "IchnoisCore", category = "Core", elementType = "appender", printObject = true)
@Open
public class IchnoisCoreAppender extends AbstractAppender {
  public static final String MECHANISM_TYPE = "Log4j2IchnoisCoreAppender";

  private final @Nullable String dsn;
  private final @Nullable ITransportFactory transportFactory;
  private @NotNull Level minimumBreadcrumbLevel = Level.INFO;
  private @NotNull Level minimumEventLevel = Level.ERROR;
  private @NotNull Level minimumLevel = Level.INFO;
  private final @Nullable Boolean debug;
  private final @NotNull IScopes scopes;
  private final @Nullable List<String> contextTags;

  static {
    IchnoisCoreIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.github.ichnois:ichnois-log4j2", BuildConfig.VERSION_NAME);
  }

  /**
   * @deprecated This constructor is deprecated. Please use {@link #IchnoisCoreAppender(String, Filter,
   *     String, Level, Level, Level, Boolean, ITransportFactory, IScopes, String[])} instead.
   */
  @Deprecated
  @SuppressWarnings("InlineMeSuggester")
  public IchnoisCoreAppender(
      final @NotNull String name,
      final @Nullable Filter filter,
      final @Nullable String dsn,
      final @Nullable Level minimumBreadcrumbLevel,
      final @Nullable Level minimumEventLevel,
      final @Nullable Boolean debug,
      final @Nullable ITransportFactory transportFactory,
      final @NotNull IScopes scopes,
      final @Nullable String[] contextTags) {
    this(
        name,
        filter,
        dsn,
        minimumBreadcrumbLevel,
        minimumEventLevel,
        null,
        debug,
        transportFactory,
        scopes,
        contextTags);
  }

  public IchnoisCoreAppender(
      final @NotNull String name,
      final @Nullable Filter filter,
      final @Nullable String dsn,
      final @Nullable Level minimumBreadcrumbLevel,
      final @Nullable Level minimumEventLevel,
      final @Nullable Level minimumLevel,
      final @Nullable Boolean debug,
      final @Nullable ITransportFactory transportFactory,
      final @NotNull IScopes scopes,
      final @Nullable String[] contextTags) {
    super(name, filter, null, true, null);
    this.dsn = dsn;
    if (minimumBreadcrumbLevel != null) {
      this.minimumBreadcrumbLevel = minimumBreadcrumbLevel;
    }
    if (minimumEventLevel != null) {
      this.minimumEventLevel = minimumEventLevel;
    }
    if (minimumLevel != null) {
      this.minimumLevel = minimumLevel;
    }
    this.debug = debug;
    this.transportFactory = transportFactory;
    this.scopes = scopes;
    this.contextTags = contextTags != null ? Arrays.asList(contextTags) : null;
  }

  /**
   * Create a IchnoisCore Appender.
   *
   * @param name The name of the Appender.
   * @param minimumBreadcrumbLevel The min. level of the breadcrumb.
   * @param minimumEventLevel The min. level of the event.
   * @param minimumLevel The min. level of the log event.
   * @param dsn the IchnoisCore DSN.
   * @param debug if IchnoisCore debug mode should be on
   * @param filter The filter, if any, to use.
   * @return The IchnoisCoreAppender.
   */
  @PluginFactory
  public static @Nullable IchnoisCoreAppender createAppender(
      @Nullable @PluginAttribute("name") final String name,
      @Nullable @PluginAttribute("minimumBreadcrumbLevel") final Level minimumBreadcrumbLevel,
      @Nullable @PluginAttribute("minimumEventLevel") final Level minimumEventLevel,
      @Nullable @PluginAttribute("minimumLevel") final Level minimumLevel,
      @Nullable @PluginAttribute("dsn") final String dsn,
      @Nullable @PluginAttribute("debug") final Boolean debug,
      @Nullable @PluginElement("filter") final Filter filter,
      @Nullable @PluginAttribute("contextTags") final String contextTags) {

    if (name == null) {
      LOGGER.error("No name provided for IchnoisCoreAppender");
      return null;
    }
    return new IchnoisCoreAppender(
        name,
        filter,
        dsn,
        minimumBreadcrumbLevel,
        minimumEventLevel,
        minimumLevel,
        debug,
        null,
        ScopesAdapter.getInstance(),
        contextTags != null ? contextTags.split(",") : null);
  }

  @Override
  public void start() {
    start(getOptionsConfiguration(null));
  }

  public @NotNull Level getMinimumBreadcrumbLevel() {
    return minimumBreadcrumbLevel;
  }

  public @NotNull Level getMinimumEventLevel() {
    return minimumEventLevel;
  }

  public @NotNull Level getMinimumLevel() {
    return minimumLevel;
  }

  @NotNull
  IchnoisCore.OptionsConfiguration<IchnoisCoreOptions> getOptionsConfiguration(
      final @Nullable IchnoisCore.OptionsConfiguration<IchnoisCoreOptions> additionalOptionsConfiguration) {
    return options -> {
      options.setEnableExternalConfiguration(true);
      options.setInitPriority(InitPriority.LOWEST);
      options.setDsn(dsn);
      if (debug != null) {
        options.setDebug(debug);
      }
      options.setIchnoisClientName(
          BuildConfig.ICHNOIS_LOG4J2_SDK_NAME + "/" + BuildConfig.VERSION_NAME);
      options.setSdkVersion(createSdkVersion(options));
      if (contextTags != null) {
        for (final String contextTag : contextTags) {
          options.addContextTag(contextTag);
        }
      }
      Optional.ofNullable(transportFactory).ifPresent(options::setTransportFactory);
      if (additionalOptionsConfiguration != null) {
        additionalOptionsConfiguration.configure(options);
      }
    };
  }

  void start(final @NotNull IchnoisCore.OptionsConfiguration<IchnoisCoreOptions> optionsConfiguration) {
    try {
      IchnoisCore.init(optionsConfiguration);
    } catch (IllegalArgumentException e) {
      final @Nullable String errorMessage = e.getMessage();
      if (errorMessage == null || !errorMessage.startsWith("DSN is required.")) {
        LOGGER.warn("Failed to init IchnoisCore during appender initialization: " + errorMessage);
      }
    }
    addPackageAndIntegrationInfo();
    super.start();
  }

  @Override
  public void append(final @NotNull LogEvent eventObject) {
    if (scopes.getOptions().getLogs().isEnabled()
        && eventObject.getLevel().isMoreSpecificThan(minimumLevel)) {
      captureLog(eventObject);
    }
    if (eventObject.getLevel().isMoreSpecificThan(minimumEventLevel)) {
      final Hint hint = new Hint();
      hint.set(ICHNOIS_SYNTHETIC_EXCEPTION, eventObject);

      scopes.captureEvent(createEvent(eventObject), hint);
    }
    if (eventObject.getLevel().isMoreSpecificThan(minimumBreadcrumbLevel)) {
      final Hint hint = new Hint();
      hint.set(LOG4J_LOG_EVENT, eventObject);

      scopes.addBreadcrumb(createBreadcrumb(eventObject), hint);
    }
  }

  /**
   * Captures a IchnoisCore log from Log4j2's {@link LogEvent}.
   *
   * @param loggingEvent the log4j2 event
   */
  // for the Android compatibility we must use old Java Date class
  @SuppressWarnings("JdkObsolete")
  protected void captureLog(@NotNull LogEvent loggingEvent) {
    final @NotNull IchnoisCoreLogLevel ichnoisLevel = toIchnoisCoreLogLevel(loggingEvent.getLevel());

    final @Nullable Object[] arguments = loggingEvent.getMessage().getParameters();
    final @NotNull IchnoisCoreAttributes attributes = IchnoisCoreAttributes.of();

    final @Nullable String nonFormattedMessage = loggingEvent.getMessage().getFormat();
    final @NotNull String formattedMessage = loggingEvent.getMessage().getFormattedMessage();

    if (nonFormattedMessage != null && !formattedMessage.equals(nonFormattedMessage)) {
      attributes.add(
          IchnoisCoreAttribute.stringAttribute("ichnois.message.template", nonFormattedMessage));
    }

    final @NotNull Map<String, String> contextData = loggingEvent.getContextData().toMap();
    final @NotNull List<String> contextTags = scopes.getOptions().getContextTags();
    LoggerPropertiesUtil.applyPropertiesToAttributes(attributes, contextTags, contextData);

    final @NotNull IchnoisCoreLogParameters params = IchnoisCoreLogParameters.create(attributes);
    params.setOrigin("auto.log.log4j2");

    scopes.logger().log(ichnoisLevel, params, formattedMessage, arguments);
  }

  /**
   * Creates {@link IchnoisCoreEvent} from Log4j2 {@link LogEvent}.
   *
   * @param loggingEvent the log4j2 event
   * @return the ichnois event
   */
  // for the Android compatibility we must use old Java Date class
  @SuppressWarnings("JdkObsolete")
  protected @NotNull IchnoisCoreEvent createEvent(final @NotNull LogEvent loggingEvent) {
    final IchnoisCoreEvent event = new IchnoisCoreEvent(DateUtils.getDateTime(loggingEvent.getTimeMillis()));
    final Message message = new Message();
    message.setMessage(loggingEvent.getMessage().getFormat());
    message.setFormatted(loggingEvent.getMessage().getFormattedMessage());
    message.setParams(toParams(loggingEvent.getMessage().getParameters()));
    event.setMessage(message);
    event.setLogger(loggingEvent.getLoggerName());
    event.setLevel(formatLevel(loggingEvent.getLevel()));

    final @Nullable Throwable thrown = loggingEvent.getThrown();
    if (thrown != null) {
      final Mechanism mechanism = new Mechanism();
      mechanism.setType(MECHANISM_TYPE);
      final Throwable mechanismException =
          new ExceptionMechanismException(mechanism, thrown, Thread.currentThread());
      event.setThrowable(mechanismException);
    }

    if (loggingEvent.getThreadName() != null) {
      event.setExtra("thread_name", loggingEvent.getThreadName());
    }

    if (loggingEvent.getMarker() != null) {
      event.setExtra("marker", loggingEvent.getMarker().toString());
    }

    final Map<String, String> contextData =
        CollectionUtils.filterMapEntries(
            loggingEvent.getContextData().toMap(), entry -> entry.getValue() != null);
    if (!contextData.isEmpty()) {
      // get tags from ScopesAdapter options to allow getting the correct tags if IchnoisCore has been
      // initialized somewhere else
      final List<String> contextTags = scopes.getOptions().getContextTags();
      LoggerPropertiesUtil.applyPropertiesToEvent(event, contextTags, contextData, "Context Data");
    }

    return event;
  }

  private @NotNull List<String> toParams(final @Nullable Object[] arguments) {
    if (arguments != null) {
      return Arrays.stream(arguments)
          .filter(Objects::nonNull)
          .map(Object::toString)
          .collect(Collectors.toList());
    } else {
      return Collections.emptyList();
    }
  }

  /**
   * Creates {@link Breadcrumb} from log4j2 {@link LogEvent}.
   *
   * @param loggingEvent the log4j2 event
   * @return the ichnois breadcrumb
   */
  protected @NotNull Breadcrumb createBreadcrumb(final @NotNull LogEvent loggingEvent) {
    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setLevel(formatLevel(loggingEvent.getLevel()));
    breadcrumb.setCategory(loggingEvent.getLoggerName());
    breadcrumb.setMessage(loggingEvent.getMessage().getFormattedMessage());
    return breadcrumb;
  }

  /**
   * Transforms a {@link Level} into an {@link IchnoisCoreLevel}.
   *
   * @param level original level as defined in log4j.
   * @return log level used within ichnois.
   */
  private static @NotNull IchnoisCoreLevel formatLevel(final @NotNull Level level) {
    if (level.isMoreSpecificThan(Level.FATAL)) {
      return IchnoisCoreLevel.FATAL;
    } else if (level.isMoreSpecificThan(Level.ERROR)) {
      return IchnoisCoreLevel.ERROR;
    } else if (level.isMoreSpecificThan(Level.WARN)) {
      return IchnoisCoreLevel.WARNING;
    } else if (level.isMoreSpecificThan(Level.INFO)) {
      return IchnoisCoreLevel.INFO;
    } else {
      return IchnoisCoreLevel.DEBUG;
    }
  }

  /**
   * Transforms a {@link Level} into an {@link IchnoisCoreLogLevel}.
   *
   * @param level original level as defined in log4j.
   * @return log level used within ichnois.
   */
  private static @NotNull IchnoisCoreLogLevel toIchnoisCoreLogLevel(final @NotNull Level level) {
    if (level.isMoreSpecificThan(Level.FATAL)) {
      return IchnoisCoreLogLevel.FATAL;
    } else if (level.isMoreSpecificThan(Level.ERROR)) {
      return IchnoisCoreLogLevel.ERROR;
    } else if (level.isMoreSpecificThan(Level.WARN)) {
      return IchnoisCoreLogLevel.WARN;
    } else if (level.isMoreSpecificThan(Level.INFO)) {
      return IchnoisCoreLogLevel.INFO;
    } else if (level.isMoreSpecificThan(Level.DEBUG)) {
      return IchnoisCoreLogLevel.DEBUG;
    } else {
      return IchnoisCoreLogLevel.TRACE;
    }
  }

  private @NotNull SdkVersion createSdkVersion(final @NotNull IchnoisCoreOptions ichnoisOptions) {
    SdkVersion sdkVersion = ichnoisOptions.getSdkVersion();

    final String name = BuildConfig.ICHNOIS_LOG4J2_SDK_NAME;
    final String version = BuildConfig.VERSION_NAME;
    sdkVersion = SdkVersion.updateSdkVersion(sdkVersion, name, version);

    return sdkVersion;
  }

  private void addPackageAndIntegrationInfo() {
    IchnoisCoreIntegrationPackageStorage.getInstance().addIntegration("Log4j");
  }
}
