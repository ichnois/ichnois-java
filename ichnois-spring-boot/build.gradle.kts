import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `java-library`
  id("io.ichnois.javadoc")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.buildconfig)
  id("io.ichnois.animalsniffer")
}

tasks.named<KotlinCompile>("compileKotlin") {
  compilerOptions.jvmTarget = JvmTarget.JVM_1_8
  compilerOptions.languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
  compilerOptions.apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
}

// Spring scans the integration test apps' own bytecode with its repackaged ASM at startup, and
// the ASM in Spring before 5.3.26 fails on class files as new as the toolchain's: "Unsupported
// class file major version 65". In the Spring Boot 2.x matrix, 2.4.13 (Spring 5.3.13) reads up
// to Java 18 and 2.7.0 (Spring 5.3.20) up to Java 19, so the suites target 17. Remove this once
// the matrix drops 2.4.13 and moves 2.7.0 to 2.7.10 or later.
// The suites are Kotlin only. Their Java tasks keep the toolchain's level, because it decides
// which Java version their classpaths ask for, and ichnois-test-support needs the toolchain's.
tasks
  .withType<KotlinCompile>()
  .matching { it.name !in setOf("compileKotlin", "compileTestKotlin") }
  .configureEach {
    compilerOptions.jvmTarget = JvmTarget.JVM_17
    jvmTargetValidationMode = org.jetbrains.kotlin.gradle.dsl.jvm.JvmTargetValidationMode.IGNORE
  }

dependencies {
  constraints {
    testImplementation(libs.okhttp) { version { strictly(libs.versions.okhttp.get()) } }
    testImplementation(libs.okhttp.mockwebserver) {
      version { strictly(libs.versions.okhttp.get()) }
    }
  }

  api(projects.ichnois)
  api(projects.ichnoisSpring)
  compileOnly(projects.ichnoisLogback)
  compileOnly(projects.ichnoisOkhttp)
  compileOnly(libs.okhttp)
  compileOnly(projects.ichnoisApacheHttpClient5)
  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)
  compileOnly(libs.reactor.core)
  compileOnly(libs.servlet.api)
  compileOnly(libs.springboot.starter)
  compileOnly(libs.springboot.starter.aop)
  compileOnly(libs.spring.graphql)
  compileOnly(libs.springboot.starter.quartz)
  compileOnly(libs.springboot.starter.security)
  compileOnly(libs.spring.kafka2)
  compileOnly(platform(libs.springboot2.bom))
  compileOnly(Config.Libs.springWeb)
  compileOnly(Config.Libs.springWebflux)
  compileOnly(projects.ichnoisOpentelemetry.ichnoisOpentelemetryCore)
  compileOnly(projects.ichnoisGraphql)
  compileOnly(projects.ichnoisKafka)
  compileOnly(projects.ichnoisQuartz)

  annotationProcessor(platform(libs.springboot2.bom))
  annotationProcessor(Config.AnnotationProcessors.springBootAutoConfigure)
  annotationProcessor(Config.AnnotationProcessors.springBootConfiguration)

  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  // tests
  testImplementation(projects.ichnoisLogback)
  testImplementation(projects.ichnoisOkhttp)
  testImplementation(projects.ichnoisQuartz)
  testImplementation(projects.ichnoisApacheHttpClient5)
  testImplementation(projects.ichnoisKafka)
  testImplementation(projects.ichnoisTestSupport)
  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.google.truth)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.okhttp)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.otel)
  testImplementation(libs.otel.extension.autoconfigure.spi)
  testImplementation(libs.springboot.starter)
  testImplementation(libs.springboot.starter.aop)
  testImplementation(libs.springboot.starter.quartz)
  testImplementation(libs.springboot.starter.security)
  testImplementation(libs.spring.kafka2)
  testImplementation(libs.springboot.starter.test)
  testImplementation(libs.springboot.starter.web)
  testImplementation(libs.springboot.starter.webflux)
  testImplementation(libs.springboot3.otel)
  testImplementation(projects.ichnoisOpentelemetry.ichnoisOpentelemetryCore)
  testImplementation(projects.ichnoisOpentelemetry.ichnoisOpentelemetryAgent)
  testImplementation(projects.ichnoisOpentelemetry.ichnoisOpentelemetryAgentcustomization)
  testImplementation(projects.ichnoisOpentelemetry.ichnoisOpentelemetryBootstrap)
  testImplementation(projects.ichnoisAsyncProfiler)
}

