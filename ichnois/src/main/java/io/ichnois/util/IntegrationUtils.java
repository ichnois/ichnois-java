package io.ichnois.util;

import io.ichnois.IchnoisCoreIntegrationPackageStorage;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class IntegrationUtils {

  public static void addIntegrationToSdkVersion(final @NotNull String name) {
    IchnoisCoreIntegrationPackageStorage.getInstance().addIntegration(name);
  }
}
