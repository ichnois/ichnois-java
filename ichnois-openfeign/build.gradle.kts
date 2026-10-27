import net.ltgt.gradle.errorprone.errorprone
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
  compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
}

dependencies {
  api(projects.ichnois)

  compileOnly(libs.feign.core)
  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)
  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  // tests
  testImplementation(projects.ichnoisTestSupport)
  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(libs.awaitility.kotlin)
  testImplementation(libs.feign.core)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.google.truth)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.okhttp.mockwebserver)
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
      "IchnoisCore-SDK-Name" to Config.IchnoisCore.ICHNOIS_OPENFEIGN_SDK_NAME,
      "IchnoisCore-SDK-Package-Name" to "maven:io.github.ichnois:ichnois-openfeign",
      "Implementation-Vendor" to "IchnoisCore",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}
