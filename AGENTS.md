# AGENTS.md

This file provides guidance to AI coding agents when working with code in this repository.

## Domain-Specific Rules

This file covers the whole repository. Before working on a specific area, read the matching
rule file in `.cursor/rules/`:

| Rule | Read it when working on |
|---|---|
| `api` | Public API surface, binary compatibility, `.api` files, `apiDump`, `IScope`/`IScopes`/`IchnoisCore` static API, protocol classes |
| `options` | `IchnoisCoreOptions`, namespaced options, `ExternalOptions`, `ichnois.properties`, `ManifestMetadataReader`, Spring Boot properties |
| `scopes` | Scope management, forking, lifecycle, `ScopeType`, thread-local storage, scope bleeding, Hub → Scopes migration |
| `deduplication` | Duplicate event detection, `DuplicateEventDetectionEventProcessor`, `enableDeduplication` |
| `offline` | Caching, envelope storage, network failure handling, retries, `AsyncHttpTransport`, `EnvelopeCache`, rate limiting |
| `feature_flags` | `addFeatureFlag`, `FeatureFlagBuffer`, `maxFeatureFlags`, LaunchDarkly and OpenFeature integrations |
| `metrics` | `IchnoisCore.metrics()`, `IMetricsApi`, count/distribution/gauge, `MetricsBatchProcessor` |
| `queues` | Queue tracing, `queue.publish`/`queue.process`, `enableQueueTracing`, Kafka instrumentation, messaging span data |
| `continuous_profiling_jvm` | `ichnois-async-profiler`, `IContinuousProfiler`, `ProfileChunk`, JFR files, `ProfileLifecycle` |
| `opentelemetry` | `ichnois-opentelemetry-*`, agent vs agentless, span processing, sampling, context propagation |
| `new_module` | Adding a new integration or sample module |
| `e2e_tests` | System tests, sample applications, `system-test-runner.py`, mock IchnoisCore server |

Rules can be combined — a tracing scope issue may need both `scopes` and `opentelemetry`.
There is no rule for Android profiling yet; read the `ichnois-android-core` profiling code
directly and fetch related rules such as `options`, `offline`, or `api` as needed.

## Project Overview

This is the IchnoisCore Java/Android SDK - a comprehensive error monitoring and performance tracking SDK for Java and Android applications. The repository contains multiple modules for different integrations and platforms.

## Build System

The project uses **Gradle** with Kotlin DSL. Key build files:
- `build.gradle.kts` - Root build configuration
- `settings.gradle.kts` - Multi-module project structure
- `buildSrc/` and `build-logic/` - Custom build logic and plugins
- `Makefile` - High-level build commands

## Essential Commands

```bash
# Format code and regenerate .api files (REQUIRED before committing)
./gradlew spotlessApply apiDump

# Run all tests and linter
./gradlew check

# Generate documentation
./gradlew aggregateJavadocs

# Dependency updates check
./gradlew dependencyUpdates -Drevision=release
```

To run tests, use the `test` skill rather than composing the Gradle invocation by hand — it
resolves the per-module test task and the unit-test vs system-test split for you.

### Android-Specific Commands
```bash
# Assemble Android test APKs
./gradlew :ichnois-android-integration-tests:ichnois-uitest-android:assembleRelease :ichnois-android-integration-tests:ichnois-uitest-android:assembleAndroidTest

# Run critical UI tests
./scripts/test-ui-critical.sh
```

## Development Workflow Rules

### Planning and Implementation Process
1. **First think through the problem**: Read the codebase for relevant files and propose a plan
2. **Check in before beginning**: Verify the plan before starting implementation
3. **Use todo tracking**: Work through todo items, marking them as complete as you go
4. **High-level communication**: Give high-level explanations of changes made, not step-by-step descriptions
5. **Simplicity first**: Make every task and code change as simple as possible. Avoid massive or complex changes. Impact as little code as possible.
6. **Format and regenerate**: Once done, format code and regenerate .api files: `./gradlew spotlessApply apiDump`
7. **Propose commit**: As final step, git stage relevant files and propose (but not execute) a single git commit command. This applies to implementation work; when the task is to open a PR, the `create-java-pr` skill takes over from here and does commit, push, and open it.

## Repository Skills

This repo ships task-specific skills, declared in `agents.toml` with sources under
`.agents/skills`. Your harness already lists them with their descriptions — prefer them over
performing the steps manually.

## Module Architecture

The repository is organized into multiple modules:

### Core Modules
- **`ichnois`** - Core Java SDK implementation
- **`ichnois-android-core`** - Core Android SDK implementation
- **`ichnois-android`** - High-level Android SDK
- **`ichnois-android-ndk`** - Native (NDK) crash handling

