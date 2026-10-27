import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
  `java-library`
  id("io.ichnois.javadoc")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.buildconfig)
  alias(libs.plugins.springboot4) apply false
}

// Published code targets Java 17. Test code is not published and follows the toolchain.
tasks.named<JavaCompile>("compileJava") { options.release.set(17) }

tasks.named<KotlinCompile>("compileKotlin") {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9)
    freeCompilerArgs.add("-Xjsr305=strict")
  }
}

dependencies {
  api(projects.ichnois)
  api(projects.ichnoisSpring7)
  compileOnly(projects.ichnoisLogback)
  compileOnly(projects.ichnoisLog4j2)
  compileOnly(projects.ichnoisApacheHttpClient5)
  compileOnly(platform(SpringBootPlugin.BOM_COORDINATES))
  compileOnly(projects.ichnoisGraphql)
  compileOnly(projects.ichnoisGraphql22)
  compileOnly(projects.ichnoisQuartz)
  compileOnly(libs.spring.kafka4)
  compileOnly(Config.Libs.springWeb)
  compileOnly(Config.Libs.springWebflux)
  compileOnly(libs.context.propagation)
  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)
  compileOnly(libs.log4j.api)
  compileOnly(libs.log4j.core)
  compileOnly(projects.ichnoisOkhttp)
  compileOnly(libs.okhttp)
  compileOnly(libs.otel)
  compileOnly(libs.reactor.core)
  compileOnly(libs.servlet.jakarta.api)
  compileOnly(libs.springboot4.starter)
  compileOnly(libs.springboot4.starter.aspectj)
  compileOnly(libs.springboot4.starter.graphql)
  compileOnly(libs.springboot4.starter.quartz)
  compileOnly(libs.springboot4.starter.security)
  compileOnly(libs.springboot4.starter.restclient)
  compileOnly(libs.springboot4.starter.webclient)
  compileOnly(projects.ichnoisOpentelemetry.ichnoisOpentelemetryCore)
  compileOnly(projects.ichnoisOpentelemetry.ichnoisOpentelemetryAgentcustomization)
  api(projects.ichnoisReactor)

  annotationProcessor(platform(SpringBootPlugin.BOM_COORDINATES))
  annotationProcessor(Config.AnnotationProcessors.springBootAutoConfigure)
  annotationProcessor(Config.AnnotationProcessors.springBootConfiguration)

  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  // tests
  testImplementation(projects.ichnoisLogback)
  testImplementation(projects.ichnoisLog4j2)
  testImplementation(projects.ichnoisOkhttp)
  testImplementation(projects.ichnoisApacheHttpClient5)
  testImplementation(projects.ichnoisGraphql)
  testImplementation(projects.ichnoisGraphql22)
  testImplementation(projects.ichnoisKafka)
  testImplementation(projects.ichnoisOpentelemetry.ichnoisOpentelemetryCore)
  testImplementation(projects.ichnoisOpentelemetry.ichnoisOpentelemetryAgent)
  testImplementation(projects.ichnoisOpentelemetry.ichnoisOpentelemetryAgentcustomization)
  testImplementation(projects.ichnoisOpentelemetry.ichnoisOpentelemetryBootstrap)
  testImplementation(projects.ichnoisQuartz)
  testImplementation(projects.ichnoisReactor)
  testImplementation(projects.ichnoisTestSupport)
  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(Config.Libs.kotlinReflect)
  testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
  testImplementation(libs.context.propagation)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.google.truth)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.okhttp)
  testImplementation(libs.log4j.api)
  testImplementation(libs.log4j.core)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.otel)
  testImplementation(libs.otel.extension.autoconfigure.spi)
  testImplementation(projects.ichnoisAsyncProfiler)
  /**
   * Adding a version of opentelemetry-spring-boot-starter that doesn't support Spring Boot 4 causes
   * java.lang.IllegalArgumentException: Could not find class
   * [org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration]
   * https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/14363
   */
  //  testImplementation(libs.springboot4.otel)
  testImplementation(libs.springboot4.starter)
  testImplementation(libs.springboot4.starter.aspectj)
  testImplementation(libs.springboot4.starter.graphql)
  testImplementation(libs.spring.kafka4)
  testImplementation(libs.springboot4.starter.quartz)
  testImplementation(libs.springboot4.starter.security)
  testImplementation(libs.springboot4.starter.test)
  testImplementation(libs.springboot4.starter.web)
  testImplementation(libs.springboot4.starter.webflux)
  testImplementation(libs.springboot4.starter.restclient)
  testImplementation(libs.springboot4.starter.webclient)
  testImplementation(libs.springboot4.resttestclient)
}

