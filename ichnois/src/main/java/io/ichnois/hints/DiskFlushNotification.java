package io.ichnois.hints;

import io.ichnois.protocol.IchnoisCoreId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DiskFlushNotification {
  void markFlushed();

  boolean isFlushable(@Nullable IchnoisCoreId eventId);

  void setFlushable(@NotNull IchnoisCoreId eventId);
}