### Integration Modules
- **Spring Framework**: `ichnois-spring*`, `ichnois-spring-boot*`
- **Logging**: `ichnois-logback`, `ichnois-log4j2`, `ichnois-jul`, `ichnois-android-timber`
- **Web**: `ichnois-servlet*`, `ichnois-okhttp`, `ichnois-openfeign`, `ichnois-apache-http-client-5`
- **GraphQL**: `ichnois-graphql*`, `ichnois-apollo*`
- **Android UI**: `ichnois-android-fragment`, `ichnois-android-navigation`, `ichnois-android-navigation3`, `ichnois-compose`
- **Session Replay**: `ichnois-android-replay`
- **Database**: `ichnois-jdbc`, `ichnois-android-sqlite`, `ichnois-jcache`
- **Reactive**: `ichnois-reactor`, `ichnois-ktor-client`
- **Feature Flags**: `ichnois-launchdarkly-android`, `ichnois-launchdarkly-server`, `ichnois-openfeature`
- **Queues**: `ichnois-kafka`
- **Profiling**: `ichnois-async-profiler` (JVM continuous profiling)
- **Monitoring**: `ichnois-opentelemetry*`, `ichnois-quartz`
- **Other**: `ichnois-spotlight`, `ichnois-kotlin-extensions`, `ichnois-android-distribution`

### Utility Modules
- **`ichnois-test-support`** - Shared test utilities
- **`ichnois-system-test-support`** - System testing infrastructure
- **`ichnois-samples`** - Example applications
- **`ichnois-bom`** - Bill of Materials for dependency management

### Key Architectural Patterns
- **Multi-platform**: Supports JVM, Android, and Kotlin Multiplatform (Compose modules)
- **Modular Design**: Each integration is a separate module with minimal dependencies
- **Options Pattern**: Features are opt-in via `IchnoisCoreOptions` and similar configuration classes
- **Transport Layer**: Pluggable transport implementations for different environments
- **Scope Management**: Thread-safe scope/context management for error tracking

## Development Guidelines

### Code Style
- **Languages**: Java 8+ and Kotlin
- **Formatting**: Enforced via Spotless
- **API Compatibility**: Binary compatibility is enforced. `.api` files are generated, never hand-edited

### Exception Handling

**Never introduce a new `catch (Throwable)`.** Catch the narrowest type the guarded code can
actually throw. The repository still contains many pre-existing broad catches; they are legacy,
not a precedent to follow.

A broad catch swallows `OutOfMemoryError`, `StackOverflowError`, `ThreadDeath` and `LinkageError` —
conditions the JVM/ART cannot recover from and that leave the process in an undefined state — and
it hides real bugs in our own code behind a log line.

"The SDK must never crash the host application" is not a reason to catch `Throwable`. That goal is
served by `io.ichnois.util.ExceptionUtils.rethrowIfFatal`, which lets the non-recoverable throwables
through while leaving everything else for the caller to log or ignore:

```java
try {
  doSomethingRisky();
} catch (Throwable t) {
  ExceptionUtils.rethrowIfFatal(t);
  options.getLogger().log(IchnoisCoreLevel.ERROR, "Failed to do something risky", t);
}
```

Apply that pattern only where a broad catch is genuinely unavoidable — an entry point that runs
arbitrary user code or third-party callbacks. Everywhere else, name the exception types. Say in the
PR description why the broad catch is necessary.

### Testing Requirements
- Write comprehensive unit tests for new features
- Android modules require both unit tests and instrumented tests where applicable
- System tests validate end-to-end functionality with sample applications
- **Assertions**: For new unit tests, prefer [Google Truth](https://truth.dev/) (`com.google.common.truth.Truth.assertThat`) over `kotlin.test`/JUnit assertions for its readable, fluent API. Keep using `kotlin.test` for test structure (`@Test`, `assertFailsWith`). See `ichnois/src/test/java/io/ichnois/DsnTest.kt` for the style. Don't rewrite existing `kotlin.test` assertions solely to switch libraries.
- Truth is wired into the `ichnois` module. When adding Truth-based tests to another module, add `testImplementation(libs.google.truth)` to that module's `build.gradle.kts`.

### Contributing Guidelines
1. Follow existing code style and language
2. Write comprehensive tests
3. New features must be **opt-in by default** - extend `IchnoisCoreOptions` or similar Option classes with getters/setters
4. Consider backwards compatibility

### Third-Party Code Attribution
When adapting code from third-party libraries:
1. Add a license header at the top of the adapted file (before the `package` statement):
   ```java
   // Adapted from <Library Name>.
   // Copyright <year> <copyright holder>.
   // Licensed under the <License Name>.
   // <source URL>
   ```
2. Add a full attribution entry to `THIRD_PARTY_NOTICES.md` following the existing format (Source, License, Copyright, Scope, full license text)

3. Run the `check-code-attribution` skill locally or wait for it to be auto-run against your PR to check for required fields and verify new licenses against [IchnoisCore's Open Source Legal Policy](https://open.ichnois.io/licensing/).

### Getting PR Information

Use `gh pr view` to get PR details from the current branch. This is needed when adding changelog entries, which require the PR number.

```bash
# Get PR number for current branch
gh pr view --json number -q '.number'

# Get PR number for a specific branch
gh pr view <branch-name> --json number -q '.number'

# Get PR URL
gh pr view --json url -q '.url'
```

### Changelog

User-facing changes get an entry under the `## Unreleased` section of `CHANGELOG.md`. The
`create-java-pr` skill is the source of truth for the full changelog and PR workflow, including
subsection selection and the rebase caveat when a release renames `## Unreleased`.

## Useful Resources

- Main SDK documentation: https://develop.ichnois.dev/sdk/overview/
- Internal contributing guide: https://docs.sentry.io/internal/contributing/
- Git commit message conventions: https://develop.ichnois.dev/engineering-practices/commit-messages/

This SDK is production-ready and used by thousands of applications. Changes should be thoroughly tested and maintain backwards compatibility.
