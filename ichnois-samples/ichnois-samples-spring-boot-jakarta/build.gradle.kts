import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.springboot3)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
}

group = "io.ichnois.sample.spring-boot-jakarta"

version = "0.0.1-SNAPSHOT"

dependencyManagement {
  imports {
    mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.springboot3.get()}")
  }
}

// Apollo 4.x requires coroutines 1.9.0+, override Spring Boot's managed version
extra["kotlin-coroutines.version"] = "1.9.0"

tasks.withType<KotlinCompile>().configureEach {
  kotlin {
    compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
  }
}

dependencies {
  implementation(libs.springboot3.starter)
  implementation(libs.springboot3.starter.actuator)
  implementation(libs.springboot3.starter.aop)
  implementation(libs.springboot3.starter.graphql)
  implementation(libs.springboot3.starter.jdbc)
  implementation(libs.springboot3.starter.quartz)
  implementation(libs.springboot3.starter.security)
  implementation(libs.springboot3.starter.web)
  implementation(libs.springboot3.starter.webflux)
  implementation(libs.springboot3.starter.websocket)
  implementation(Config.Libs.aspectj)
  implementation(Config.Libs.kotlinReflect)
  implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
  implementation(projects.ichnoisSpringBootStarterJakarta)
  implementation(projects.ichnoisLogback)
  implementation(projects.ichnoisGraphql22)
  implementation(projects.ichnoisQuartz)
  implementation(projects.ichnoisAsyncProfiler)
  implementation(projects.ichnoisOpenfeature)

  // cache tracing
  implementation(libs.springboot3.starter.cache)
  implementation(libs.caffeine)

  // kafka
  implementation(libs.spring.kafka3)
  implementation(projects.ichnoisKafka)

  // OpenFeature SDK
  implementation(libs.openfeature)

  // okhttp client instrumentation
  implementation(projects.ichnoisOkhttp)
  implementation(libs.okhttp)

  // database query tracing
  implementation(projects.ichnoisJdbc)
  runtimeOnly(libs.hsqldb)
}