buildConfig {
  useJavaOutput()
  packageName("io.ichnois.spring.boot4")
  buildConfigField(
    "String",
    "ICHNOIS_SPRING_BOOT_4_SDK_NAME",
    "\"${Config.IchnoisCore.ICHNOIS_SPRING_BOOT_4_SDK_NAME}\"",
  )
  buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
}

tasks.withType<JavaCompile>().configureEach {
  dependsOn(tasks.generateBuildConfig)
  options.errorprone {
    check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
    option("NullAway:AnnotatedPackages", "io.ichnois")
  }
}

tasks.jar {
  manifest {
    attributes(
      "IchnoisCore-Version-Name" to project.version,
      "IchnoisCore-SDK-Name" to Config.IchnoisCore.ICHNOIS_SPRING_BOOT_4_SDK_NAME,
      "IchnoisCore-SDK-Package-Name" to "maven:io.github.ichnois:ichnois-spring-boot-4",
      "Implementation-Vendor" to "IchnoisCore",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}

kotlin {
  explicitApi()
  compilerOptions {
    // skip metadata version check, as Spring 7 / Spring Boot 4 is
    // compiled against a newer version of Kotlin
    freeCompilerArgs.add("-Xskip-metadata-version-check")
  }
}

// Swaps Logback for Log4j2 where a classpath has both starters, which only
// integrationTestLog4j2's has.
dependencies {
  modules {
    module("org.springframework.boot:spring-boot-starter-logging") {
      replacedBy(
        "org.springframework.boot:spring-boot-starter-log4j2",
        "Use Log4j2 instead of Logback",
      )
    }
  }
}

testing {
  suites {
    // Runs a Spring Boot app in the test's JVM against MockIchnoisCoreServer. It is a suite of its own
    // because the unit tests' classpath carries the agent, which switches IchnoisCore into agent mode.
    register<JvmTestSuite>("integrationTest") {
      useJUnit()
      dependencies {
        implementation(platform(SpringBootPlugin.BOM_COORDINATES))
        implementation(projects.ichnoisSpringBoot4Starter)
        implementation(projects.ichnoisAsyncProfiler)
        implementation(projects.ichnoisGraphql22)
        implementation(projects.ichnoisJdbc)
        implementation(projects.ichnoisKafka)
        implementation(projects.ichnoisLogback)
        implementation(projects.ichnoisOkhttp)
        implementation(projects.ichnoisTestSupport)
        // Reactor's automatic context propagation needs it to carry the IchnoisCore scope across
        // threads.
        implementation(libs.context.propagation)
        implementation(libs.kotlin.test.junit)
        implementation(libs.google.truth)
        implementation(libs.caffeine)
        implementation(libs.okhttp)
        implementation(libs.springboot4.resttestclient)
        implementation(libs.springboot4.starter.aspectj)
        implementation(libs.springboot4.starter.cache)
        implementation(libs.springboot4.starter.graphql)
        implementation(libs.springboot4.starter.jdbc)
        implementation(libs.springboot4.starter.kafka)
        implementation(libs.springboot4.starter.restclient)
        implementation(libs.springboot4.starter.test)
        implementation(libs.springboot4.starter.web)
        implementation(libs.springboot4.starter.webclient)
        implementation(libs.springboot4.starter.webflux)
        implementation(libs.spring.kafka4.test)
        runtimeOnly(libs.hsqldb)
      }
    }

    // The servlet app with Log4j2 instead of Logback, so IchnoisCore's auto-configuration attaches its
    // Log4j2 appender.
    register<JvmTestSuite>("integrationTestLog4j2") {
      useJUnit()
      dependencies {
        implementation(platform(SpringBootPlugin.BOM_COORDINATES))
        implementation(projects.ichnoisSpringBoot4Starter)
        implementation(projects.ichnoisLog4j2)
        implementation(projects.ichnoisTestSupport)
        implementation(libs.kotlin.test.junit)
        implementation(libs.google.truth)
        implementation("org.springframework.boot:spring-boot-starter-log4j2")
        implementation(libs.springboot4.resttestclient)
        implementation(libs.springboot4.starter.restclient)
        implementation(libs.springboot4.starter.test)
        implementation(libs.springboot4.starter.web)
      }
    }

    // The same for a reactive app: without the servlet stack, Spring Boot starts a WebFlux server.
    register<JvmTestSuite>("integrationTestWebflux") {
      useJUnit()
      dependencies {
        implementation(platform(SpringBootPlugin.BOM_COORDINATES))
        implementation(projects.ichnoisSpringBoot4Starter)
        implementation(projects.ichnoisAsyncProfiler)
        implementation(projects.ichnoisGraphql22)
        implementation(projects.ichnoisLogback)
        implementation(projects.ichnoisTestSupport)
        // Reactor's automatic context propagation needs it to carry the IchnoisCore scope across
        // threads.
        implementation(libs.context.propagation)
        implementation(libs.kotlin.test.junit)
        implementation(libs.google.truth)
        implementation(libs.caffeine)
        implementation(libs.springboot4.resttestclient)
        // For TestRestTemplate; it doesn't add the servlet stack.
        implementation(libs.springboot4.starter.restclient)
        implementation(libs.springboot4.starter.cache)
        implementation(libs.springboot4.starter.graphql)
        implementation(libs.springboot4.starter.test)
        implementation(libs.springboot4.starter.webclient)
        implementation(libs.springboot4.starter.webflux)
      }
    }

    // The same with IchnoisCore tracing through the OpenTelemetry SDK in the app's JVM instead of the
    // agent.
    register<JvmTestSuite>("integrationTestOtelNoAgent") {
      useJUnit()
      dependencies {
        implementation(platform(SpringBootPlugin.BOM_COORDINATES))
        implementation(projects.ichnoisSpringBoot4Starter)
        implementation(projects.ichnoisOpentelemetry.ichnoisOpentelemetryAgentlessSpring)
        implementation(projects.ichnoisAsyncProfiler)
        implementation(projects.ichnoisGraphql22)
        implementation(projects.ichnoisJdbc)
        implementation(projects.ichnoisKafka)
        implementation(projects.ichnoisLogback)
        implementation(projects.ichnoisTestSupport)
        // Reactor's automatic context propagation needs it to carry the IchnoisCore scope across
        // threads.
        implementation(libs.context.propagation)
        implementation(libs.kotlin.test.junit)
        implementation(libs.google.truth)
        implementation(libs.springboot4.resttestclient)
        implementation(libs.springboot4.starter.aspectj)
        implementation(libs.springboot4.starter.graphql)
        implementation(libs.springboot4.starter.jdbc)
        implementation(libs.springboot4.starter.kafka)
        implementation(libs.springboot4.starter.restclient)
        implementation(libs.springboot4.starter.test)
        implementation(libs.springboot4.starter.web)
        implementation(libs.springboot4.starter.webclient)
        implementation(libs.springboot4.starter.webflux)
        implementation(libs.spring.kafka4.test)
        runtimeOnly(libs.hsqldb)
      }
    }
  }
}

// The Spring Boot Gradle plugin compiles apps with parameter names, which Spring reads, for example
// to resolve `#id` in a cache key.
listOf(
    "compileIntegrationTestKotlin",
    "compileIntegrationTestWebfluxKotlin",
    "compileIntegrationTestOtelNoAgentKotlin",
  )
  .forEach {
    tasks.named<KotlinCompile>(it) { compilerOptions.javaParameters = true }
  }
