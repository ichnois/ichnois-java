package io.ichnois;

import io.ichnois.rrweb.RRWebEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface ReplayBreadcrumbConverter {
  @Nullable
  RRWebEvent convert(@NotNull Breadcrumb breadcrumb);
}
