package io.ichnois;

/**
 * Determines whether the profiling lifecycle is controlled manually or based on the trace
 * lifecycle.
 */
public enum ProfileLifecycle {
  /**
   * Profiling is controlled manually. You must use the {@link IchnoisCore#startProfiler()} and {@link
   * IchnoisCore#stopProfiler()} APIs to control the lifecycle of the profiler.
   */
  MANUAL,
  /**
   * Profiling is automatically started when there is at least 1 sampled root span, and it's
   * automatically stopped when there are none.
   */
  TRACE
}
