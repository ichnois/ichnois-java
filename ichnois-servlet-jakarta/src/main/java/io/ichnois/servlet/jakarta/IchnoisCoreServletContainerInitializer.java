package io.ichnois.servlet.jakarta;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.IchnoisCoreIntegrationPackageStorage;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Servlet container initializer used to add the {@link IchnoisCoreServletRequestListener} to the {@link
 * ServletContext}.
 */
@Open
public class IchnoisCoreServletContainerInitializer implements ServletContainerInitializer {

  static {
    IchnoisCoreIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.github.ichnois:ichnois-servlet-jakarta", BuildConfig.VERSION_NAME);
  }

  @Override
  public void onStartup(@Nullable Set<Class<?>> c, @NotNull ServletContext ctx)
      throws ServletException {
    ctx.addListener(IchnoisCoreServletRequestListener.class);
    IchnoisCoreIntegrationPackageStorage.getInstance().addIntegration("Servlet-Jakarta");
  }
}
