package io.ichnois;

import io.ichnois.Breadcrumb;
import io.ichnois.ITransaction;
import io.ichnois.IchnoisCore;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.protocol.IchnoisCoreId;
import io.ichnois.protocol.User;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Public Ichnois API for Java and Kotlin applications. */
public final class Ichnois {
  private static final Logger LOGGER = Logger.getLogger(Ichnois.class.getName());
  private static final String SDK_CLIENT_NAME = "ichnois.java/1.0.0";

  private Ichnois() {}

  /** Initializes Ichnois using either a DSN or structured endpoint/project/key configuration. */
  public static void init(@NotNull IchnoisOptions config) {
    if (config == null) {
      throw new IllegalArgumentException("IchnoisOptions is required.");
    }
    final String resolvedDsn = config.resolveDsn();
    final String resolvedRelease = config.resolveRelease();
    IchnoisCore.init(options -> {
      options.setDsn(resolvedDsn);
      options.setRelease(resolvedRelease);
      options.setEnvironment(config.getEnvironment() == null ? "production" : config.getEnvironment());
      options.setIchnoisClientName(SDK_CLIENT_NAME);
      options.setEnableExternalConfiguration(false);
      for (String packagePrefix : config.getInAppIncludes()) {
        options.addInAppInclude(packagePrefix);
      }
    });
    if ("unknown".equals(resolvedRelease)) {
      LOGGER.warning("Ichnois could not detect a Git commit SHA. Configure release or ICHNOIS_RELEASE for reliable source attribution.");
    } else {
      IchnoisCore.setTag("git.commit", resolvedRelease);
    }
    String projectId = config.resolveProjectId();
    if (projectId != null) {
      IchnoisCore.setTag("service", projectId);
    }
  }

  /** Captures an exception and returns its 32-character event identifier. */
  public static @NotNull String captureException(@NotNull Throwable error) {
    if (error == null) {
      throw new IllegalArgumentException("error is required.");
    }
    return eventId(IchnoisCore.captureException(error));
  }

  /** Captures a message and returns its 32-character event identifier. */
  public static @NotNull String captureMessage(@NotNull String message) {
    if (message == null || message.trim().isEmpty()) {
      throw new IllegalArgumentException("message must not be blank.");
    }
    return eventId(IchnoisCore.captureMessage(message));
  }

  /** Captures a log message with a level: debug, info, warning, error, or fatal. */
  public static @NotNull String captureLog(@NotNull String level, @NotNull String message) {
    if (message == null || message.trim().isEmpty()) {
      throw new IllegalArgumentException("message must not be blank.");
    }
    return eventId(IchnoisCore.captureMessage(message, parseLevel(level)));
  }

  /** Starts an instrumented operation and returns an Ichnois-owned span handle. */
  public static @NotNull IchnoisSpan startSpan(@NotNull String name) {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("span name must not be blank.");
    }
    return new IchnoisSpan(IchnoisCore.startTransaction(name, "ichnois.span"));
  }

  /** Finishes a span returned by {@link #startSpan(String)}. */
  public static void finishSpan(@Nullable IchnoisSpan span) {
    if (span != null) {
      span.finish();
    }
  }

  /** Sets user identity using the keys id, email, username, ip_address, and optional custom fields. */
  public static void setUser(@Nullable Map<String, String> attributes) {
    if (attributes == null || attributes.isEmpty()) {
      IchnoisCore.setUser(null);
      return;
    }
    User user = new User();
    Map<String, String> custom = new HashMap<>();
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (key == null || value == null) {
        continue;
      }
      switch (key) {
        case "id": user.setId(value); break;
        case "email": user.setEmail(value); break;
        case "username": user.setUsername(value); break;
        case "ip_address": user.setIpAddress(value); break;
        default: custom.put(key, value); break;
      }
    }
    if (!custom.isEmpty()) {
      user.setData(custom);
    }
    IchnoisCore.setUser(user);
  }

  /** Sets a tag attached to subsequent events. */
  public static void setTag(@NotNull String key, @NotNull String value) {
    if (key == null || key.trim().isEmpty() || value == null) {
      throw new IllegalArgumentException("tag key and value are required.");
    }
    IchnoisCore.setTag(key, value);
  }

  /** Adds a breadcrumb from a map containing optional message, category, type, and level keys. */
  public static void addBreadcrumb(@NotNull Map<String, String> attributes) {
    if (attributes == null) {
      throw new IllegalArgumentException("breadcrumb attributes are required.");
    }
    Breadcrumb breadcrumb = new Breadcrumb(System.currentTimeMillis());
    breadcrumb.setMessage(attributes.get("message"));
    breadcrumb.setCategory(attributes.get("category"));
    breadcrumb.setType(attributes.getOrDefault("type", "default"));
    String level = attributes.get("level");
    if (level != null) {
      breadcrumb.setLevel(parseLevel(level));
    }
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      String key = entry.getKey();
      if (key != null && entry.getValue() != null
          && !"message".equals(key) && !"category".equals(key)
          && !"type".equals(key) && !"level".equals(key)) {
        breadcrumb.setData(key, entry.getValue());
      }
    }
    IchnoisCore.addBreadcrumb(breadcrumb);
  }

  /** Flushes queued events, waiting up to the supplied timeout in milliseconds. */
  public static void flush(long timeoutMillis) {
    IchnoisCore.flush(Math.max(0L, timeoutMillis));
  }

  /** Flushes pending events and releases SDK resources. */
  public static void shutdown() {
    IchnoisCore.close();
  }

  private static @NotNull String eventId(@Nullable IchnoisCoreId id) {
    return id == null ? IchnoisCoreId.EMPTY_ID.toString() : id.toString().replace("-", "");
  }

  private static @NotNull IchnoisCoreLevel parseLevel(@Nullable String level) {
    if (level == null) return IchnoisCoreLevel.INFO;
    switch (level.trim().toLowerCase(Locale.ROOT)) {
      case "debug": return IchnoisCoreLevel.DEBUG;
      case "warning":
      case "warn": return IchnoisCoreLevel.WARNING;
      case "error": return IchnoisCoreLevel.ERROR;
      case "fatal": return IchnoisCoreLevel.FATAL;
      default: return IchnoisCoreLevel.INFO;
    }
  }
}
