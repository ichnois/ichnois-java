package io.ichnois.hints;

public interface Retryable {
  boolean isRetry();

  void setRetry(boolean retry);
}
