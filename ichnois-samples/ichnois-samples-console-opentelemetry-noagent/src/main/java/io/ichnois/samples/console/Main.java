package io.ichnois.samples.console;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.ichnois.Breadcrumb;
import io.ichnois.EventProcessor;
import io.ichnois.Hint;
import io.ichnois.IIchnoisCoreLifecycleToken;
import io.ichnois.ISpan;
import io.ichnois.ITransaction;
import io.ichnois.IchnoisCore;
import io.ichnois.IchnoisCoreEvent;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.SpanStatus;
import io.ichnois.protocol.Message;
import io.ichnois.protocol.User;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Main {

  public static void main(String[] args) throws InterruptedException {
    AutoConfiguredOpenTelemetrySdk.builder()
        .setResultAsGlobal()
        .addPropertiesSupplier(
            () -> {
              final Map<String, String> properties = new HashMap<>();
              properties.put("otel.logs.exporter", "none");
              properties.put("otel.metrics.exporter", "none");
              properties.put("otel.traces.exporter", "none");
              return properties;
            })
        .build();

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
    // Note: finishing spans does not send them to IchnoisCore
    innerSpan.finish();
    try (IIchnoisCoreLifecycleToken outerScope = outerSpan.makeCurrent()) {
      Span otelSpan =
          GlobalOpenTelemetry.get()
              .getTracer("demoTracer", "1.0.0")
              .spanBuilder("otelSpan")
              .startSpan();
      try (Scope innerScope = otelSpan.makeCurrent()) {
        otelSpan.setAttribute("otel-attribute", "attribute-value");
        Thread.sleep(150);
        otelSpan.setStatus(StatusCode.OK);
      } finally {
        otelSpan.end();
      }
    }
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
