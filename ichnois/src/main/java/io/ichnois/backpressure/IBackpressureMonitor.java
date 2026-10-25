package io.ichnois.backpressure;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface IBackpressureMonitor {
  void start();

  int getDownsampleFactor();

  void close();
}
