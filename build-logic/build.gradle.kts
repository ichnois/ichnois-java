plugins {
  `kotlin-dsl`
}

dependencies {
  implementation(libs.animalsniffer.gradle.plugin)
  implementation(libs.spotlessLib)
  compileOnly(libs.kotlin.gradle.plugin)
  compileOnly(libs.android.gradle.plugin)
}

gradlePlugin {
  plugins {
    register("ichnoisJdkTarget") {
      id = "io.ichnois.jdk-target"
      implementationClass = "io.ichnois.gradle.JdkTargetConvention"
    }
    register("ichnoisAnimalSniffer") {
      id = "io.ichnois.animalsniffer"
      implementationClass = "io.ichnois.gradle.IchnoisCoreAnimalSnifferPlugin"
    }
    register("ichnoisAnimalSnifferAndroid") {
      id = "io.ichnois.animalsniffer.android"
      implementationClass = "io.ichnois.gradle.IchnoisCoreAnimalSnifferAndroidPlugin"
    }
  }
}
