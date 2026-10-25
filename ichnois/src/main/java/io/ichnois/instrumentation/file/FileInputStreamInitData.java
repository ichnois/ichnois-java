package io.ichnois.instrumentation.file;

import io.ichnois.ISpan;
import io.ichnois.IchnoisCoreOptions;
import java.io.File;
import java.io.FileInputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class FileInputStreamInitData {

  final @Nullable File file;
  final @Nullable ISpan span;
  final @NotNull FileInputStream delegate;
  final @NotNull IchnoisCoreOptions options;

  FileInputStreamInitData(
      final @Nullable File file,
      final @Nullable ISpan span,
      final @NotNull FileInputStream delegate,
      final @NotNull IchnoisCoreOptions options) {
    this.file = file;
    this.span = span;
    this.delegate = delegate;
    this.options = options;
  }
}
