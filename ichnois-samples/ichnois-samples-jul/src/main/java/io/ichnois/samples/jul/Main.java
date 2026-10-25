package io.ichnois.samples.jul;

import io.ichnois.IchnoisCore;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.slf4j.MDC;

public class Main {

  private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

  public static void main(String[] args) throws Exception {
    // instead of the following line you can also pass
    // -Djava.util.logging.config.file=.../logging.properties to the
    // java command
    LogManager.getLogManager()
        .readConfiguration(Main.class.getClassLoader().getResourceAsStream("logging.properties"));
    LOGGER.config("Hello IchnoisCore!");

    // MDC parameters are converted to IchnoisCore Event tags
    MDC.put("userId", UUID.randomUUID().toString());
    MDC.put("requestId", UUID.randomUUID().toString());

    IchnoisCore.setAttribute("user.type", "admin");
    IchnoisCore.setAttribute("feature.version", 2);
    IchnoisCore.setAttribute("debug.enabled", true);
    IchnoisCore.addFeatureFlag("my-feature-flag", true);

    LOGGER.warning("important warning");

    // logging arguments are converted to IchnoisCore Event parameters
    LOGGER.log(Level.INFO, "User has made a purchase of product: %d", 445);

    try {
      throw new RuntimeException("Invalid productId=445");
    } catch (Throwable e) {
      LOGGER.log(Level.SEVERE, "Something went wrong", e);
    }
  }
}
