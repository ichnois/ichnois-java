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
  compileOnly(libs.slf4j.api)

  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)
  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  // tests
  testImplementation(projects.ichnoisTestSupport)
  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.logback.classic)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.slf4j.api)
}

tasks {
  test {
    // used to test io.ichnois.jul.IchnoisCoreHandler
    systemProperty(
      "java.util.logging.config.file",
      "${project.projectDir}/src/test/resources/logging.properties",
    )
  }
}

buildConfig {
  useJavaOutput()
  packageName("io.ichnois.jul")
  buildConfigField("String", "ICHNOIS_JUL_SDK_NAME", "\"${Config.IchnoisCore.ICHNOIS_JUL_SDK_NAME}\"")
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
      "IchnoisCore-SDK-Name" to Config.IchnoisCore.ICHNOIS_JUL_SDK_NAME,
      "IchnoisCore-SDK-Package-Name" to "maven:io.github.ichnois:ichnois-jul",
      "Implementation-Vendor" to "IchnoisCore",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}
