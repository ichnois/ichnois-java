enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
  repositories {
    // Prefer local SAGP artifact if one exists; otherwise fall back to libs.versions.toml.
    if (providers.gradleProperty("useSagp").isPresent) {
      mavenLocal {
        content {
          includeGroup("io.sentry")
          includeGroup("io.sentry.android.gradle")
        }
      }
    }
    mavenCentral()
    gradlePluginPortal()
    google()
  }
}

plugins {
    id("com.gradle.develocity") version "4.5.0"
    id("com.gradle.common-custom-user-data-gradle-plugin") version "2.8.0"
}

develocity {
    buildScan {
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "ichnois-java-sdk"
rootProject.buildFileName = "build.gradle.kts"
includeBuild("build-logic")
include(
    "ichnois",
    "ichnois-spotlight",
    "ichnois-kotlin-extensions",
    "ichnois-android-distribution",
    "ichnois-android-core",
    "ichnois-android-ndk",
    "ichnois-android",
    "ichnois-android-timber",
    "ichnois-android-fragment",
    "ichnois-android-navigation",
    "ichnois-android-navigation3",
    "ichnois-android-sqlite",
    "ichnois-android-replay",
    "ichnois-compose",
    "ichnois-apollo",
    "ichnois-apollo-3",
    "ichnois-apollo-4",
    "ichnois-apollo-5",
    "ichnois-system-test-support",
    "ichnois-test-support",
    "ichnois-log4j2",
    "ichnois-logback",
    "ichnois-jul",
    "ichnois-servlet",
    "ichnois-servlet-jakarta",
    "ichnois-apache-http-client-5",
    "ichnois-spring",
    "ichnois-spring-jakarta",
    "ichnois-spring-7",
    "ichnois-spring-boot",
    "ichnois-spring-boot-jakarta",
    "ichnois-spring-boot-starter",
    "ichnois-spring-boot-starter-jakarta",
    "ichnois-spring-boot-4",
    "ichnois-spring-boot-4-starter",
    "ichnois-bom",
    "ichnois-openfeign",
    "ichnois-graphql",
    "ichnois-graphql-22",
    "ichnois-graphql-core",
    "ichnois-jdbc",
    "ichnois-kafka",
    "ichnois-opentelemetry:ichnois-opentelemetry-bootstrap",
    "ichnois-opentelemetry:ichnois-opentelemetry-core",
    "ichnois-opentelemetry:ichnois-opentelemetry-agentcustomization",
    "ichnois-opentelemetry:ichnois-opentelemetry-agent",
    "ichnois-opentelemetry:ichnois-opentelemetry-bom",
    "ichnois-opentelemetry:ichnois-opentelemetry-agentless",
    "ichnois-opentelemetry:ichnois-opentelemetry-agentless-spring",
    "ichnois-opentelemetry:ichnois-opentelemetry-otlp",
    "ichnois-opentelemetry:ichnois-opentelemetry-otlp-spring",
    "ichnois-jcache",
    "ichnois-quartz",
    "ichnois-okhttp",
    "ichnois-openfeature",
    "ichnois-launchdarkly-server",
    "ichnois-launchdarkly-android",
    "ichnois-reactor",
    "ichnois-async-profiler",
    "ichnois-ktor-client",
    "ichnois-samples:ichnois-samples-android",
    "ichnois-samples:ichnois-samples-console",
    "ichnois-samples:ichnois-samples-console-otlp",
    "ichnois-samples:ichnois-samples-console-opentelemetry-noagent",
    "ichnois-samples:ichnois-samples-jul",
    "ichnois-samples:ichnois-samples-ktor-client",
    "ichnois-samples:ichnois-samples-log4j2",
    "ichnois-samples:ichnois-samples-logback",
    "ichnois-samples:ichnois-samples-servlet",
    "ichnois-samples:ichnois-samples-spring",
    "ichnois-samples:ichnois-samples-spring-jakarta",
    "ichnois-samples:ichnois-samples-spring-7",
    "ichnois-samples:ichnois-samples-spring-boot",
    "ichnois-samples:ichnois-samples-spring-boot-opentelemetry",
    "ichnois-samples:ichnois-samples-spring-boot-opentelemetry-noagent",
    "ichnois-samples:ichnois-samples-spring-boot-jakarta",
    "ichnois-samples:ichnois-samples-spring-boot-jakarta-log4j2",
    "ichnois-samples:ichnois-samples-spring-boot-jakarta-opentelemetry",
    "ichnois-samples:ichnois-samples-spring-boot-jakarta-opentelemetry-noagent",
    "ichnois-samples:ichnois-samples-spring-boot-webflux",
    "ichnois-samples:ichnois-samples-spring-boot-webflux-jakarta",
    "ichnois-samples:ichnois-samples-spring-boot-4",
    "ichnois-samples:ichnois-samples-spring-boot-4-log4j2",
    "ichnois-samples:ichnois-samples-spring-boot-4-opentelemetry",
    "ichnois-samples:ichnois-samples-spring-boot-4-opentelemetry-noagent",
    "ichnois-samples:ichnois-samples-spring-boot-4-otlp",
    "ichnois-samples:ichnois-samples-spring-boot-4-webflux",
    "ichnois-samples:ichnois-samples-netflix-dgs",
    "ichnois-android-integration-tests:ichnois-uitest-android-critical",
    "ichnois-android-integration-tests:ichnois-uitest-android-macrobenchmark",
    "ichnois-android-integration-tests:ichnois-uitest-android",
    "ichnois-android-integration-tests:test-app-size",
    "ichnois-samples:ichnois-samples-openfeign"
)
