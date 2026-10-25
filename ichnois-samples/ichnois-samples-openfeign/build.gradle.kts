plugins {
  java
  application
  alias(libs.plugins.gradle.versions)
}

application { mainClass.set("io.ichnois.samples.openfeign.Main") }

configure<JavaPluginExtension> {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
  implementation(projects.ichnois)
  implementation(projects.ichnoisOpenfeign)
  implementation(libs.feign.core)
  implementation(libs.feign.gson)
}
