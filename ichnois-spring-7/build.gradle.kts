import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
  `java-library`
  id("io.ichnois.javadoc")
  alias(libs.plugins.kotlin.jvm)
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
    languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
    freeCompilerArgs.add("-Xjsr305=strict")
  }
}

dependencies {
  api(projects.ichnois)

  compileOnly(platform(SpringBootPlugin.BOM_COORDINATES))

  compileOnly(Config.Libs.springWeb)
  compileOnly(Config.Libs.springAop)
  compileOnly(Config.Libs.springSecurityWeb)
  compileOnly(Config.Libs.aspectj)
  compileOnly(libs.context.propagation)
  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)
  compileOnly(libs.otel)
  compileOnly(libs.servlet.jakarta.api)
  compileOnly(libs.slf4j.api)
  compileOnly(libs.springboot4.starter.graphql)
  compileOnly(libs.springboot4.starter.quartz)
  compileOnly(libs.spring.kafka4)

  compileOnly(Config.Libs.springWebflux)
  compileOnly(projects.ichnoisGraphql)
  compileOnly(projects.ichnoisGraphql22)
  compileOnly(projects.ichnoisKafka)
  compileOnly(projects.ichnoisQuartz)
  compileOnly(projects.ichnoisOpentelemetry.ichnoisOpentelemetryAgentcustomization)
  compileOnly(projects.ichnoisOpentelemetry.ichnoisOpentelemetryBootstrap)
  api(projects.ichnoisReactor)

  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
  // tests
  testImplementation(projects.ichnoisTestSupport)
  testImplementation(projects.ichnoisGraphql)
  testImplementation(projects.ichnoisKafka)
  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(libs.awaitility.kotlin.spring7)
  testImplementation(libs.context.propagation)
  testImplementation(libs.graphql.java24)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.mockito.core)
  testImplementation(libs.springboot4.starter.aspectj)
  testImplementation(libs.springboot4.starter.graphql)
  testImplementation(libs.spring.kafka4)
  testImplementation(libs.springboot4.starter.security)
  testImplementation(libs.springboot4.starter.test)
  testImplementation(libs.springboot4.starter.web)
  testImplementation(libs.springboot4.starter.webflux)
  testImplementation(libs.springboot4.starter.restclient)
  testImplementation(libs.springboot4.starter.webclient)
  testImplementation(libs.springboot4.resttestclient)
  testImplementation(projects.ichnoisReactor)
}

buildConfig {
  useJavaOutput()
  packageName("io.ichnois.spring7")
  buildConfigField(
    "String",
    "ICHNOIS_SPRING_7_SDK_NAME",
    "\"${Config.IchnoisCore.ICHNOIS_SPRING_7_SDK_NAME}\"",
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
      "IchnoisCore-SDK-Name" to Config.IchnoisCore.ICHNOIS_SPRING_7_SDK_NAME,
      "IchnoisCore-SDK-Package-Name" to "maven:io.github.ichnois:ichnois-spring-7",
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
