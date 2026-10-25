package io.ichnois;

import io.ichnois.protocol.profiling.IchnoisCoreProfile;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class NoOpProfileConverter implements IProfileConverter {

  private static final NoOpProfileConverter instance = new NoOpProfileConverter();

  private NoOpProfileConverter() {}

  public static NoOpProfileConverter getInstance() {
    return instance;
  }

  @Override
  public @NotNull IchnoisCoreProfile convertFromFile(@NotNull String jfrFilePath) throws IOException {
    return new IchnoisCoreProfile();
  }
}
