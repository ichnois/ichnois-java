package io.ichnois.spring7;

import io.ichnois.protocol.User;
import org.jetbrains.annotations.Nullable;

/**
 * Out of the box Spring integration configures single {@link IchnoisCoreUserProvider} - {@link
 * HttpServletRequestIchnoisCoreUserProvider}.
 */
@FunctionalInterface
public interface IchnoisCoreUserProvider {
  @Nullable
  User provideUser();
}
