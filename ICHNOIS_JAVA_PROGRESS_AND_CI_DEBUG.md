# Ichnois Java SDK Audit, Progress & CI Debugging Report

**Date**: October 2026  
**Repository**: [https://github.com/ichnois/ichnois-java](https://github.com/ichnois/ichnois-java)  
**Local Workspace**: `d:\getichnois\ichnois-java`  
**Base Upstream**: `getsentry/sentry-java`  

---

## 1. Executive Summary

Your friend has completed a **massive, impressive transformation** of the entire Java/Kotlin SDK ecosystem:
- **3,006 files** modified and created (+414,370 lines of code) in commit `474d2c9` (`ichnois_java`).
- The entire codebase has been re-branded and re-namespaced from `io.sentry` to `io.ichnois`.
- All 66 Gradle subprojects have been renamed to `ichnois-*`.
- The public API (`io.ichnois.Ichnois`, `IchnoisOptions`) has been connected to your live backend at `https://ichnois-h2jw.onrender.com`.

### Why the GitHub Actions Workflows Failed
The CI failures are **NOT** due to bad Java SDK code logic. They are caused by **oversanitized CI workflow configuration**:
1. **Non-Existent GitHub Actions**: A bulk find-and-replace changed `getsentry/*` actions to `getichnois/*` (e.g. `getichnois/action-setup-cli`, `getichnois/action-enforce-license-compliance`), which do not exist on GitHub, causing instant CI crashes in 2–4 seconds.
2. **Missing Sentry Corporate Secrets**: Workflows expect Sentry corporate secrets (`GRADLE_ENCRYPTION_KEY`, `FOSSA_API_KEY`, `SAUCE_USERNAME`, `SAUCE_ACCESS_KEY`, `ICHNOIS_AUTH_TOKEN`).
3. **Android Gradle Plugin (SAGP) Matrix**: AGP matrix workflows attempt to resolve `io.ichnois:ichnois-android-gradle-plugin`, which has not yet been published to Maven Central or Gradle Plugin Portal.
4. **Enterprise CI Overhead**: Sentry had 23 separate CI pipelines (FOSSA license checks, SauceLabs cloud emulators, size trackers). For Ichnois, 70% of these pipelines are enterprise internal checks that should be disabled or streamlined.

---

## 2. Detailed Progress Audit: What Your Friend Completed

### A. Namespace & Package Re-architecture
- **Package Hierarchy**: Fully transformed from `io.sentry` to `io.ichnois` across all sources (`src/main/java`, `src/main/kotlin`).
- **Project Structure**: Renamed root project in `settings.gradle.kts` to `ichnois-java-sdk`.
- **Subprojects (66 modules)**:
  - Core Java/Kotlin: `ichnois`, `ichnois-kotlin-extensions`, `ichnois-test-support`
  - Android Ecosystem: `ichnois-android`, `ichnois-android-core`, `ichnois-android-ndk`, `ichnois-android-timber`, `ichnois-android-fragment`, `ichnois-android-navigation`, `ichnois-android-replay`, `ichnois-compose`
  - Framework Integrations: `ichnois-spring`, `ichnois-spring-boot`, `ichnois-spring-boot-starter`, `ichnois-spring-jakarta`, `ichnois-opentelemetry`, `ichnois-apollo`, `ichnois-graphql`, `ichnois-log4j2`, `ichnois-logback`, `ichnois-okhttp`
  - Samples & Verification: `ichnois-samples-console`, `ichnois-samples-spring-boot`, `ichnois-samples-jul`, etc.

### B. Live Backend Gateway & Ingestion Alignment
- **Default Production Gateway**: Configured to `https://ichnois-h2jw.onrender.com`.
- **Canonical Envelope Route**: Fixed to `POST /api/{project_id}/envelope/`.
- **DSN Parser**:
  - Supports alphanumeric slug `project_id` (e.g. `https://PUBLIC_KEY@ichnois-h2jw.onrender.com/api/checkout-service`).
  - Added URL normalization so `/api/` in DSN does not result in `/api/api/{project_id}`.
- **Git Commit SHA Auto-Discovery**:
  - Reads `ICHNOIS_RELEASE`, `GIT_COMMIT_SHA`, `VERCEL_GIT_COMMIT_SHA`, `RENDER_GIT_COMMIT`, `HEROKU_SLUG_COMMIT`, `GITHUB_SHA`, `CI_COMMIT_SHA`, and `CIRCLE_SHA1`.
  - Executes `git rev-parse HEAD` as a local fallback to guarantee GitHub GraphQL blame attribution.

### C. Public API (`io.ichnois.Ichnois`)
Implemented universal specification parity:
- `Ichnois.init(IchnoisOptions)`
- `Ichnois.captureException(Throwable)`
- `Ichnois.captureMessage(String)`
- `Ichnois.captureLog(String level, String message)`
- `Ichnois.startSpan(String)` / `Ichnois.finishSpan(IchnoisSpan)`
- `Ichnois.setUser(Map<String, String>)`
- `Ichnois.setTag(String, String)`
- `Ichnois.addBreadcrumb(Map<String, String>)`
- `Ichnois.flush(long timeoutMillis)` / `Ichnois.shutdown()`

---

## 3. Root-Cause Analysis of CI Failures

| CI Job | Failure Time | Exact Root Cause | Fix Required |
| :--- | :--- | :--- | :--- |
| **Enforce License Compliance** | 2s | `uses: getichnois/action-enforce-license-compliance` does not exist on GitHub; missing `FOSSA_API_KEY` | Delete or disable `enforce-license-compliance.yml` |
| **Update Dependencies** | 4s | `uses: getichnois/github-workflows/updater` does not exist on GitHub; missing `CI_DEPLOY_KEY` | Disable `update-deps.yml` |
| **Build / Build Job ubuntu-latest** | 3s | Line 50 of `build.yml`: `uses: getichnois/action-setup-cli` does not exist on GitHub | Remove or revert action reference |
| **UI Tests Critical** | 11s | Line 78 of `integration-tests-ui.yml`: uses non-existent `getichnois/action-setup-cli`; SauceLabs credentials missing | Disable SauceLabs UI tests in fork |
| **SDK Size Analysis** | 15s | Tries to bundle Android test app without Android SDK/keystores and missing `ICHNOIS_AUTH_TOKEN` | Disable `integration-tests-size.yml` |
| **Spring Boot 2.x, 3.x, 4.x Matrix** | 16–21s | Tries to build sample artifacts with Develocity encryption key (`GRADLE_ENCRYPTION_KEY`) and run system tests without active backend | Streamline matrix to test core Spring Boot modules |
| **AGP Matrix Compatibility** | 1m | Tries to resolve Android Gradle Plugin from local maven (`useSagp`) which is not published | Disable AGP matrix until core is released |
| **CodeQL / Analyze** | 11m | CodeQL attempts to compile all 66 modules including Android NDK C++ CMake without NDK installed | Scope CodeQL to Java core |

---

## 4. Action Plan to Fix CI & Make It Green

### Step 1: Fix Broken GitHub Actions References
Search for `getichnois/` across `.github/workflows/` and either:
1. Revert `getichnois/action-setup-cli` to `getsentry/action-setup-cli` (or remove the CLI snapshot upload step).
2. Remove `getichnois/action-enforce-license-compliance` and `getichnois/github-workflows`.

### Step 2: Disable Enterprise Sentry Checks
Sentry uses proprietary third-party subscriptions:
- FOSSA (License compliance)
- SauceLabs (Real Android cloud emulators)
- Gradle Develocity / Enterprise Cache
Delete or disable these workflow files:
- `.github/workflows/enforce-license-compliance.yml`
- `.github/workflows/update-deps.yml`
- `.github/workflows/integration-tests-macrobenchmark.yml`
- `.github/workflows/integration-tests-ui.yml`
- `.github/workflows/integration-tests-ui-critical.yml`
- `.github/workflows/danger.yml`
- `.github/workflows/validate-pr.yml`

### Step 3: Streamline `build.yml` for Clean Execution
Modify `.github/workflows/build.yml` to simply run:
```yaml
- name: Run Core Tests
  run: ./gradlew :ichnois:check :ichnois-spring:check :ichnois-spring-boot:check --no-daemon
```
This ensures that the main Build job runs fast, tests the Java SDK logic, and passes cleanly on GitHub without requiring external secrets or third-party tools.
