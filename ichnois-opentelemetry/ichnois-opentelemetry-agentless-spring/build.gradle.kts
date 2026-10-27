plugins {
  `java-library`
  id("io.ichnois.javadoc")
  alias(libs.plugins.buildconfig)
  id("io.ichnois.animalsniffer")
}

dependencies {
  api(projects.ichnois)
  implementation(projects.ichnoisOpentelemetry.ichnoisOpentelemetryBootstrap)
  implementation(projects.ichnoisOpentelemetry.ichnoisOpentelemetryAgentcustomization)

  api(libs.otel)
  api(libs.otel.semconv)
  api(libs.otel.semconv.incubating)
  api(libs.otel.extension.autoconfigure)
  api(libs.springboot3.otel)
}

buildConfig {
  useJavaOutput()
  packageName("io.ichnois.opentelemetry.agentless.spring")
  buildConfigField(
    "String",
    "ICHNOIS_OPENTELEMETRY_AGENTLESS_SPRING_SDK_NAME",
    "\"${Config.IchnoisCore.ICHNOIS_OPENTELEMETRY_AGENTLESS_SPRING_SDK_NAME}\"",
  )
  buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
}

tasks.jar {
  manifest {
    attributes(
      "IchnoisCore-Opentelemetry-SDK-Name" to
        Config.IchnoisCore.ICHNOIS_OPENTELEMETRY_AGENTLESS_SPRING_SDK_NAME,
      "IchnoisCore-Version-Name" to project.version,
      "IchnoisCore-SDK-Name" to Config.IchnoisCore.ICHNOIS_OPENTELEMETRY_AGENTLESS_SPRING_SDK_NAME,
      "IchnoisCore-SDK-Package-Name" to "maven:io.github.ichnois:ichnois-opentelemetry-agentless-spring",
      "Implementation-Vendor" to "IchnoisCore",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}
