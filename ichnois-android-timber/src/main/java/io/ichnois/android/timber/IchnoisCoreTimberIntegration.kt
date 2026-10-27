package io.ichnois.android.timber

import io.ichnois.ILogger
import io.ichnois.IScopes
import io.ichnois.Integration
import io.ichnois.IchnoisCoreIntegrationPackageStorage
import io.ichnois.IchnoisCoreLevel
import io.ichnois.IchnoisCoreLogLevel
import io.ichnois.IchnoisCoreOptions
import io.ichnois.android.timber.BuildConfig.VERSION_NAME
import io.ichnois.util.IntegrationUtils.addIntegrationToSdkVersion
import java.io.Closeable
import timber.log.Timber

/** IchnoisCore integration for Timber. */
public class IchnoisCoreTimberIntegration(
  public val minEventLevel: IchnoisCoreLevel = IchnoisCoreLevel.ERROR,
  public val minBreadcrumbLevel: IchnoisCoreLevel = IchnoisCoreLevel.INFO,
  public val minLogsLevel: IchnoisCoreLogLevel = IchnoisCoreLogLevel.INFO,
) : Integration, Closeable {
  private lateinit var tree: IchnoisCoreTimberTree
  private lateinit var logger: ILogger

  private companion object {
    init {
      IchnoisCoreIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.github.ichnois:ichnois-android-timber", VERSION_NAME)
    }
  }

  override fun register(scopes: IScopes, options: IchnoisCoreOptions) {
    logger = options.logger

    tree = IchnoisCoreTimberTree(scopes, minEventLevel, minBreadcrumbLevel, minLogsLevel)
    Timber.plant(tree)

    logger.log(IchnoisCoreLevel.DEBUG, "IchnoisCoreTimberIntegration installed.")
    addIntegrationToSdkVersion("Timber")
  }

  override fun close() {
    if (this::tree.isInitialized) {
      Timber.uproot(tree)

      if (this::logger.isInitialized) {
        logger.log(IchnoisCoreLevel.DEBUG, "IchnoisCoreTimberIntegration removed.")
      }
    }
  }
}
