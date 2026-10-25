package io.ichnois;

import java.io.IOException;
import java.io.InputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IEnvelopeReader {
  @Nullable
  IchnoisCoreEnvelope read(@NotNull InputStream stream) throws IOException;
}
