package io.ichnois.android.ndk;

import io.ichnois.IchnoisCoreIntegrationPackageStorage;
import io.ichnois.protocol.SdkVersion;
import org.jetbrains.annotations.Nullable;

/**
 * Util class to make IchnoisCoreNdk testable, as IchnoisCoreNdk inits native libraries and it breaks on init.
 */
final class IchnoisCoreNdkUtil {

  static {
    IchnoisCoreIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.ichnois:ichnois-android-ndk", BuildConfig.VERSION_NAME);
  }

  private IchnoisCoreNdkUtil() {}

  /**
   * Adds the ichnois-android-ndk package into the package list
   *
   * @param sdkVersion the SdkVersion object
   */
  static void addPackage(@Nullable final SdkVersion sdkVersion) {
    if (sdkVersion == null) {
      return;
    }
    sdkVersion.addPackage("maven:io.ichnois:ichnois-android-ndk", BuildConfig.VERSION_NAME);
  }
}
