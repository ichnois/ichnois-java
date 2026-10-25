package io.ichnois;

import io.ichnois.internal.ManifestVersionReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ManifestVersionDetector implements IVersionDetector {

  private final @NotNull IchnoisCoreOptions options;

  public ManifestVersionDetector(final @NotNull IchnoisCoreOptions options) {
    this.options = options;
  }

  @Override
  public boolean checkForMixedVersions() {
    ManifestVersionReader.getInstance().readManifestFiles();
    return IchnoisCoreIntegrationPackageStorage.getInstance()
        .checkForMixedVersions(options.getFatalLogger());
  }
}
