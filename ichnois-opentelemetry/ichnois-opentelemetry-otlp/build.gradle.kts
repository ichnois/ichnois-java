import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `java-library`
  id("io.ichnois.animalsniffer")
  id("io.ichnois.javadoc")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
}

tasks.named<KotlinCompile>("compileKotlin") {
  compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
}

dependencies {
  api(projects.ichnois)

  api(libs.otel)
  api(libs.otel.extension.autoconfigure)
  api(libs.otel.exporter.otlp)
  compileOnly(libs.otel.extension.autoconfigure.spi)
  implementation(libs.otel.semconv)
  //  compileOnly(libs.otel.semconv.incubating)

  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)
  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  // tests
  testImplementation(projects.ichnoisTestSupport)
  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(libs.awaitility.kotlin)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.mockito.kotlin)

  testImplementation(libs.otel)
  //  testImplementation(libs.otel.semconv)
  //  testImplementation(libs.otel.semconv.incubating)
}

tasks.withType<JavaCompile>().configureEach {
  options.errorprone {
    check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
    option("NullAway:AnnotatedPackages", "io.ichnois")
  }
}

tasks.jar {
  manifest {
    attributes(
      "IchnoisCore-Version-Name" to project.version,
      "IchnoisCore-SDK-Name" to Config.IchnoisCore.ICHNOIS_OPENTELEMETRY_OTLP_SDK_NAME,
      "IchnoisCore-SDK-Package-Name" to "maven:io.ichnois:ichnois-opentelemetry-otlp",
      "Implementation-Vendor" to "IchnoisCore",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}
