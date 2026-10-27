import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `java-library`
  id("io.ichnois.javadoc")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
  id("io.ichnois.animalsniffer.android")
  alias(libs.plugins.buildconfig)
}

tasks.named<KotlinCompile>("compileKotlin") {
  compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
}

dependencies {
  api(projects.ichnois)

  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)
  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)

  // tests
  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.mockito.core)
  testImplementation(projects.ichnoisTestSupport)
}

buildConfig {
  useJavaOutput()
  packageName("io.ichnois.spotlight")
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
      "IchnoisCore-SDK-Name" to Config.IchnoisCore.ICHNOIS_JAVA_SDK_NAME,
      "IchnoisCore-SDK-Package-Name" to "maven:io.github.ichnois:ichnois-spotlight",
      "Implementation-Vendor" to "IchnoisCore",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}
