import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.springboot3)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
}

group = "io.ichnois.sample.spring-boot-webflux-jakarta"

version = "0.0.1-SNAPSHOT"

dependencyManagement {
  imports {
    mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springboot3.get()}")
  }
}

// Apollo 4.x requires coroutines 1.9.0+, override Spring Boot's managed version
extra["kotlin-coroutines.version"] = "1.9.0"

dependencies {
  implementation(Config.Libs.kotlinReflect)
  implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
  implementation(projects.ichnoisSpringBootStarterJakarta)
  implementation(projects.ichnoisLogback)
  implementation(projects.ichnoisJdbc)
  implementation(projects.ichnoisGraphql22)
  implementation(projects.ichnoisAsyncProfiler)
  implementation(libs.context.propagation)
  implementation(libs.springboot3.starter.actuator)
  implementation(libs.springboot3.starter.graphql)
  implementation(libs.springboot3.starter.webflux)

  // cache tracing
  implementation(libs.springboot3.starter.cache)
  implementation(libs.caffeine)
}

tasks.withType<KotlinCompile>().configureEach {
  kotlin {
    compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
  }
}
