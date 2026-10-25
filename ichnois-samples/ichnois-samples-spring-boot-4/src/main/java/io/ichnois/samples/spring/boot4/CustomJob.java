package io.ichnois.samples.spring.boot4;

import io.ichnois.spring7.checkin.IchnoisCoreCheckIn;
import io.ichnois.spring7.tracing.IchnoisCoreTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link IchnoisCoreTransaction} added on the class level, creates transaction around each method
 * execution of every method of the annotated class.
 */
@Component
@IchnoisCoreTransaction(operation = "scheduled")
public class CustomJob {

  private static final Logger LOGGER = LoggerFactory.getLogger(CustomJob.class);

  @IchnoisCoreCheckIn("monitor_slug_1")
  //  @Scheduled(fixedRate = 3 * 60 * 1000L)
  void execute() throws InterruptedException {
    LOGGER.info("Executing scheduled job");
    Thread.sleep(2000L);
  }
}
