package io.ichnois.android.core;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public enum NdkHandlerStrategy {
  ICHNOIS_HANDLER_STRATEGY_DEFAULT(0),
  ICHNOIS_HANDLER_STRATEGY_CHAIN_AT_START(1);

  private final int value;

  NdkHandlerStrategy(final int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
