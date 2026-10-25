package io.ichnois.logback;

import static io.ichnois.TypeCheckHint.LOGBACK_LOGGING_EVENT;
import static io.ichnois.TypeCheckHint.ICHNOIS_SYNTHETIC_EXCEPTION;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.encoder.Encoder;
import com.jakewharton.nopen.annotation.Open;
import io.ichnois.Breadcrumb;
import io.ichnois.DateUtils;
import io.ichnois.Hint;
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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Appender for logback in charge of sending the logged events to a IchnoisCore server. */
@Open
public class IchnoisCoreAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
  public static final String MECHANISM_TYPE = "LogbackIchnoisCoreAppender";
  // WARNING: Do not use these options in here, they are only to be used for startup
  private @NotNull IchnoisCoreOptions options = new IchnoisCoreOptions();
  private @Nullable ITransportFactory transportFactory;
  private @NotNull Level minimumBreadcrumbLevel = Level.INFO;
  private @NotNull Level minimumEventLevel = Level.ERROR;
  private @NotNull Level minimumLevel = Level.INFO;
  private @Nullable Encoder<ILoggingEvent> encoder;
  private boolean includeUnencodedMessage = false;

  static {
    IchnoisCoreIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.ichnois:ichnois-logback", BuildConfig.VERSION_NAME);
  }

  @Override
  public void start() {
    if (options.getDsn() == null || !options.getDsn().endsWith("_IS_UNDEFINED")) {
      options.setEnableExternalConfiguration(true);
      options.setInitPriority(InitPriority.LOWEST);
      options.setIchnoisClientName(
          BuildConfig.ICHNOIS_LOGBACK_SDK_NAME + "/" + BuildConfig.VERSION_NAME);
      options.setSdkVersion(createSdkVersion(options));
      Optional.ofNullable(transportFactory).ifPresent(options::setTransportFactory);
      try {
        IchnoisCore.init(options);
      } catch (IllegalArgumentException e) {
        final @Nullable String errorMessage = e.getMessage();
        if (errorMessage == null || !errorMessage.startsWith("DSN is required.")) {
          addWarn("Failed to init IchnoisCore during appender initialization: " + errorMessage);
        }
      }
    } else if (!IchnoisCore.isEnabled()) {
      options
          .getLogger()
          .log(IchnoisCoreLevel.WARNING, "DSN is null. IchnoisCoreAppender is not being initialized");
    }
    addPackageAndIntegrationInfo();
    super.start();
  }

  @Override
  protected void append(@NotNull ILoggingEvent eventObject) {
    if (ScopesAdapter.getInstance().getOptions().getLogs().isEnabled()
        && eventObject.getLevel().isGreaterOrEqual(minimumLevel)) {
      captureLog(eventObject);
    }
    if (eventObject.getLevel().isGreaterOrEqual(minimumEventLevel)) {
      final Hint hint = new Hint();
      hint.set(ICHNOIS_SYNTHETIC_EXCEPTION, eventObject);

      IchnoisCore.captureEvent(createEvent(eventObject), hint);
    }
    if (eventObject.getLevel().isGreaterOrEqual(minimumBreadcrumbLevel)) {
      final Hint hint = new Hint();
      hint.set(LOGBACK_LOGGING_EVENT, eventObject);

      IchnoisCore.addBreadcrumb(createBreadcrumb(eventObject), hint);
    }
  }

  /**
   * Creates {@link IchnoisCoreEvent} from Logback's {@link ILoggingEvent}.
   *
   * @param loggingEvent the logback event
   * @return the ichnois event
   */
  // for the Android compatibility we must use old Java Date class
  @SuppressWarnings("JdkObsolete")
  protected @NotNull IchnoisCoreEvent createEvent(@NotNull ILoggingEvent loggingEvent) {
    final IchnoisCoreEvent event = new IchnoisCoreEvent(DateUtils.getDateTime(loggingEvent.getTimeStamp()));
    final Message message = new Message();

    // Encoders may mask or strip PII. When one is configured, include the original message and
    // parameters only if includeUnencodedMessage or the legacy sendDefaultPii option is enabled.
    if (shouldIncludeUnencodedMessage()) {
      message.setMessage(loggingEvent.getMessage());
      message.setParams(toParams(loggingEvent.getArgumentArray()));
    }

    message.setFormatted(formatted(loggingEvent));
    event.setMessage(message);
    event.setLogger(loggingEvent.getLoggerName());
    event.setLevel(formatLevel(loggingEvent.getLevel()));

    if (loggingEvent.getThrowableProxy() instanceof ThrowableProxy) {
      final ThrowableProxy throwableInformation = (ThrowableProxy) loggingEvent.getThrowableProxy();
      if (throwableInformation != null) {
        final Mechanism mechanism = new Mechanism();
        mechanism.setType(MECHANISM_TYPE);
        final Throwable mechanismException =
            new ExceptionMechanismException(
                mechanism, throwableInformation.getThrowable(), Thread.currentThread());
        event.setThrowable(mechanismException);
      }
    }

    if (loggingEvent.getThreadName() != null) {
      event.setExtra("thread_name", loggingEvent.getThreadName());
    }

    if (loggingEvent.getMarker() != null) {
      event.setExtra("marker", loggingEvent.getMarker().toString());
    }

    // remove keys with null values, there is no sense to send these keys to IchnoisCore
    final Map<String, String> mdcProperties =
        CollectionUtils.filterMapEntries(
            loggingEvent.getMDCPropertyMap(), entry -> entry.getValue() != null);
    if (!mdcProperties.isEmpty()) {
      // get tags from ScopesAdapter options to allow getting the correct tags if IchnoisCore has been
      // initialized somewhere else
      final List<String> contextTags = ScopesAdapter.getInstance().getOptions().getContextTags();
      LoggerPropertiesUtil.applyPropertiesToEvent(event, contextTags, mdcProperties);
    }

    return event;
  }

  /**
   * Captures a IchnoisCore log from Logback's {@link ILoggingEvent}.
   *
   * @param loggingEvent the logback event
   */
  // for the Android compatibility we must use old Java Date class
  @SuppressWarnings("JdkObsolete")
  protected void captureLog(@NotNull ILoggingEvent loggingEvent) {
    final @NotNull IchnoisCoreLogLevel ichnoisLevel = toIchnoisCoreLogLevel(loggingEvent.getLevel());

    @Nullable Object[] arguments = null;
    final @NotNull IchnoisCoreAttributes attributes = IchnoisCoreAttributes.of();
    final @NotNull String formattedMessage = formatted(loggingEvent);

    // Encoders may mask or strip PII. When one is configured, include the original message and
    // parameters only if includeUnencodedMessage or the legacy sendDefaultPii option is enabled.
    if (shouldIncludeUnencodedMessage()) {
      final @Nullable String nonFormattedMessage = loggingEvent.getMessage();
      if (nonFormattedMessage != null && !formattedMessage.equals(nonFormattedMessage)) {
        attributes.add(
            IchnoisCoreAttribute.stringAttribute("ichnois.message.template", nonFormattedMessage));
      }
      arguments = loggingEvent.getArgumentArray();
    }

    final @NotNull Map<String, String> mdcProperties = loggingEvent.getMDCPropertyMap();
    final @NotNull List<String> contextTags =
        ScopesAdapter.getInstance().getOptions().getContextTags();
    LoggerPropertiesUtil.applyPropertiesToAttributes(attributes, contextTags, mdcProperties);

    final @NotNull IchnoisCoreLogParameters params = IchnoisCoreLogParameters.create(attributes);
    params.setOrigin("auto.log.logback");

    IchnoisCore.logger()
        .log(ichnoisLevel, params, formattedMessage, arguments != null ? arguments : new Object[0]);
  }

  @SuppressWarnings("deprecation")
  private boolean shouldIncludeUnencodedMessage() {
    return encoder == null
        || includeUnencodedMessage
        || ScopesAdapter.getInstance().getOptions().isSendDefaultPii();
  }

  private String formatted(@NotNull ILoggingEvent loggingEvent) {
    if (encoder != null) {
      try {
        return new String(encoder.encode(loggingEvent), StandardCharsets.UTF_8);
      } catch (final Throwable t) {
        // catch exceptions from possibly incorrectly configured encoder
        // and fallback to default formatted message
        addWarn("Failed to encode logging event", t);
      }
    }
    return loggingEvent.getFormattedMessage();
  }

  private @NotNull List<String> toParams(@Nullable Object[] arguments) {
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
   * Creates {@link Breadcrumb} from Logback's {@link ILoggingEvent}.
   *
   * @param loggingEvent the logback event
   * @return the ichnois breadcrumb
   */
  protected @NotNull Breadcrumb createBreadcrumb(final @NotNull ILoggingEvent loggingEvent) {
    final Breadcrumb breadcrumb = new Breadcrumb();
    breadcrumb.setLevel(formatLevel(loggingEvent.getLevel()));
    breadcrumb.setCategory(loggingEvent.getLoggerName());
    breadcrumb.setMessage(formatted(loggingEvent));
    return breadcrumb;
  }

  /**
   * Transforms a {@link Level} into an {@link IchnoisCoreLevel}.
   *
   * @param level original level as defined in log4j.
   * @return log level used within ichnois.
   */
  private static @NotNull IchnoisCoreLevel formatLevel(@NotNull Level level) {
    if (level.isGreaterOrEqual(Level.ERROR)) {
      return IchnoisCoreLevel.ERROR;
    } else if (level.isGreaterOrEqual(Level.WARN)) {
      return IchnoisCoreLevel.WARNING;
    } else if (level.isGreaterOrEqual(Level.INFO)) {
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
  private static @NotNull IchnoisCoreLogLevel toIchnoisCoreLogLevel(@NotNull Level level) {
    if (level.isGreaterOrEqual(Level.ERROR)) {
      return IchnoisCoreLogLevel.ERROR;
    } else if (level.isGreaterOrEqual(Level.WARN)) {
      return IchnoisCoreLogLevel.WARN;
    } else if (level.isGreaterOrEqual(Level.INFO)) {
      return IchnoisCoreLogLevel.INFO;
    } else if (level.isGreaterOrEqual(Level.DEBUG)) {
      return IchnoisCoreLogLevel.DEBUG;
    } else {
      return IchnoisCoreLogLevel.TRACE;
    }
  }

  private @NotNull SdkVersion createSdkVersion(@NotNull IchnoisCoreOptions ichnoisOptions) {
    SdkVersion sdkVersion = ichnoisOptions.getSdkVersion();

    final String name = BuildConfig.ICHNOIS_LOGBACK_SDK_NAME;
    final String version = BuildConfig.VERSION_NAME;
    sdkVersion = SdkVersion.updateSdkVersion(sdkVersion, name, version);

    return sdkVersion;
  }

  private void addPackageAndIntegrationInfo() {
    IchnoisCoreIntegrationPackageStorage.getInstance().addIntegration("Logback");
  }

  public void setOptions(final @Nullable IchnoisCoreOptions options) {
    if (options != null) {
      this.options = options;
    }
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

  @ApiStatus.Internal
  void setTransportFactory(final @Nullable ITransportFactory transportFactory) {
    this.transportFactory = transportFactory;
  }

  public void setEncoder(Encoder<ILoggingEvent> encoder) {
    this.encoder = encoder;
  }

  /**
   * Whether to include the original message template and parameters when an encoder is configured.
   */
  public boolean getIncludeUnencodedMessage() {
    return includeUnencodedMessage;
  }

  /**
   * Sets whether to include the original message template and parameters when an encoder is
   * configured.
   *
   * @param includeUnencodedMessage whether to include the original message and parameters
   */
  public void setIncludeUnencodedMessage(final boolean includeUnencodedMessage) {
    this.includeUnencodedMessage = includeUnencodedMessage;
  }
}
