plugins {
  id("com.android.application")
  id("io.sentry.android.gradle")
}

android {
  namespace = "io.ichnois.tests.size"
  compileSdk = libs.versions.compileSdk.get().toInt()
  compileSdkMinor = libs.versions.compileSdkMinor.get().toInt()

  defaultConfig {
    applicationId = "io.ichnois.tests.size"
    minSdk = libs.versions.minSdk.get().toInt()
    targetSdk = libs.versions.targetSdk.get().toInt()
    versionCode = 1
    versionName = project.version.toString()
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      ndk {
        abiFilters.clear()
        abiFilters.add("arm64-v8a")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  androidComponents.beforeVariants {
    it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
  }
}

configurations.configureEach {
  exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
  exclude(group = "androidx.core")
  exclude(group = "androidx.lifecycle")
}

dependencies {
  implementation(projects.ichnoisAndroid)
  implementation(projects.ichnoisAndroidTimber)
  implementation(projects.ichnoisAndroidSqlite)
  implementation(projects.ichnoisOkhttp)
  implementation(projects.ichnoisCompose)
  implementation(projects.ichnoisAndroidFragment)
}

ichnois {
  org.set("ichnois-sdks")
  projectName.set("ichnois-android")
  authToken.set(System.getenv("ICHNOIS_AUTH_TOKEN"))
  includeProguardMapping.set(false)
  tracingInstrumentation.enabled.set(false)
  includeDependenciesReport.set(false)
  telemetry.set(false)
  autoInstallation.enabled.set(false)
  val authTokenPresent =
    providers.environmentVariable("ICHNOIS_AUTH_TOKEN").map { it.isNotBlank() }.getOrElse(false)
  distribution.enabled.set(authTokenPresent)
  sizeAnalysis.enabled.set(authTokenPresent)
}
