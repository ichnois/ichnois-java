package io.ichnois.android.ndk;

import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.android.core.IDebugImagesLoader;
import io.ichnois.android.core.IchnoisCoreAndroidOptions;
import io.sentry.ndk.NativeModuleListLoader;
import io.ichnois.protocol.DebugImage;
import io.ichnois.util.AutoClosableReentrantLock;
import io.ichnois.util.Objects;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Class used for loading the list of debug images from ichnois-native. Using this class requires
 * manually initializing the SDK.
 */
public final class DebugImagesLoader implements IDebugImagesLoader {

  private final @NotNull IchnoisCoreOptions options;

  private final @NotNull NativeModuleListLoader moduleListLoader;

  private static volatile @Nullable List<DebugImage> debugImages;

  /** we need to lock it because it could be called from different threads */
  protected static final @NotNull AutoClosableReentrantLock debugImagesLock =
      new AutoClosableReentrantLock();

  public DebugImagesLoader(
      final @NotNull IchnoisCoreAndroidOptions options,
      final @NotNull NativeModuleListLoader moduleListLoader) {
    this.options = Objects.requireNonNull(options, "The IchnoisCoreAndroidOptions is required.");
    this.moduleListLoader =
        Objects.requireNonNull(moduleListLoader, "The NativeModuleListLoader is required.");
  }

  /**
   * Returns the list of debug images loaded by ichnois-native.
   *
   * @return the list or null.
   */
  @Override
  public @Nullable List<DebugImage> loadDebugImages() {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = debugImagesLock.acquire()) {
      if (debugImages == null) {
        try {
          final io.sentry.ndk.DebugImage[] debugImagesArr = moduleListLoader.loadModuleList();
          if (debugImagesArr != null) {
            debugImages = new ArrayList<>(debugImagesArr.length);
            for (io.sentry.ndk.DebugImage d : debugImagesArr) {
              final DebugImage debugImage = new DebugImage();
              debugImage.setCodeFile(d.getCodeFile());
              debugImage.setDebugFile(d.getDebugFile());
              debugImage.setUuid(d.getUuid());
              debugImage.setType(d.getType());
              debugImage.setDebugId(d.getDebugId());
              debugImage.setCodeId(d.getCodeId());
              debugImage.setImageAddr(d.getImageAddr());
              debugImage.setImageSize(d.getImageSize());
              debugImage.setArch(d.getArch());
              debugImages.add(debugImage);
            }
            options
                .getLogger()
                .log(IchnoisCoreLevel.DEBUG, "Debug images loaded: %d", debugImages.size());
          }
        } catch (Throwable e) {
          options.getLogger().log(IchnoisCoreLevel.ERROR, e, "Failed to load debug images.");
        }
      }
    }
    return debugImages;
  }

  /**
   * Loads debug images for the given set of addresses.
   *
   * @param addresses Set of memory addresses to find debug images for
   * @return Set of matching debug images, or null if debug images couldn't be loaded
   */
  public @Nullable Set<DebugImage> loadDebugImagesForAddresses(
      final @NotNull Set<String> addresses) {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = debugImagesLock.acquire()) {
      final @Nullable List<DebugImage> allDebugImages = loadDebugImages();
      if (allDebugImages == null) {
        return null;
      }
      if (addresses.isEmpty()) {
        return null;
      }

      final Set<DebugImage> referencedImages = filterImagesByAddresses(allDebugImages, addresses);
      if (referencedImages.isEmpty()) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.WARNING,
                "No debug images found for any of the %d addresses.",
                addresses.size());
        return null;
      }

      return referencedImages;
    }
  }

  /**
   * Finds all debug image containing the given addresses. Assumes that the images are sorted by
   * address, which should always be true on Linux/Android and Windows platforms
   *
   * @return All matching debug images or null if none are found
   */
  private @NotNull Set<DebugImage> filterImagesByAddresses(
      final @NotNull List<DebugImage> images, final @NotNull Set<String> addresses) {
    final Set<DebugImage> result = new HashSet<>();

    for (int i = 0; i < images.size(); i++) {
      final @NotNull DebugImage image = images.get(i);
      final @Nullable DebugImage nextDebugImage =
          (i + 1) < images.size() ? images.get(i + 1) : null;
      final @Nullable String nextDebugImageAddress =
          nextDebugImage != null ? nextDebugImage.getImageAddr() : null;

      for (final @NotNull String rawAddress : addresses) {
        try {
          final long address = Long.parseLong(rawAddress.replace("0x", ""), 16);

          final @Nullable String imageAddress = image.getImageAddr();
          if (imageAddress != null) {
            try {
              final long imageStart = Long.parseLong(imageAddress.replace("0x", ""), 16);
              final long imageEnd;

              final @Nullable Long imageSize = image.getImageSize();
              if (imageSize != null) {
                imageEnd = imageStart + imageSize;
              } else if (nextDebugImageAddress != null) {
                imageEnd = Long.parseLong(nextDebugImageAddress.replace("0x", ""), 16);
              } else {
                imageEnd = Long.MAX_VALUE;
              }
              if (address >= imageStart && address < imageEnd) {
                result.add(image);
                // once image is added we can skip the remaining addresses and go straight to the
                // next
                // image
                break;
              }
            } catch (NumberFormatException e) {
              // ignored, invalid debug image address
            }
          }
        } catch (NumberFormatException e) {
          // ignored, invalid address supplied
        }
      }
    }
    return result;
  }

  @Override
  public void clearDebugImages() {
    try (final @NotNull IIchnoisCoreLifecycleToken ignored = debugImagesLock.acquire()) {
      try {
        moduleListLoader.clearModuleList();

        options.getLogger().log(IchnoisCoreLevel.INFO, "Debug images cleared.");
      } catch (Throwable e) {
        options.getLogger().log(IchnoisCoreLevel.ERROR, e, "Failed to clear debug images.");
      }
      debugImages = null;
    }
  }

  @VisibleForTesting
  @Nullable
  List<DebugImage> getCachedDebugImages() {
    return debugImages;
  }
}
