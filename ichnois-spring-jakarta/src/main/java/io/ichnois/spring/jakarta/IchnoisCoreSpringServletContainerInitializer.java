package io.ichnois.spring.jakarta;

import static io.ichnois.util.ClassLoaderUtils.classLoaderOrDefault;

import com.jakewharton.nopen.annotation.Open;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import java.util.EnumSet;
import java.util.Set;
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
    try {
      Class.forName(
          "org.springframework.boot.SpringApplication",
          false,
          classLoaderOrDefault(getClass().getClassLoader()));
    } catch (ClassNotFoundException e) {
      final FilterRegistration.Dynamic dynamic =
          ctx.addFilter("ichnoisSpringFilter", IchnoisCoreSpringFilter.class);
      if (dynamic != null) {
        dynamic.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), false, "/*");
      }
    }
  }
}
