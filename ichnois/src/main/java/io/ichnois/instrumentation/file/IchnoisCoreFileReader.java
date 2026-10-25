package io.ichnois.instrumentation.file;

import io.ichnois.IScopes;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import org.jetbrains.annotations.NotNull;

public final class IchnoisCoreFileReader extends InputStreamReader {
  public IchnoisCoreFileReader(final @NotNull String fileName) throws FileNotFoundException {
    super(new IchnoisCoreFileInputStream(fileName));
  }

  public IchnoisCoreFileReader(final @NotNull File file) throws FileNotFoundException {
    super(new IchnoisCoreFileInputStream(file));
  }

  public IchnoisCoreFileReader(final @NotNull FileDescriptor fd) {
    super(new IchnoisCoreFileInputStream(fd));
  }

  IchnoisCoreFileReader(final @NotNull File file, final @NotNull IScopes scopes)
      throws FileNotFoundException {
    super(new IchnoisCoreFileInputStream(file, scopes));
  }
}
