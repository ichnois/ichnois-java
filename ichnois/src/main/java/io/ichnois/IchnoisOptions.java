package io.ichnois;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Configuration for the Ichnois Java/Kotlin SDK facade. */
public final class IchnoisOptions {
  private static final Pattern RELEASE_PATTERN =
      Pattern.compile("^(?:[^@]+@)?[0-9a-fA-F]{7,40}$");

  private @Nullable String dsn;
  private @Nullable String endpoint;
  private @Nullable String projectId;
  private @Nullable String apiKey;
  private @Nullable String release;
  private @Nullable String environment = "production";
  private final @NotNull List<String> inAppIncludes = new ArrayList<>();

  /** Creates options from an Ichnois DSN such as https://key@host/api/project-id. */
  public static @NotNull IchnoisOptions fromDsn(@NotNull String dsn) {
    IchnoisOptions options = new IchnoisOptions();
    options.setDsn(dsn);
    return options;
  }

  public @Nullable String getDsn() { return dsn; }
  public void setDsn(@Nullable String dsn) { this.dsn = trimToNull(dsn); }
  public @Nullable String getEndpoint() { return endpoint; }
  public void setEndpoint(@Nullable String endpoint) { this.endpoint = trimToNull(endpoint); }
  public @Nullable String getProjectId() { return projectId; }
  public void setProjectId(@Nullable String projectId) {
    String normalized = trimToNull(projectId);
    if (normalized != null && !normalized.matches("[A-Za-z0-9._-]+")) {
      throw new IllegalArgumentException("Ichnois projectId may contain only letters, digits, dots, underscores, and hyphens.");
    }
    this.projectId = normalized;
  }
  public @Nullable String getApiKey() { return apiKey; }
  public void setApiKey(@Nullable String apiKey) { this.apiKey = trimToNull(apiKey); }
  public @Nullable String getRelease() { return release; }
  public void setRelease(@Nullable String release) { this.release = trimToNull(release); }
  public @Nullable String getEnvironment() { return environment; }
  public void setEnvironment(@Nullable String environment) { this.environment = trimToNull(environment); }
  public void addInAppInclude(@NotNull String packagePrefix) {
    if (packagePrefix == null || packagePrefix.trim().isEmpty()) {
      throw new IllegalArgumentException("packagePrefix must not be blank.");
    }
    inAppIncludes.add(packagePrefix.trim());
  }
  public @NotNull List<String> getInAppIncludes() {
    return Collections.unmodifiableList(inAppIncludes);
  }

  @Nullable String resolveProjectId() {
    if (projectId != null) return projectId;
    String source = dsn;
    if (source == null) return null;
    try {
      String path = URI.create(source).getRawPath();
      if (path == null || path.isEmpty()) return null;
      int slash = path.lastIndexOf('/');
      return slash >= 0 ? path.substring(slash + 1) : path;
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  @NotNull String resolveDsn() {
    if (dsn != null) {
      return dsn;
    }
    if (endpoint == null || projectId == null || apiKey == null) {
      throw new IllegalArgumentException(
          "Configure an Ichnois DSN or provide endpoint, projectId, and apiKey.");
    }
    try {
      URI uri = URI.create(endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint);
      if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
        throw new IllegalArgumentException("Ichnois endpoint must be an absolute HTTP(S) URL without credentials, query, or fragment.");
      }
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Invalid Ichnois endpoint: " + endpoint, ex);
    }
    String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    URI uri = URI.create(base);
    if (apiKey.indexOf('@') >= 0 || apiKey.indexOf(':') >= 0 || apiKey.indexOf('/') >= 0) {
      throw new IllegalArgumentException("Ichnois apiKey must not contain @, :, or / characters.");
    }
    String basePath = uri.getRawPath() == null ? "" : uri.getRawPath();
    while (basePath.endsWith("/")) basePath = basePath.substring(0, basePath.length() - 1);
    if (!basePath.isEmpty() && !basePath.equals("/api")) {
      throw new IllegalArgumentException(
          "Ichnois endpoint path must be empty or /api; configure projectId separately.");
    }
    return uri.getScheme() + "://" + apiKey + "@" + uri.getRawAuthority() + basePath + "/" + projectId;
  }

  @Nullable String resolveRelease() {
    String candidate = release;
    if (candidate == null) {
      candidate = discoverGitSha();
    }
    if (candidate == null) {
      return "unknown";
    }
    if (!RELEASE_PATTERN.matcher(candidate).matches()) {
      throw new IllegalArgumentException(
          "Ichnois release must be a 7-40 character Git SHA or service@<sha>.");
    }
    return candidate;
  }

  private static @Nullable String discoverGitSha() {
    String[] envNames = {
      "ICHNOIS_RELEASE", "GIT_COMMIT_SHA", "VERCEL_GIT_COMMIT_SHA", "RENDER_GIT_COMMIT",
      "HEROKU_SLUG_COMMIT", "GITHUB_SHA", "CI_COMMIT_SHA", "CIRCLE_SHA1"
    };
    for (String name : envNames) {
      String value = trimToNull(System.getenv(name));
      if (value != null && RELEASE_PATTERN.matcher(value).matches()) {
        return value;
      }
    }
    try {
      Process process = new ProcessBuilder("git", "rev-parse", "HEAD").redirectErrorStream(true).start();
      java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
      byte[] buffer = new byte[256];
      int count;
      while ((count = process.getInputStream().read(buffer)) != -1) {
        output.write(buffer, 0, count);
      }
      if (process.waitFor() == 0) {
        String value = trimToNull(new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
        if (value != null && RELEASE_PATTERN.matcher(value).matches()) {
          return value;
        }
      }
    } catch (Exception ignored) {
      // Runtime may not have Git installed (for example, an Android production device).
    }
    return null;
  }

  private static @Nullable String trimToNull(@Nullable String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
