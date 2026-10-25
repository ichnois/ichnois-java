# ichnois-opentelemetry-bom

This BOM aligns IchnoisCore OpenTelemetry modules with the OpenTelemetry artifacts tested by IchnoisCore.

Use this BOM only when you want IchnoisCore to manage OpenTelemetry dependency versions for IchnoisCore's OpenTelemetry integrations. Do not import it for regular IchnoisCore usage unless you also want this OpenTelemetry version alignment.

The BOM intentionally manages stable and `-alpha` OpenTelemetry artifacts, including incubator artifacts used by the OpenTelemetry instrumentation stack. It makes IchnoisCore's tested OpenTelemetry versions authoritative, so verify dependency resolution before importing it if your application already uses newer OpenTelemetry versions.

This BOM is primarily for classpath-based OpenTelemetry integrations such as `ichnois-opentelemetry-agentless`, `ichnois-opentelemetry-agentless-spring`, `ichnois-opentelemetry-otlp`, and `ichnois-opentelemetry-otlp-spring`. It does not change the OpenTelemetry dependencies shaded into the `ichnois-opentelemetry-agent` Java agent JAR.

## Dependency management ordering

Ordering matters when another BOM, such as Spring Boot's dependency management, also manages OpenTelemetry versions.

### Gradle

With Gradle's native dependency management, import the BOM as a platform and omit versions from IchnoisCore OpenTelemetry and OpenTelemetry dependencies:

```kotlin
dependencies {
  implementation(platform("io.ichnois:ichnois-opentelemetry-bom:<ichnois-version>"))

  implementation("io.ichnois:ichnois-opentelemetry-agentless")
  implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
}
```

If another imported platform also manages OpenTelemetry versions, Gradle's normal version conflict resolution applies. Use `enforcedPlatform(...)` only when you need IchnoisCore's tested OpenTelemetry versions to override other platforms.

When using Gradle with the Spring dependency management plugin, the last imported BOM wins. Import this BOM after Spring Boot's dependency management so its OpenTelemetry versions take precedence:

```kotlin
dependencyManagement {
  imports {
    mavenBom("org.springframework.boot:spring-boot-dependencies:<spring-boot-version>")
    mavenBom("io.ichnois:ichnois-opentelemetry-bom:<ichnois-version>")
  }
}
```

If the Spring Boot Gradle plugin imports Spring Boot dependency management implicitly, add the IchnoisCore BOM in your `dependencyManagement` block; explicit imports are applied after the implicit Spring Boot import.

### Maven

Maven uses different precedence rules: when multiple BOMs are imported in the same `<dependencyManagement>` block, the first declaration wins.

When using `spring-boot-starter-parent`, declare `ichnois-opentelemetry-bom` in the child POM's `<dependencyManagement>` block. Dependency management in the child POM takes precedence over the parent:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.ichnois</groupId>
      <artifactId>ichnois-opentelemetry-bom</artifactId>
      <version>${ichnois.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

When importing `spring-boot-dependencies` manually in the same POM, import `ichnois-opentelemetry-bom` first so IchnoisCore's OpenTelemetry versions win:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.ichnois</groupId>
      <artifactId>ichnois-opentelemetry-bom</artifactId>
      <version>${ichnois.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-dependencies</artifactId>
      <version>${spring-boot.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```
