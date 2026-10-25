import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.run.BootRun

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
  implementation(libs.otel)
  implementation(projects.ichnoisAsyncProfiler)

  // kafka
  implementation(libs.spring.kafka3)
  implementation(projects.ichnoisKafka)

  // cache tracing
  implementation(libs.springboot3.starter.cache)
  implementation(libs.caffeine)

  // database query tracing
  implementation(projects.ichnoisJdbc)
  runtimeOnly(libs.hsqldb)
}

tasks.register<BootRun>("bootRunWithAgent").configure {
  group = "application"

  val mainBootRunTask = tasks.getByName<BootRun>("bootRun")
  mainClass = mainBootRunTask.mainClass
  classpath = mainBootRunTask.classpath

  val versionName = project.properties["versionName"] as String
  val agentJarPath =
    "$rootDir/ichnois-opentelemetry/ichnois-opentelemetry-agent/build/libs/ichnois-opentelemetry-agent-$versionName.jar"

  val dsn =
    System.getenv("ICHNOIS_DSN")
      ?: "https://test-public-key@ichnois-h2jw.onrender.com/5428563"
  val tracesSampleRate = System.getenv("ICHNOIS_TRACES_SAMPLE_RATE") ?: "1"

  environment("ICHNOIS_DSN", dsn)
  environment("ICHNOIS_DEBUG", "true")
  environment("ICHNOIS_PROFILE_SESSION_SAMPLE_RATE", "1.0")
  environment("ICHNOIS_PROFILING_TRACES_DIR_PATH", "tmp/ichnois/profiling-traces")
  environment("ICHNOIS_PROFILE_LIFECYCLE", "TRACE")

  environment("ICHNOIS_TRACES_SAMPLE_RATE", tracesSampleRate)
  environment("OTEL_TRACES_EXPORTER", "none")
  environment("OTEL_METRICS_EXPORTER", "none")
  environment("OTEL_LOGS_EXPORTER", "none")
  environment("ICHNOIS_IN_APP_INCLUDES", "io.ichnois.samples")
  environment("ICHNOIS_ENABLE_PRETTY_SERIALIZATION_OUTPUT", "false")

  jvmArgs = listOf("-Dotel.javaagent.debug=true", "-javaagent:$agentJarPath")
}
