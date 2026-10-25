# ichnois-opentelemetry-agent

## How to use it

Download the latest `ichnois-opentelemetry-agent.jar` and use it when launching your Java
application by adding the following parameters to your `java` command:

`$ ICHNOIS_PROPERTIES_FILE=ichnois.properties java -javaagent:ichnois-opentelemetry-agent.jar -jar your-application.jar`

Your `ichnois.properties` could look like this:

```properties
# NOTE: Replace the test DSN below with YOUR OWN DSN to see the events from this app in your IchnoisCore project/dashboard
dsn=https://test-public-key@ichnois-h2jw.onrender.com/5428563
traces-sample-rate=1.0
```

For more details on configuring IchnoisCore via `ichnois.properties` please see the
[docs page](https://docs.sentry.io/platforms/java/configuration/).

As an alternative to the `ICHNOIS_PROPERTIES_FILE` environment variable you can provide individual
settings as environment variables (e.g. `ICHNOIS_DSN=...`).

## Controlling auto initialization of IchnoisCore

By default, if you pass either `ICHNOIS_DSN` or `ICHNOIS_PROPERTIES_FILE` as environment variable,
IchnoisCore will automatically be initialized by this agent. To disable this behaviour, you can set
`ICHNOIS_AUTO_INIT=false` as environment variable. You will then have to use a IchnoisCore SDK that takes care of initialization or initialize IchnoisCore manually inside
the target application:

```
IchnoisCore.init(
        options -> {
          options.setDsn("...");
          ...
        }
)
```

## Debugging

To enable debug logging for IchnoisCore, please provide `ICHNOIS_DEBUG=true` as environment variable or
add `debug=true` to your `ichnois.properties`.

To also show debug output for OpenTelemetry please add `-Dotel.javaagent.debug=true` to the command.

## Getting rid of exporter error messages

In case you are using this agent without needing to use any OpenTelemetry exporters you can add
the following environment variables to turn off exporters and stop seeing error messages about 
servers not being reachable in the logs.

Example log message:
```
ERROR io.opentelemetry.exporter.internal.grpc.OkHttpGrpcExporter - Failed to export spans. The request could not be executed. Full error message: Failed to connect to localhost/[0:0:0:0:0:0:0:1]:4317
ERROR io.opentelemetry.exporter.internal.grpc.OkHttpGrpcExporter - Failed to export metrics. The request could not be executed. Full error message: Failed to connect to localhost/[0:0:0:0:0:0:0:1]:4317
ERROR io.opentelemetry.exporter.internal.http.HttpExporter - Failed to export logs. The request could not be executed. Full error message: Failed to connect to localhost/[0:0:0:0:0:0:0:1]:4318
```

### Traces

To turn off exporting of traces you can set `OTEL_TRACES_EXPORTER=none`
see [OpenTelemetry GitHub](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure#otlp-exporter-span-metric-and-log-exporters)

### Metrics

To turn off exporting of metrics you can set `OTEL_METRICS_EXPORTER=none`
see [OpenTelemetry GitHub](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure#otlp-exporter-span-metric-and-log-exporters)

### Logs

To turn off log exporting, set `OTEL_LOGS_EXPORTER=none` 
see [OpenTelemetry GitHub](https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure#otlp-exporter-span-metric-and-log-exporters).
