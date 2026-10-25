package io.ichnois.spring;

import com.jakewharton.nopen.annotation.Open;
import java.util.EnumSet;
import java.util.Set;
import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Servlet container initializer used to add the {@link IchnoisCoreSpringFilter} to the {@link
 * ServletContext}.
 */
@Open
public class IchnoisCoreSpringServletContainerInitializer implements ServletContainerInitializer {
  @Override
  public void onStartup(final @Nullable Set<Class<?>> c, final @NotNull ServletContext ctx)
      throws ServletException {
    final FilterRegistration.Dynamic dynamic =
        ctx.addFilter("ichnoisSpringFilter", IchnoisCoreSpringFilter.class);
    if (dynamic != null) {
      dynamic.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "/*");
    }
  }
}
