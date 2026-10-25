import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  java
  application
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.shadow)
  id("io.ichnois.systemtest")
}

application { mainClass.set("io.ichnois.samples.console.Main") }

tasks.withType<KotlinCompile>().configureEach {
  kotlin {
    compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
  }
}

dependencies {
  implementation(projects.ichnois)
  implementation(projects.ichnoisAsyncProfiler)
  implementation(projects.ichnoisJcache)
  implementation(projects.ichnoisKafka)
  implementation(libs.jcache)
  implementation(libs.caffeine.jcache)
  implementation(libs.kafka.clients)

  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(projects.ichnois)
  testImplementation(projects.ichnoisSystemTestSupport)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.slf4j.api)
  testImplementation(libs.slf4j.jdk14)
}

// Configure the Shadow JAR (executable JAR with all dependencies)
tasks.shadowJar {
  manifest { attributes["Main-Class"] = "io.ichnois.samples.console.Main" }
  archiveClassifier.set("") // Remove the classifier so it replaces the regular JAR
  mergeServiceFiles()
}

// Make the regular jar task depend on shadowJar
tasks.jar {
  enabled = false
  dependsOn(tasks.shadowJar)
}

// Fix the startScripts task dependency
tasks.startScripts { dependsOn(tasks.shadowJar) }

tasks.register<Test>("systemTest").configure {
  group = "verification"
  description = "Runs the System tests"

  val test = project.extensions.getByType<SourceSetContainer>()["test"]
  testClassesDirs = test.output.classesDirs
  classpath = test.runtimeClasspath

  maxParallelForks = 1

  filter { includeTestsMatching("io.ichnois.systemtest*") }
}

tasks.named("test").configure {
  require(this is Test)

  filter { excludeTestsMatching("io.ichnois.systemtest.*") }
}
