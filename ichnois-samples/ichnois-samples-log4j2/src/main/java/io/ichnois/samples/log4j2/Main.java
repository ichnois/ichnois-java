package io.ichnois.samples.log4j2;

import io.ichnois.IchnoisCore;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

public class Main {
  private static final Logger LOGGER = LogManager.getLogger(Main.class);

  public static void main(String[] args) {
    // The SDK was initialized through the appender configuration because a DSN was set there.
    // Update the DSN in log4j2.xml to see these events in your IchnoisCore dashboard.
    LOGGER.debug("Hello IchnoisCore!");

    // ThreadContext tags listed in log4j2.xml are converted to IchnoisCore Event tags
    ThreadContext.put("userId", UUID.randomUUID().toString());
    ThreadContext.put("requestId", UUID.randomUUID().toString());
    // ThreadContext tag not listed in log4j2.xml
    ThreadContext.put("context-tag", "context-tag-value");

    IchnoisCore.setAttribute("user.type", "admin");
    IchnoisCore.setAttribute("feature.version", 2);
    IchnoisCore.setAttribute("debug.enabled", true);
    IchnoisCore.addFeatureFlag("my-feature-flag", true);

    // logging arguments are converted to IchnoisCore Event parameters
    LOGGER.info("User has made a purchase of product: {}", 445);
    // because minimumEventLevel is set to WARN this raises an event
    LOGGER.warn("Important warning");

    try {
      throw new RuntimeException("Invalid productId=445");
    } catch (Throwable e) {
      LOGGER.error("Something went wrong", e);
    }
  }
}
