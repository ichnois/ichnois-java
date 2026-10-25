package io.ichnois;

/**
 * Which framework is responsible for instrumenting. This includes starting and stopping of
 * transactions and spans.
 */
public enum Instrumenter {
  ICHNOIS,

  /** OpenTelemetry */
  OTEL
}
