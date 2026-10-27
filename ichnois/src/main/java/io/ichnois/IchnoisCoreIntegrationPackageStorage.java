package io.ichnois;

import io.ichnois.protocol.IchnoisCorePackage;
import io.ichnois.util.AutoClosableReentrantLock;
import io.ichnois.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

@ApiStatus.Internal
public final class IchnoisCoreIntegrationPackageStorage {
  private static volatile @Nullable IchnoisCoreIntegrationPackageStorage INSTANCE;
  private static final @NotNull AutoClosableReentrantLock staticLock =
      new AutoClosableReentrantLock();
  private static volatile @Nullable Boolean mixedVersionsDetected = null;
  private static final @NotNull AutoClosableReentrantLock mixedVersionsLock =
      new AutoClosableReentrantLock();

  public static @NotNull IchnoisCoreIntegrationPackageStorage getInstance() {
    if (INSTANCE == null) {
      try (final @NotNull IIchnoisCoreLifecycleToken ignored = staticLock.acquire()) {
        if (INSTANCE == null) {
          INSTANCE = new IchnoisCoreIntegrationPackageStorage();
        }
      }
    }

    return INSTANCE;
  }

  /**
   * List of integrations that are enabled in the SDK. _Optional._
   *
   * <p>The list should have all enabled integrations, including default integrations. Default
   * integrations are included because different SDK releases may contain different default
   * integrations.
   */
  private final Set<String> integrations = new CopyOnWriteArraySet<>();

  /**
   * List of installed and loaded SDK packages. _Optional._
   *
   * <p>A list of packages that were installed as part of this SDK or the activated integrations.
   * Each package consists of a name in the format `source:identifier` and `version`. If the source
   * is a Git repository, the `source` should be `git`, the identifier should be a checkout link and
   * the version should be a Git reference (branch, tag or SHA).
   */
  private final Set<IchnoisCorePackage> packages = new CopyOnWriteArraySet<>();

  private IchnoisCoreIntegrationPackageStorage() {}

  public void addIntegration(final @NotNull String integration) {
    Objects.requireNonNull(integration, "integration is required.");
    integrations.add(integration);
  }

  public @NotNull Set<String> getIntegrations() {
    return integrations;
  }

  public void addPackage(final @NotNull String name, final @NotNull String version) {
    Objects.requireNonNull(name, "name is required.");
    Objects.requireNonNull(version, "version is required.");

    IchnoisCorePackage newPackage = new IchnoisCorePackage(name, version);
    packages.add(newPackage);
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = mixedVersionsLock.acquire()) {
      mixedVersionsDetected = null;
    }
  }

  public @NotNull Set<IchnoisCorePackage> getPackages() {
    return packages;
  }

  public boolean checkForMixedVersions(final @NotNull ILogger logger) {
    final @Nullable Boolean mixedVersionsDetectedBefore = mixedVersionsDetected;
    if (mixedVersionsDetectedBefore != null) {
      return mixedVersionsDetectedBefore;
    }
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = mixedVersionsLock.acquire()) {
      final @NotNull String sdkVersion = BuildConfig.VERSION_NAME;
      boolean mixedVersionsDetectedThisCheck = false;

      for (IchnoisCorePackage pkg : packages) {
        if ((pkg.getName().startsWith("maven:io.github.ichnois:") || pkg.getName().startsWith("maven:io.ichnois:"))
            && !sdkVersion.equalsIgnoreCase(pkg.getVersion())) {
          logger.log(
              IchnoisCoreLevel.ERROR,
              "The IchnoisCore SDK has been configured with mixed versions. Expected %s to match core SDK version %s but was %s",
              pkg.getName(),
              sdkVersion,
              pkg.getVersion());
          mixedVersionsDetectedThisCheck = true;
        }
      }

      if (mixedVersionsDetectedThisCheck) {
        logger.log(IchnoisCoreLevel.ERROR, "^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
        logger.log(IchnoisCoreLevel.ERROR, "^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
        logger.log(IchnoisCoreLevel.ERROR, "^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
        logger.log(IchnoisCoreLevel.ERROR, "^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
      }
      mixedVersionsDetected = mixedVersionsDetectedThisCheck;
      return mixedVersionsDetectedThisCheck;
    }
  }

  @TestOnly
  public void clearStorage() {
    integrations.clear();
    packages.clear();
  }
}