buildConfig {
  useJavaOutput()
  packageName("io.ichnois.spring.boot")
  buildConfigField(
    "String",
    "ICHNOIS_SPRING_BOOT_SDK_NAME",
    "\"${Config.IchnoisCore.ICHNOIS_SPRING_BOOT_SDK_NAME}\"",
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
      "IchnoisCore-SDK-Name" to Config.IchnoisCore.ICHNOIS_SPRING_BOOT_SDK_NAME,
      "IchnoisCore-SDK-Package-Name" to "maven:io.ichnois:ichnois-spring-boot",
      "Implementation-Vendor" to "IchnoisCore",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}

// Spring Boot supports GraphQL only from 2.7, and its Kafka auto-configuration needs 2.7 for the
// spring-kafka version IchnoisCore integrates with. The Spring Boot 2 matrix also covers older versions.
val bootSupportsGraphqlAndKafka =
  libs.versions.springboot2.get().split(".").let { (major, minor) ->
    major.toInt() > 2 || minor.toInt() >= 7
  }

testing {
  suites {
    // Runs a Spring Boot app in the test's JVM against MockIchnoisCoreServer. It is a suite of its own
    // because the unit tests' classpath carries the agent, which switches IchnoisCore into agent mode.
    register<JvmTestSuite>("integrationTest") {
      useJUnit()
      dependencies {
        implementation(platform(libs.springboot2.bom))
        implementation(projects.ichnoisSpringBootStarter)
        implementation(projects.ichnoisAsyncProfiler)
        implementation(projects.ichnoisJdbc)
        implementation(projects.ichnoisLogback)
        implementation(projects.ichnoisOkhttp)
        implementation(projects.ichnoisTestSupport)
        implementation(libs.kotlin.test.junit)
        implementation(libs.google.truth)
        implementation(libs.caffeine)
        implementation(libs.okhttp)
        implementation(libs.springboot.starter.aop)
        implementation(libs.springboot.starter.cache)
        implementation(libs.springboot.starter.jdbc)
        implementation(libs.springboot.starter.test)
        implementation(libs.springboot.starter.web)
        implementation(libs.springboot.starter.webflux)
        runtimeOnly(libs.hsqldb)
        if (bootSupportsGraphqlAndKafka) {
          implementation(projects.ichnoisGraphql)
          implementation(projects.ichnoisKafka)
          implementation(libs.springboot.starter.graphql)
          implementation(libs.spring.kafka2)
          implementation(libs.spring.kafka2.test)
        }
      }
    }

    // The same for a reactive app: without the servlet stack, Spring Boot starts a WebFlux server.
    register<JvmTestSuite>("integrationTestWebflux") {
      useJUnit()
      dependencies {
        implementation(platform(libs.springboot2.bom))
        implementation(projects.ichnoisSpringBootStarter)
        implementation(projects.ichnoisAsyncProfiler)
        implementation(projects.ichnoisLogback)
        implementation(projects.ichnoisTestSupport)
        implementation(libs.kotlin.test.junit)
        implementation(libs.google.truth)
        implementation(libs.springboot.starter.test)
        implementation(libs.springboot.starter.webflux)
        if (bootSupportsGraphqlAndKafka) {
          implementation(projects.ichnoisGraphql)
          implementation(libs.springboot.starter.graphql)
        }
      }
    }

    // The same with IchnoisCore tracing through the OpenTelemetry SDK in the app's JVM instead of the
    // agent.
    register<JvmTestSuite>("integrationTestOtelNoAgent") {
      useJUnit()
      dependencies {
        implementation(platform(libs.springboot2.bom))
        implementation(projects.ichnoisSpringBootStarter)
        implementation(projects.ichnoisOpentelemetry.ichnoisOpentelemetryAgentlessSpring)
        implementation(projects.ichnoisAsyncProfiler)
        implementation(projects.ichnoisJdbc)
        implementation(projects.ichnoisLogback)
        implementation(projects.ichnoisTestSupport)
        implementation(libs.kotlin.test.junit)
        implementation(libs.google.truth)
        implementation(libs.springboot.starter.aop)
        implementation(libs.springboot.starter.jdbc)
        implementation(libs.springboot.starter.test)
        implementation(libs.springboot.starter.web)
        implementation(libs.springboot.starter.webflux)
        runtimeOnly(libs.hsqldb)
        if (bootSupportsGraphqlAndKafka) {
          implementation(projects.ichnoisGraphql)
          implementation(projects.ichnoisKafka)
          implementation(libs.springboot.starter.graphql)
          implementation(libs.spring.kafka2)
          implementation(libs.spring.kafka2.test)
        }
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
    tasks.named<KotlinCompile>(it) {
      compilerOptions.javaParameters = true
      if (!bootSupportsGraphqlAndKafka) {
        exclude("**/Graphql*", "**/Greeting*", "**/Kafka*")
      }
    }
  }
