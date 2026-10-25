package io.ichnois.samples.console;

import io.ichnois.*;
import io.ichnois.clientreport.DiscardReason;
import io.ichnois.jcache.IchnoisCoreJCacheWrapper;
import io.ichnois.protocol.Message;
import io.ichnois.protocol.User;
import io.ichnois.samples.console.kafka.KafkaShowcase;
import java.util.Collections;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;

public class Main {

  private static long numberOfDiscardedSpansDueToOverflow = 0;

  public static void main(String[] args) throws InterruptedException {
    final String kafkaBootstrapServers = System.getenv("ICHNOIS_SAMPLE_KAFKA_BOOTSTRAP_SERVERS");
    final boolean kafkaEnabled =
        kafkaBootstrapServers != null && !kafkaBootstrapServers.trim().isEmpty();

    IchnoisCore.init(
        options -> {
          // NOTE: Replace the test DSN below with YOUR OWN DSN to see the events from this app in
          // your IchnoisCore project/dashboard
          options.setEnableExternalConfiguration(true);
          options.setDsn(System.getenv("ICHNOIS_DSN"));

          // All events get assigned to the release. See more at
          // https://docs.sentry.io/workflow/releases/
          options.setRelease("io.ichnois.samples.console@3.0.0+1");

          // Modifications to event before it goes out. Could replace the event altogether
          options.setBeforeSend(
              (event, hint) -> {
                // Drop an event altogether:
                if (event.getTag("SomeTag") != null) {
                  return null;
                }
                return event;
              });

          options.setBeforeSendTransaction(
              (transaction, hint) -> {
                // Drop a transaction:
                if (transaction.getTag("SomeTransactionTag") != null) {
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
          // To change the verbosity, use:
          // By default it's DEBUG.
          // options.setDiagnosticLevel(IchnoisCoreLevel.ERROR);
          // A good option to have SDK debug log in prod is to use only level ERROR here.

          // Exclude frames from some packages from being "inApp" so are hidden by default in IchnoisCore
          // UI:
          options.addInAppExclude("org.jboss");

          // Include frames from our package
          options.addInAppInclude("io.ichnois.samples");

          // Performance configuration options
          // Set what percentage of traces should be collected
          options.setTracesSampleRate(1.0); // set 0.5 to send 50% of traces

          // Enable cache tracing to create spans for cache operations
          options.setEnableCacheTracing(true);
          options.setEnableQueueTracing(kafkaEnabled);

          // Determine traces sample rate based on the sampling context
          //          options.setTracesSampler(
          //              context -> {
          //                // only 10% of transactions with "/product" prefix will be collected
          //                if (!context.getTransactionContext().getName().startsWith("/products"))
          // {
          //                  return 0.1;
          //                } else {
          //                  return 0.5;
          //                }
          //              });
        });

    IchnoisCore.addBreadcrumb(
        "A 'bad breadcrumb' that will be rejected because of 'BeforeBreadcrumb callback above.'");

    // Data added to the root scope (no PushScope called up to this point)
    // The modifications done here will affect all events sent and will propagate to child scopes.
    IchnoisCore.configureScope(
        scope -> {
          scope.addEventProcessor(new SomeEventProcessor());

          scope.setExtra("SomeExtraInfo", "Some value for extra info");
        });

    // Configures a scope which is only valid within the callback
    IchnoisCore.withScope(
        scope -> {
          scope.setLevel(IchnoisCoreLevel.FATAL);
          scope.setTransaction("main");

          // This message includes the data set to the scope in this block:
          IchnoisCore.captureMessage("Fatal message!");
        });

    // Only data added to the scope on `configureScope` above is included.
    IchnoisCore.captureMessage("Some warning!", IchnoisCoreLevel.WARNING);

    IchnoisCore.addFeatureFlag("my-feature-flag", true);

    IchnoisCore.setAttribute("user.type", "admin");
    IchnoisCore.setAttribute("feature.version", 2);
    captureMetrics();

    // Sending exception:
    Exception exception = new RuntimeException("Some error!");
    IchnoisCore.captureException(exception);

    // An event with breadcrumb and user data
    IchnoisCoreEvent evt = new IchnoisCoreEvent();
    Message msg = new Message();
    msg.setMessage("Detailed event");
    evt.setMessage(msg);
    evt.addBreadcrumb("Breadcrumb directly to the event");
    User user = new User();
    user.setUsername("some@user");
    evt.setUser(user);
    // Group all events with the following fingerprint:
    evt.setFingerprints(Collections.singletonList("NewClientDebug"));
    evt.setLevel(IchnoisCoreLevel.DEBUG);
    IchnoisCore.captureEvent(evt);

    int count = 10;
    for (int i = 0; i < count; i++) {
      String messageContent = "%d of %d items we'll wait to flush to IchnoisCore!";
      Message message = new Message();
      message.setMessage(messageContent);
      message.setFormatted(String.format(messageContent, i, count));
      IchnoisCoreEvent event = new IchnoisCoreEvent();
      event.setMessage(message);

      final Hint hint = new Hint();
      hint.set("level", IchnoisCoreLevel.DEBUG);
      IchnoisCore.captureEvent(event, hint);
    }

    // Cache tracing with JCache (JSR-107)
    //
    // Wrapping a JCache Cache with IchnoisCoreJCacheWrapper creates cache.get, cache.put,
    // cache.remove, and cache.flush spans as children of the active transaction.
    demonstrateCacheTracing();

    // Kafka queue tracing with the kafka-clients producer interceptor and manual consumer tracing.
    //
    // Enable with: ICHNOIS_SAMPLE_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
    if (kafkaEnabled) {
      KafkaShowcase.runKafkaWithIchnoisCoreTracing(kafkaBootstrapServers);
    }

    // Performance feature
    //
    // Transactions collect execution time of the piece of code that's executed between the start
    // and finish of transaction.
    ITransaction transaction = IchnoisCore.startTransaction("transaction name", "op");
    // Transactions can contain one or more Spans
    ISpan outerSpan = transaction.startChild("child");
    Thread.sleep(100);
    // Spans create a tree structure. Each span can have one ore more spans inside.
    ISpan innerSpan = outerSpan.startChild("jdbc", "select * from product where id = :id");
    innerSpan.setStatus(SpanStatus.OK);
    Thread.sleep(300);
    // Finish the span and mark the end time of the span execution.
    // Note: finishing spans does not sent them to IchnoisCore
    innerSpan.finish();
    // Every IchnoisCoreEvent reported during the execution of the transaction or a span, will have trace
    // context attached
    IchnoisCore.captureMessage("this message is connected to the outerSpan");
    outerSpan.finish();
    // marks transaction as finished and sends it together with all child spans to IchnoisCore
    transaction.finish();

    // All events that have not been sent yet are being flushed on JVM exit. Events can be also
    // flushed manually:
    // IchnoisCore.close();
  }

  private static void demonstrateCacheTracing() {
    // Create a JCache CacheManager and Cache using standard JSR-107 API
    CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();
    MutableConfiguration<String, String> config =
        new MutableConfiguration<String, String>().setTypes(String.class, String.class);
    Cache<String, String> rawCache = cacheManager.createCache("myCache", config);

    // Wrap with IchnoisCoreJCacheWrapper to enable cache tracing
    Cache<String, String> cache = new IchnoisCoreJCacheWrapper<>(rawCache);

    // All cache operations inside a transaction produce child spans
    ITransaction transaction = IchnoisCore.startTransaction("cache-demo", "demo");
    try (IIchnoisCoreLifecycleToken ignored = transaction.makeCurrent()) {
      // cache.put span
      cache.put("greeting", "hello");

      // cache.get span (hit — returns "hello", cache.hit = true)
      cache.get("greeting");

      // cache.get span (miss — returns null, cache.hit = false)
      cache.get("nonexistent");

      // cache.remove span
      cache.remove("greeting");

      // cache.flush span
      cache.clear();
    } finally {
      transaction.finish();
    }

    // Clean up
    cacheManager.destroyCache("myCache");
    cacheManager.close();
  }

  private static void captureMetrics() {
    IchnoisCore.metrics().count("countMetric");
    IchnoisCore.metrics().gauge("gaugeMetric", 5.0);
    IchnoisCore.metrics().distribution("distributionMetric", 7.0);
  }

  private static class SomeEventProcessor implements EventProcessor {
    @Override
    public IchnoisCoreEvent process(IchnoisCoreEvent event, Hint hint) {
      // Here you can modify the event as you need
      if (event.getLevel() != null && event.getLevel().ordinal() > IchnoisCoreLevel.INFO.ordinal()) {
        event.addBreadcrumb(new Breadcrumb("Processed by " + SomeEventProcessor.class));
      }

      return event;
    }
  }
}
