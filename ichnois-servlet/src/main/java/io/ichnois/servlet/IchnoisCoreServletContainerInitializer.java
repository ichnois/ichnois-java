package io.ichnois.servlet;

import com.jakewharton.nopen.annotation.Open;
import io.ichnois.IchnoisCoreIntegrationPackageStorage;
import java.util.Set;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
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
        .addPackage("maven:io.ichnois:ichnois-servlet", BuildConfig.VERSION_NAME);
  }

  @Override
  public void onStartup(@Nullable Set<Class<?>> c, @NotNull ServletContext ctx)
      throws ServletException {
    ctx.addListener(IchnoisCoreServletRequestListener.class);
    IchnoisCoreIntegrationPackageStorage.getInstance().addIntegration("Servlet");
  }
}
