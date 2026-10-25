package io.ichnois.instrumentation.file;

import io.ichnois.IScopes;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.OutputStreamWriter;
import org.jetbrains.annotations.NotNull;

public final class IchnoisCoreFileWriter extends OutputStreamWriter {
  public IchnoisCoreFileWriter(final @NotNull String fileName) throws FileNotFoundException {
    super(new IchnoisCoreFileOutputStream(fileName));
  }

  public IchnoisCoreFileWriter(final @NotNull String fileName, final boolean append)
      throws FileNotFoundException {
    super(new IchnoisCoreFileOutputStream(fileName, append));
  }

  public IchnoisCoreFileWriter(final @NotNull File file) throws FileNotFoundException {
    super(new IchnoisCoreFileOutputStream(file));
  }

  public IchnoisCoreFileWriter(final @NotNull File file, final boolean append)
      throws FileNotFoundException {
    super(new IchnoisCoreFileOutputStream(file, append));
  }

  public IchnoisCoreFileWriter(final @NotNull FileDescriptor fd) {
    super(new IchnoisCoreFileOutputStream(fd));
  }

  IchnoisCoreFileWriter(final @NotNull File file, final boolean append, final @NotNull IScopes scopes)
      throws FileNotFoundException {
    super(new IchnoisCoreFileOutputStream(file, append, scopes));
  }
}
