package io.ichnois.spring;

import io.ichnois.IchnoisCoreOptions;
import io.ichnois.protocol.User;
import io.ichnois.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolves user information from Spring Security {@link Authentication} obtained via {@link
 * SecurityContextHolder}.
 */
public final class SpringSecurityIchnoisCoreUserProvider implements IchnoisCoreUserProvider {
  private final @NotNull IchnoisCoreOptions options;

  public SpringSecurityIchnoisCoreUserProvider(final @NotNull IchnoisCoreOptions options) {
    this.options = Objects.requireNonNull(options, "options is required");
  }

  @Override
  public @Nullable User provideUser() {
    if (options.getDataCollectionResolver().isUserInfo()) {
      final SecurityContext context = SecurityContextHolder.getContext();
      if (context != null && context.getAuthentication() != null) {
        final User user = new User();
        user.setUsername(context.getAuthentication().getName());
        return user;
      }
    }
    return null;
  }
}
