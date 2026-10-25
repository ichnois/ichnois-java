package io.ichnois.hints;

public interface SubmissionResult {
  void setResult(boolean success);

  boolean isSuccess();
}
