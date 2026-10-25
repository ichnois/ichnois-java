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
  alias(libs.plugins.springboot3) apply false
}

// Published code targets Java 17. Test code is not published and follows the toolchain.
tasks.named<JavaCompile>("compileJava") { options.release.set(17) }

kotlin {
  compilerOptions.languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
  compilerOptions.apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
  compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
}

tasks.named<KotlinCompile>("compileKotlin") {
  compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
}

dependencies {
  api(projects.ichnois)
  compileOnly(projects.ichnoisKafka)
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
  compileOnly(libs.springboot3.starter.graphql)
  compileOnly(libs.spring.kafka3)
  compileOnly(libs.springboot3.starter.quartz)

  compileOnly(Config.Libs.springWebflux)
  compileOnly(projects.ichnoisGraphql)
  compileOnly(projects.ichnoisGraphql22)
  compileOnly(projects.ichnoisQuartz)
  compileOnly(projects.ichnoisOpentelemetry.ichnoisOpentelemetryAgentcustomization)
  compileOnly(projects.ichnoisOpentelemetry.ichnoisOpentelemetryBootstrap)
  api(projects.ichnoisReactor)

  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  // tests
  testImplementation(projects.ichnoisTestSupport)
  testImplementation(projects.ichnoisGraphql)
  testImplementation(projects.ichnoisKafka)
  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(libs.awaitility.kotlin)
  testImplementation(libs.context.propagation)
  testImplementation(libs.graphql.java24)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.mockito.core)
  testImplementation(libs.springboot3.starter.aop)
  testImplementation(libs.springboot3.starter.graphql)
  testImplementation(libs.springboot3.starter.security)
  testImplementation(libs.spring.kafka3)
  testImplementation(libs.springboot3.starter.test)
  testImplementation(libs.springboot3.starter.web)
  testImplementation(libs.springboot3.starter.webflux)
  testImplementation(projects.ichnoisReactor)
}

buildConfig {
  useJavaOutput()
  packageName("io.ichnois.spring.jakarta")
  buildConfigField(
    "String",
    "ICHNOIS_SPRING_JAKARTA_SDK_NAME",
    "\"${Config.IchnoisCore.ICHNOIS_SPRING_JAKARTA_SDK_NAME}\"",
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
      "IchnoisCore-SDK-Name" to Config.IchnoisCore.ICHNOIS_SPRING_JAKARTA_SDK_NAME,
      "IchnoisCore-SDK-Package-Name" to "maven:io.ichnois:ichnois-spring-jakarta",
      "Implementation-Vendor" to "IchnoisCore",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}
