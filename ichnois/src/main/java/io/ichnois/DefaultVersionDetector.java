package io.ichnois;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class DefaultVersionDetector implements IVersionDetector {

  private final @NotNull IchnoisCoreOptions options;

  public DefaultVersionDetector(final @NotNull IchnoisCoreOptions options) {
    this.options = options;
  }

  @Override
  public boolean checkForMixedVersions() {
    return IchnoisCoreIntegrationPackageStorage.getInstance()
        .checkForMixedVersions(options.getFatalLogger());
  }
}
