# ichnois-opentelemetry

## OpenTelemetry

More information on OpenTelemetry can be found on their [website](https://opentelemetry.io/) as well
as their docs and GitHub repos:
- https://opentelemetry.io/docs/instrumentation/java/getting-started/
- https://github.com/open-telemetry/opentelemetry-java
- https://github.com/open-telemetry/opentelemetry-java-instrumentation

## Modules

### [`ichnois-opentelemetry-agent`](ichnois-opentelemetry-agent/README.md)

Produces the IchnoisCore OpenTelemetry Java Agent `JAR` that can be used to auto instrument an 
application. Please see the module [README](ichnois-opentelemetry-agent/README.md) for more details on how to use it.

### `ichnois-opentelemetry-agentcustomization`

This contains customizations to the OpenTelemetry Java Agent such as registering the
`IchnoisCoreSpanProcessor` and `IchnoisCorePropagator` as well as providing default properties that
enable the `ichnois` propagator.

### `ichnois-opentelemetry-bootstrap`

Classes that are loaded into the bootstrap classloader
(represented as `null` when invoking X.class.classLoader)
These are shared between the agent and the application and include things like storage,
utils, factory, tokens etc.

If you want to use IchnoisCore with OpenTelemetry without the agent,
you also need this module as a dependency.

### `ichnois-opentelemetry-core`

Contains `IchnoisCoreSpanProcessor` and `IchnoisCorePropagator` which are used by our Java Agent but can also
be used when manually instrumenting using OpenTelemetry. If you want to use OpenTelemetry without
the agent but still want some configuration convenience, you should rather use the
`ichnois-opentelemetry-agentless` module or the `ichnois-opentelemetry-agentless-spring` module if you are using Spring Boot.

### `ichnois-opentelemetry-agentless`
Combines all modules and dependencies needed to use IchnoisCore with OpenTelemetry without the agent.

### `ichnois-opentelemetry-agentless-spring`
Combines all modules and dependencies needed to use IchnoisCore with OpenTelemetry in Spring Boot without an agent.

## Running without an Agent
If you want to use IchnoisCore with OpenTelemetry without an agent, you can do so by adding the `ichnois-opentelemetry-agentless` (or `ichnois-opentelemetry-agentless-spring`) module as dependency to your project. 

And run your application with the following JVM arguments:
```
-Dotel.java.global-autoconfigure.enabled=true
```
You may also want to set the following environment variables to if you do not use OpenTelemetry exporters:
`OTEL_LOGS_EXPORTER=none;OTEL_METRICS_EXPORTER=none;OTEL_TRACES_EXPORTER=none`

Alternatively you can initialize OpenTelemetry programmatically like this:

```java
// Initialize OpenTelemetry by using the AutoConfiguredOpenTelemetrySdk which automatically
// registers the `IchnoisCoreSpanProcessor` and `IchnoisCorePropagator` and others.
// Also, you need to disable the OTEL exporters if you do not use them.
AutoConfiguredOpenTelemetrySdk.builder()
  .setResultAsGlobal()
  .addPropertiesSupplier(() -> {
final Map<String, String> properties = new HashMap<>();
    properties.put("otel.logs.exporter", "none");
    properties.put("otel.metrics.exporter", "none");
    properties.put("otel.traces.exporter", "none");
    return properties;
  })
  .build();
```

And then initialize IchnoisCore as usual:

```java
// Initialize IchnoisCore
IchnoisCore.init(
    options -> {
    options.setDsn("...");
      ...
  }
)
```
