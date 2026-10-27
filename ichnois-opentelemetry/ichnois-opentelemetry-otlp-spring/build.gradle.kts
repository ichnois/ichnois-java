plugins {
  `java-library`
  id("io.ichnois.animalsniffer")
  id("io.ichnois.javadoc")
}

dependencies {
  api(projects.ichnoisOpentelemetry.ichnoisOpentelemetryOtlp)
  implementation(libs.springboot3.otel)
}

tasks.jar {
  manifest {
    attributes(
      "IchnoisCore-Version-Name" to project.version,
      "IchnoisCore-SDK-Name" to Config.IchnoisCore.ICHNOIS_OPENTELEMETRY_OTLP_SPRING_SDK_NAME,
      "IchnoisCore-SDK-Package-Name" to "maven:io.github.ichnois:ichnois-opentelemetry-otlp-spring",
      "Implementation-Vendor" to "IchnoisCore",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}
