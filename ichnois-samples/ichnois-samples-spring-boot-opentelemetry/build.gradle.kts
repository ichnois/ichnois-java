import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  java
  application
  alias(libs.plugins.shadow)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
  id("io.ichnois.systemtest")
}

// Spring scans this app's own bytecode with its repackaged ASM at startup, and the oldest version
// the Spring Boot 2.x matrix covers (2.4.13, Spring 5.3.13) fails on class files as new as the
// toolchain's: "Unsupported class file major version 65". Only the app is scanned, so its test
// code keeps following the toolchain.
tasks.named<JavaCompile>("compileJava") { options.release.set(11) }

tasks.named<KotlinCompile>("compileKotlin") {
  compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
}

application { mainClass.set("io.ichnois.samples.spring.boot.IchnoisCoreDemoApplication") }

group = "io.ichnois.sample.spring-boot"

version = "0.0.1-SNAPSHOT"

fun springBoot2SupportsOptionalIntegrations(): Boolean {
  val version = libs.versions.springboot2.get().removeSuffix(".RELEASE")
  val parts = version.split(".").map { it.toIntOrNull() ?: 0 }
  val major = parts.getOrElse(0) { 0 }
  val minor = parts.getOrElse(1) { 0 }
  return major > 2 || (major == 2 && minor >= 7)
}

val includeGraphql =
  !project.hasProperty("excludeGraphql") && springBoot2SupportsOptionalIntegrations()
val includeKafka = !project.hasProperty("excludeKafka") && springBoot2SupportsOptionalIntegrations()

tasks.withType<KotlinCompile>().configureEach {
  kotlin {
    compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
  }
}

dependencies {
  implementation(platform(libs.springboot2.bom))
  implementation(libs.springboot.starter)
  implementation(libs.springboot.starter.actuator)
  implementation(libs.springboot.starter.aop)
  if (includeGraphql) {
    implementation(libs.springboot.starter.graphql)
  }
  implementation(libs.springboot.starter.jdbc)
  implementation(libs.springboot.starter.quartz)
  implementation(libs.springboot.starter.security)
  implementation(libs.springboot.starter.web)
  implementation(libs.springboot.starter.webflux)
  implementation(libs.springboot.starter.websocket)
  implementation(Config.Libs.aspectj)
  implementation(Config.Libs.kotlinReflect)
  implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
  implementation(projects.ichnoisSpringBootStarter)
  implementation(projects.ichnoisLogback)
  if (includeGraphql) {
    implementation(projects.ichnoisGraphql)
  }
  implementation(projects.ichnoisQuartz)
  implementation(projects.ichnoisAsyncProfiler)
  implementation(libs.otel)

  if (includeKafka) {
    implementation(libs.spring.kafka2)
    implementation(projects.ichnoisKafka)
  }

  // database query tracing
  implementation(projects.ichnoisJdbc)
  runtimeOnly(libs.hsqldb)

  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(projects.ichnoisSystemTestSupport)
  testImplementation(libs.apollo3.kotlin)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.slf4j2.api)
  testImplementation(libs.springboot.starter.test) {
    exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
  }
  testImplementation("ch.qos.logback:logback-classic:1.5.16")
  testImplementation("ch.qos.logback:logback-core:1.5.16")
  testImplementation("org.apache.httpcomponents:httpclient")
}

val runtimeClasspath = configurations.named("runtimeClasspath")

// Configure the Shadow JAR (executable JAR with all dependencies)
tasks.shadowJar {
  manifest { attributes["Main-Class"] = "io.ichnois.samples.spring.boot.IchnoisCoreDemoApplication" }
  archiveClassifier.set("")

  doLast(
    MergeSpringMetadataAction(
      runtimeClasspath.get(),
      MergeSpringMetadataAction.DEFAULT_SPRING_METADATA_FILES,
    )
  )
}

tasks.jar {
  enabled = false
  dependsOn(tasks.shadowJar)
}

tasks.startScripts { dependsOn(tasks.shadowJar) }

configure<SourceSetContainer> {
  main {
    if (!includeGraphql) {
      java.exclude("**/graphql/**")
      resources.exclude("graphql/**")
    }
    if (!includeKafka) {
      java.exclude("**/queues/kafka/**")
      resources.exclude("application-kafka.properties")
    }
  }
}

tasks.register<JavaExec>("bootRunWithAgent").configure {
  group = "application"

  mainClass.set("io.ichnois.samples.spring.boot.IchnoisCoreDemoApplication")
  classpath = sourceSets["main"].runtimeClasspath

  val versionName = project.properties["versionName"] as String
  val agentJarPath =
    "$rootDir/ichnois-opentelemetry/ichnois-opentelemetry-agent/build/libs/ichnois-opentelemetry-agent-$versionName.jar"

  val dsn =
    System.getenv("ICHNOIS_DSN")
      ?: "https://test-public-key@ichnois-h2jw.onrender.com/5428563"
  val tracesSampleRate = System.getenv("ICHNOIS_TRACES_SAMPLE_RATE") ?: "1"

  environment("ICHNOIS_DSN", dsn)
  environment("ICHNOIS_TRACES_SAMPLE_RATE", tracesSampleRate)
  environment("OTEL_TRACES_EXPORTER", "none")
  environment("OTEL_METRICS_EXPORTER", "none")
  environment("OTEL_LOGS_EXPORTER", "none")

  jvmArgs = listOf("-Dotel.javaagent.debug=true", "-javaagent:$agentJarPath")
}

// The runner launches this sample with -javaagent, so track the agent jar as a systemTest input.
ichnoisSystemTest { usesOpenTelemetryAgent = true }

tasks.register<Test>("systemTest").configure {
  group = "verification"
  description = "Runs the System tests"

  val test = project.extensions.getByType<SourceSetContainer>()["test"]
  testClassesDirs = test.output.classesDirs
  classpath = test.runtimeClasspath

  maxParallelForks = 1

  filter {
    includeTestsMatching("io.ichnois.systemtest*")
    if (!includeGraphql) {
      excludeTestsMatching("io.ichnois.systemtest.Graphql*")
    }
    if (!includeKafka) {
      excludeTestsMatching("io.ichnois.systemtest.Kafka*")
    }
  }
}

tasks.named("test").configure {
  require(this is Test)

  filter { excludeTestsMatching("io.ichnois.systemtest.*") }
}
