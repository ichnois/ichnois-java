package io.ichnois.logger;

import io.ichnois.IScope;
import io.ichnois.ISpan;
import io.ichnois.PropagationContext;
import io.ichnois.Scopes;
import io.ichnois.IchnoisCoreAttribute;
import io.ichnois.IchnoisCoreAttributeType;
import io.ichnois.IchnoisCoreAttributes;
import io.ichnois.IchnoisCoreDate;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreLogEvent;
import io.ichnois.IchnoisCoreLogEventAttributeValue;
import io.ichnois.IchnoisCoreLogLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.SpanId;
import io.ichnois.protocol.SdkVersion;
import io.ichnois.protocol.IchnoisCoreId;
import io.ichnois.protocol.User;
import io.ichnois.util.Platform;
import io.ichnois.util.TracingUtils;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LoggerApi implements ILoggerApi {

  private final @NotNull Scopes scopes;

  public LoggerApi(final @NotNull Scopes scopes) {
    this.scopes = scopes;
  }

  @Override
  public void trace(final @Nullable String message, final @Nullable Object... args) {
    log(IchnoisCoreLogLevel.TRACE, message, args);
  }

  @Override
  public void debug(final @Nullable String message, final @Nullable Object... args) {
    log(IchnoisCoreLogLevel.DEBUG, message, args);
  }

  @Override
  public void info(final @Nullable String message, final @Nullable Object... args) {
    log(IchnoisCoreLogLevel.INFO, message, args);
  }

  @Override
  public void warn(final @Nullable String message, final @Nullable Object... args) {
    log(IchnoisCoreLogLevel.WARN, message, args);
  }

  @Override
  public void error(final @Nullable String message, final @Nullable Object... args) {
    log(IchnoisCoreLogLevel.ERROR, message, args);
  }

  @Override
  public void fatal(final @Nullable String message, final @Nullable Object... args) {
    log(IchnoisCoreLogLevel.FATAL, message, args);
  }

  @Override
  public void log(
      final @NotNull IchnoisCoreLogLevel level,
      final @Nullable String message,
      final @Nullable Object... args) {
    captureLog(level, IchnoisCoreLogParameters.create(null, null), message, args);
  }

  @Override
  public void log(
      final @NotNull IchnoisCoreLogLevel level,
      final @Nullable IchnoisCoreDate timestamp,
      final @Nullable String message,
      final @Nullable Object... args) {
    captureLog(level, IchnoisCoreLogParameters.create(timestamp, null), message, args);
  }

  @Override
  public void log(
      final @NotNull IchnoisCoreLogLevel level,
      final @NotNull IchnoisCoreLogParameters params,
      final @Nullable String message,
      final @Nullable Object... args) {
    captureLog(level, params, message, args);
  }

  @SuppressWarnings("AnnotateFormatMethod")
  private void captureLog(
      final @NotNull IchnoisCoreLogLevel level,
      final @NotNull IchnoisCoreLogParameters params,
      final @Nullable String message,
      final @Nullable Object... args) {
    final @NotNull IchnoisCoreOptions options = scopes.getOptions();
    try {
      if (!scopes.isEnabled()) {
        options
            .getLogger()
            .log(IchnoisCoreLevel.WARNING, "Instance is disabled and this 'logger' call is a no-op.");
        return;
      }

      if (message == null) {
        return;
      }

      final @Nullable IchnoisCoreDate timestamp = params.getTimestamp();
      final @NotNull IchnoisCoreDate timestampToUse =
          timestamp == null ? options.getDateProvider().now() : timestamp;
      final @NotNull String messageToUse = maybeFormatMessage(message, args);

      final @NotNull IScope combinedScope = scopes.getCombinedScopeView();
      final @NotNull PropagationContext propagationContext = combinedScope.getPropagationContext();
      final @Nullable ISpan span = combinedScope.getSpan();
      if (span == null) {
        TracingUtils.maybeUpdateBaggage(combinedScope, options);
      }
      final @NotNull IchnoisCoreId traceId =
          span == null ? propagationContext.getTraceId() : span.getSpanContext().getTraceId();
      final @NotNull SpanId spanId =
          span == null ? propagationContext.getSpanId() : span.getSpanContext().getSpanId();
      final IchnoisCoreLogEvent logEvent =
          new IchnoisCoreLogEvent(traceId, timestampToUse, messageToUse, level);
      logEvent.setSpanId(spanId);
      logEvent.setAttributes(createAttributes(params, message, args));
      logEvent.setSeverityNumber(level.getSeverityNumber());

      scopes.getClient().captureLog(logEvent, combinedScope);
    } catch (Throwable e) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, "Error while capturing log event", e);
    }
  }

  private @NotNull String maybeFormatMessage(
      final @NotNull String message, final @Nullable Object[] args) {
    if (args == null || args.length == 0) {
      return message;
    }

    try {
      return String.format(message, args);
    } catch (Throwable t) {
      scopes
          .getOptions()
          .getLogger()
          .log(IchnoisCoreLevel.ERROR, "Error while running log through String.format", t);
      return message;
    }
  }

  private @NotNull Map<String, IchnoisCoreLogEventAttributeValue> createAttributes(
      final @NotNull IchnoisCoreLogParameters params,
      final @NotNull String message,
      final @Nullable Object... args) {
    final @NotNull HashMap<String, IchnoisCoreLogEventAttributeValue> attributes = new HashMap<>();

    final @NotNull Map<String, IchnoisCoreAttribute> scopeAttributes =
        scopes.getCombinedScopeView().getAttributes();
    for (IchnoisCoreAttribute scopeAttribute : scopeAttributes.values()) {
      attributes.put(
          scopeAttribute.getName(), IchnoisCoreLogEventAttributeValue.fromAttribute(scopeAttribute));
    }

    final @NotNull String origin = params.getOrigin();
    if (!"manual".equalsIgnoreCase(origin)) {
      attributes.put(
          "ichnois.origin", new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, origin));
    }

    final @Nullable IchnoisCoreAttributes incomingAttributes = params.getAttributes();

    if (incomingAttributes != null) {
      for (IchnoisCoreAttribute attribute : incomingAttributes.getAttributes().values()) {
        attributes.put(attribute.getName(), IchnoisCoreLogEventAttributeValue.fromAttribute(attribute));
      }
    }

    if (args != null) {
      int i = 0;
      for (Object arg : args) {
        final @NotNull IchnoisCoreAttributeType type = IchnoisCoreAttributeType.inferFrom(arg);
        attributes.put(
            "ichnois.message.parameter." + i, new IchnoisCoreLogEventAttributeValue(type, arg));
        i++;
      }
      if (i > 0) {
        if (attributes.get("ichnois.message.template") == null) {
          attributes.put(
              "ichnois.message.template",
              new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, message));
        }
      }
    }

    final @Nullable SdkVersion sdkVersion = scopes.getOptions().getSdkVersion();
    if (sdkVersion != null) {
      attributes.put(
          "ichnois.sdk.name",
          new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, sdkVersion.getName()));
      attributes.put(
          "ichnois.sdk.version",
          new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, sdkVersion.getVersion()));
    }

    final @Nullable String environment = scopes.getOptions().getEnvironment();
    if (environment != null) {
      attributes.put(
          "ichnois.environment",
          new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, environment));
    }

    final @NotNull IchnoisCoreId scopeReplayId = scopes.getCombinedScopeView().getReplayId();
    if (!IchnoisCoreId.EMPTY_ID.equals(scopeReplayId)) {
      attributes.put(
          "ichnois.replay_id",
          new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, scopeReplayId.toString()));
    } else {
      final @NotNull IchnoisCoreId controllerReplayId =
          scopes.getOptions().getReplayController().getReplayId();
      if (!IchnoisCoreId.EMPTY_ID.equals(controllerReplayId)) {
        attributes.put(
            "ichnois.replay_id",
            new IchnoisCoreLogEventAttributeValue(
                IchnoisCoreAttributeType.STRING, controllerReplayId.toString()));
        attributes.put(
            "ichnois._internal.replay_is_buffering",
            new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.BOOLEAN, true));
      }
    }

    final @Nullable String release = scopes.getOptions().getRelease();
    if (release != null) {
      attributes.put(
          "ichnois.release", new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, release));
    }

    if (Platform.isJvm()) {
      setServerName(attributes);
    }

    setUser(attributes);

    return attributes;
  }

  private void setServerName(final @NotNull Map<String, IchnoisCoreLogEventAttributeValue> attributes) {
    final @NotNull IchnoisCoreOptions options = scopes.getOptions();
    final @Nullable String optionsServerName = options.getServerName();
    if (optionsServerName != null) {
      attributes.put(
          "server.address",
          new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, optionsServerName));
    } else if (options.isAttachServerName()) {
      final @Nullable String hostname = options.getHostnameCache().getHostname();
      if (hostname != null) {
        attributes.put(
            "server.address",
            new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, hostname));
      }
    }
  }

  private void setUser(final @NotNull Map<String, IchnoisCoreLogEventAttributeValue> attributes) {
    final @Nullable User user = scopes.getCombinedScopeView().getUser();
    if (user == null) {
      // In case no user is set, we should fallback to the distinct id, known as installation id,
      // which is used on Android as default user id
      final @Nullable String id = scopes.getOptions().getDistinctId();
      if (id != null) {
        attributes.put("user.id", new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, id));
      }
    } else {
      final @Nullable String id = user.getId();
      if (id != null) {
        attributes.put("user.id", new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, id));
      }
      final @Nullable String username = user.getUsername();
      if (username != null) {
        attributes.put(
            "user.name", new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, username));
      }
      final @Nullable String email = user.getEmail();
      if (email != null) {
        attributes.put(
            "user.email", new IchnoisCoreLogEventAttributeValue(IchnoisCoreAttributeType.STRING, email));
      }
    }
  }
}
