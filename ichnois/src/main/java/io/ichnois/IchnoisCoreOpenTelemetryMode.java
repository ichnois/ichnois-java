package io.ichnois;

/**
 * Configures the SDK to either automatically determine if OpenTelemetry is available, whether to
 * use it and what way to use it in.
 */
public enum IchnoisCoreOpenTelemetryMode {
  /**
   * Let the SDK figure out what mode OpenTelemetry is in and whether to even use OpenTelemetry This
   * is the default for non Android.
   */
  AUTO,
  /** Do not try to use OpenTelemetry, even if it is available. This is the default for Android. */
  OFF,
  /** The `ichnois-opentelemetry-agent` is used */
  AGENT,
  /**
   * `ichnois-opentelemetry-agentless` is used, meaning OpenTelemetry will be used but there is no
   * auto instrumentation available.
   */
  AGENTLESS,
  /**
   * `ichnois-opentelemetry-agentless-spring-boot` is used, meaning
   * `opentelemetry-spring-boot-starter` and its auto instrumentation is used.
   */
  AGENTLESS_SPRING
}
