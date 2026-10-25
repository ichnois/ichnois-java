package io.ichnois.samples.servlet;

import io.ichnois.DataCategory;
import io.ichnois.IchnoisCore;
import io.ichnois.clientreport.DiscardReason;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/** Initializes IchnoisCore. */
@WebListener
public final class IchnoisCoreInitializer implements ServletContextListener {

  private static long numberOfDiscardedSpansDueToOverflow = 0;

  @Override
  public void contextInitialized(ServletContextEvent sce) {
    IchnoisCore.init(
        options -> {
          // NOTE: Replace the test DSN below with YOUR OWN DSN to see the events from this app in
          // your IchnoisCore project/dashboard
          options.setDsn(
              "https://test-public-key@ichnois-h2jw.onrender.com/5428563");

          // disables shutdown hook, as IchnoisCore has to be closed on application undeploy.
          options.setEnableShutdownHook(false);

          // All events get assigned to the release. See more at
          // https://docs.sentry.io/workflow/releases/
          options.setRelease("io.ichnois.samples.servlet@3.0.0+1");

          // Modifications to event before it goes out. Could replace the event altogether
          options.setBeforeSend(
              (event, hint) -> {
                // Drop an event altogether:
                if (event.getTag("SomeTag") != null) {
                  return null;
                }
                return event;
              });

          // Modifications to transaction before it goes out. Could replace the transaction
          // altogether
          options.setBeforeSendTransaction(
              (transaction, hint) -> {
                // Drop a transaction altogether:
                if (transaction.getTag("SomeTag") != null) {
                  return null;
                }
                return transaction;
              });

          // Allows inspecting and modifying, returning a new or simply rejecting (returning null)
          options.setBeforeBreadcrumb(
              (breadcrumb, hint) -> {
                // Don't add breadcrumbs with message containing:
                if (breadcrumb.getMessage() != null
                    && breadcrumb.getMessage().contains("bad breadcrumb")) {
                  return null;
                }
                return breadcrumb;
              });

          // Record data being discarded, including the reason, type of data, and the number of
          // items dropped
          options.setOnDiscard(
              (reason, category, number) -> {
                // Only record the number of lost spans due to overflow conditions
                if ((reason.equals(DiscardReason.CACHE_OVERFLOW)
                        || reason.equals(DiscardReason.QUEUE_OVERFLOW))
                    && category.equals(DataCategory.Span)) {
                  numberOfDiscardedSpansDueToOverflow += number;
                }
              });

          // Configure the background worker which sends events to ichnois:
          // Wait up to 5 seconds before shutdown while there are events to send.
          options.setShutdownTimeoutMillis(5000);

          // Enable SDK logging with Debug level
          options.setDebug(true);

          // Include frames from our package
          options.addInAppInclude("io.ichnois.samples");
        });
  }

  @Override
  public void contextDestroyed(ServletContextEvent sce) {
    IchnoisCore.flush(5000);
    IchnoisCore.close();
  }
}
