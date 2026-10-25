package io.ichnois;

import org.jetbrains.annotations.NotNull;

public interface IEnvelopeSender {
  void processEnvelopeFile(@NotNull String path, @NotNull Hint hint);
}
