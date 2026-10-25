package io.ichnois.metrics;

import io.ichnois.IScope;
import io.ichnois.ISpan;
import io.ichnois.PropagationContext;
import io.ichnois.Scopes;
import io.ichnois.IchnoisCoreAttribute;
import io.ichnois.IchnoisCoreAttributeType;
import io.ichnois.IchnoisCoreAttributes;
import io.ichnois.IchnoisCoreDate;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreLogEventAttributeValue;
import io.ichnois.IchnoisCoreMetricsEvent;
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

public final class MetricsApi implements IMetricsApi {

  private final @NotNull Scopes scopes;

  public MetricsApi(final @NotNull Scopes scopes) {
    this.scopes = scopes;
  }

  @Override
  public void count(final @NotNull String name) {
    captureMetrics(IchnoisCoreMetricsParameters.create(null, null), name, "counter", 1.0, null);
  }

  @Override
  public void count(final @NotNull String name, final @Nullable Double value) {
    captureMetrics(IchnoisCoreMetricsParameters.create(null, null), name, "counter", value, null);
  }

  @Override
  public void count(final @NotNull String name, final @Nullable String unit) {
    captureMetrics(IchnoisCoreMetricsParameters.create(null, null), name, "counter", 1.0, unit);
  }

  @Override
  public void count(
      final @NotNull String name, final @Nullable Double value, final @Nullable String unit) {
    captureMetrics(IchnoisCoreMetricsParameters.create(null, null), name, "counter", value, unit);
  }

  @Override
  public void count(
      final @NotNull String name,
      final @Nullable Double value,
      final @Nullable String unit,
      final @NotNull IchnoisCoreMetricsParameters params) {
    captureMetrics(params, name, "counter", value, unit);
  }

  @Override
  public void distribution(final @NotNull String name, final @Nullable Double value) {
    captureMetrics(IchnoisCoreMetricsParameters.create(null, null), name, "distribution", value, null);
  }

  @Override
  public void distribution(
      final @NotNull String name, final @Nullable Double value, final @Nullable String unit) {
    captureMetrics(IchnoisCoreMetricsParameters.create(null, null), name, "distribution", value, unit);
  }

  @Override
  public void distribution(
      final @NotNull String name,
      final @Nullable Double value,
      final @Nullable String unit,
      final @NotNull IchnoisCoreMetricsParameters params) {
    captureMetrics(params, name, "distribution", value, unit);
  }

  @Override
  public void gauge(final @NotNull String name, final @Nullable Double value) {
    captureMetrics(IchnoisCoreMetricsParameters.create(null, null), name, "gauge", value, null);
  }

  @Override
  public void gauge(
      final @NotNull String name, final @Nullable Double value, final @Nullable String unit) {
    captureMetrics(IchnoisCoreMetricsParameters.create(null, null), name, "gauge", value, unit);
  }

  @Override
  public void gauge(
      final @NotNull String name,
      final @Nullable Double value,
      final @Nullable String unit,
      final @NotNull IchnoisCoreMetricsParameters params) {
    captureMetrics(params, name, "gauge", value, unit);
  }

  @SuppressWarnings("AnnotateFormatMethod")
  private void captureMetrics(
      final @NotNull IchnoisCoreMetricsParameters params,
      final @Nullable String name,
      final @Nullable String type,
      final @Nullable Double value,
      final @Nullable String unit) {
    final @NotNull IchnoisCoreOptions options = scopes.getOptions();
    try {
      if (!scopes.isEnabled()) {
        options
            .getLogger()
            .log(IchnoisCoreLevel.WARNING, "Instance is disabled and this 'metrics' call is a no-op.");
        return;
      }

      if (name == null) {
        return;
      }

      if (type == null) {
        return;
      }

      if (value == null) {
        return;
      }

      final @Nullable IchnoisCoreDate timestamp = params.getTimestamp();
      final @NotNull IchnoisCoreDate timestampToUse =
          timestamp == null ? options.getDateProvider().now() : timestamp;

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
      final IchnoisCoreMetricsEvent metricsEvent =
          new IchnoisCoreMetricsEvent(traceId, timestampToUse, name, type, value);
      metricsEvent.setSpanId(spanId);
      metricsEvent.setUnit(unit);
      metricsEvent.setAttributes(createAttributes(params));

      scopes.getClient().captureMetric(metricsEvent, combinedScope, params.getHint());
    } catch (Throwable e) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, "Error while capturing metrics event", e);
    }
  }

  private @NotNull Map<String, IchnoisCoreLogEventAttributeValue> createAttributes(
      final @NotNull IchnoisCoreMetricsParameters params) {
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
