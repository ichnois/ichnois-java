# Ichnois Java/Kotlin SDK implementation notes

This repository provides the Java/Kotlin implementation for the Ichnois Core Ingestion Gateway. The application-facing API is `io.ichnois.Ichnois`; the default production gateway is `https://ichnois-h2jw.onrender.com`.

## Initialization

### DSN configuration

```java
IchnoisOptions options = IchnoisOptions.fromDsn(
    "https://PUBLIC_KEY@ichnois-h2jw.onrender.com/api/checkout-service");
options.setEnvironment("production");
options.addInAppInclude("com.example.checkout");
Ichnois.init(options);
```

### Structured configuration

```java
IchnoisOptions options = new IchnoisOptions();
options.setEndpoint("https://ichnois-h2jw.onrender.com");
options.setProjectId("checkout-service");
options.setApiKey("PUBLIC_KEY");
options.setEnvironment("production");
Ichnois.init(options);
```

The structured endpoint path must be empty or `/api`; provide the project ID separately. The resulting ingestion route is `/api/{project_id}/envelope/`. Project IDs are restricted to letters, digits, dots, underscores, and hyphens. Replace placeholder keys and IDs with values provisioned by your Ichnois backend.

## Public API

- `Ichnois.init(IchnoisOptions)`
- `Ichnois.captureException(Throwable)`
- `Ichnois.captureMessage(String)`
- `Ichnois.captureLog(String level, String message)`
- `Ichnois.startSpan(String)` / `Ichnois.finishSpan(IchnoisSpan)`
- `Ichnois.setUser(Map<String, String>)`
- `Ichnois.setTag(String, String)`
- `Ichnois.addBreadcrumb(Map<String, String>)`
- `Ichnois.flush(long timeoutMillis)` / `Ichnois.shutdown()`

Capture methods return the event identifier. User maps accept `id`, `email`, `username`, and `ip_address`; other keys become custom user data. Breadcrumb maps accept `message`, `category`, `type`, and `level`; remaining keys are recorded as breadcrumb data.

## Release SHA and source attribution

If release is not configured explicitly, the SDK checks `ICHNOIS_RELEASE`, `GIT_COMMIT_SHA`, `VERCEL_GIT_COMMIT_SHA`, `RENDER_GIT_COMMIT`, `HEROKU_SLUG_COMMIT`, `GITHUB_SHA`, `CI_COMMIT_SHA`, and `CIRCLE_SHA1`, then attempts `git rev-parse HEAD`. A valid release is a 7–40 character hexadecimal Git SHA, optionally prefixed by `service@`. If discovery fails, it falls back to `unknown` and logs a warning. Configure a real SHA for reliable source attribution.

## Transport and offline resilience

The SDK uses envelope serialization and the repository's on-disk cache, retry, and replay infrastructure. It sends compressed envelope requests to the canonical `/api/{project_id}/envelope/` route. The DSN parser handles the canonical `/api/{project_id}` path without creating `/api/api/{project_id}`.

The following protocol strings are required by the supplied universal specification and must not be renamed independently of the backend: `application/x-sentry-envelope`, `X-Sentry-Auth`, `Sentry sentry_key=...`, `sentry_version`, and `sentry_client`.

## Validation checklist

1. Compile the core module and run its unit tests with the pinned Gradle distribution and required dependencies available.
2. Configure a non-production Ichnois project and send a controlled exception and message.
3. Verify `event_id`, timestamp, release SHA, exception values, stack frame order, `in_app` classification, environment, service, and `git.commit` tags.
4. Simulate a network failure and confirm cached envelopes are replayed after connectivity returns.
5. Verify the live backend's response and authentication behavior before production deployment.

This repository targets Java/Kotlin and includes integration modules. The other language SDKs mentioned by the universal specification are separate implementations.
