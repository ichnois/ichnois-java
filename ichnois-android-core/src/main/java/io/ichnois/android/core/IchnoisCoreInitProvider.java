package io.ichnois.android.core;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import io.ichnois.IchnoisCore;
import io.ichnois.IchnoisCoreIntegrationPackageStorage;
import io.ichnois.IchnoisCoreLevel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class IchnoisCoreInitProvider extends EmptySecureContentProvider {

  @Override
  public boolean onCreate() {
    AndroidLogger logger = new AndroidLogger();
    final Context context = getContext();
    if (context == null) {
      logger.log(IchnoisCoreLevel.FATAL, "App. Context from ContentProvider is null");
      return false;
    }

    if (ManifestMetadataReader.isAutoInit(context, logger)
        && !ContextUtils.appIsLibraryForComposePreview(context)) {
      IchnoisCoreAndroid.init(context, logger);
      IchnoisCoreIntegrationPackageStorage.getInstance().addIntegration("AutoInit");
    }
    return true;
  }

  @Override
  public void shutdown() {
    IchnoisCore.close();
  }

  @Override
  public void attachInfo(@NotNull Context context, @NotNull ProviderInfo info) {
    // applicationId is expected to be prepended. See AndroidManifest.xml
    if (IchnoisCoreInitProvider.class.getName().equals(info.authority)) {
      throw new IllegalStateException(
          "An applicationId is required to fulfill the manifest placeholder.");
    }
    super.attachInfo(context, info);
  }

  @Override
  public @Nullable String getType(@NotNull Uri uri) {
    return null;
  }
}
