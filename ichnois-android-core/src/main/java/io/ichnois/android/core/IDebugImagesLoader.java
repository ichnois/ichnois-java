package io.ichnois.android.core;

import io.ichnois.protocol.DebugImage;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/** Used for loading the list of debug images from ichnois-native. */
@ApiStatus.Internal
public interface IDebugImagesLoader {
  @Nullable
  List<DebugImage> loadDebugImages();

  @Nullable
  Set<DebugImage> loadDebugImagesForAddresses(Set<String> addresses);

  void clearDebugImages();
}
