# Ichnois Java & Kotlin SDK

The Ichnois Java/Kotlin SDK captures application exceptions, messages, logs, breadcrumbs, user context, and spans, then delivers telemetry to the Ichnois Core Ingestion Gateway.

- **Production gateway:** `https://ichnois-h2jw.onrender.com`
- **Canonical ingestion route:** `POST /api/{project_id}/envelope/`
- **Public Java/Kotlin package:** `io.ichnois`
- **Universal specification:** [UNIVERSAL_SDK_SPECIFICATION.md](UNIVERSAL_SDK_SPECIFICATION.md)

## Quick start

Add the `io.github.ichnois:ichnois` SDK dependency from Maven Central, then initialize it with your own DSN:

```java
import io.ichnois.Ichnois;
import io.ichnois.IchnoisOptions;

IchnoisOptions options = IchnoisOptions.fromDsn(
    "https://PUBLIC_KEY@ichnois-h2jw.onrender.com/api/checkout-service");
options.setEnvironment("production");
options.addInAppInclude("com.example.checkout");
Ichnois.init(options);

try {
  runApplicationWork();
} catch (RuntimeException error) {
  Ichnois.captureException(error);
  throw error;
} finally {
  Ichnois.flush(2000);
}
```

Replace `PUBLIC_KEY` and `checkout-service` with the public key and project ID issued by your Ichnois backend. Do not commit credentials into source control.

## Public API

`Ichnois` exposes `init`, `captureException`, `captureMessage`, `captureLog`, `startSpan`, `finishSpan`, `setUser`, `setTag`, `addBreadcrumb`, `flush`, and `shutdown`. Configuration, release discovery, transport, and local replay details are described in [ICHNOIS_SDK.md](ICHNOIS_SDK.md).

## Repository structure

Source packages use `io.ichnois`, module names use the `ichnois-*` prefix, and the public API is `Ichnois`. `IchnoisCore`-prefixed classes are internal implementation classes used by the SDK and its Java/Kotlin integrations. Third-party license notices are retained.

The wire protocol identifiers `application/x-sentry-envelope`, `X-Sentry-Auth`, and the `Sentry sentry_key=...` authentication scheme are intentionally preserved because the supplied Ichnois backend specification explicitly requires them. These are protocol compatibility strings, not the public SDK brand.

## Build and test

Use a JDK compatible with the Gradle build and allow the Gradle wrapper to download its pinned distribution and dependencies. Compile and run tests before using a customized build in production. This source archive has not been validated against the live ingestion service; send a test event to a non-production project and verify event IDs, release SHA, stack frames, tags, and offline replay before deployment.
