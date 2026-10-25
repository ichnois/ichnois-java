plugins {
  `java-platform`
  `maven-publish`
}

javaPlatform.allowDependencies()

dependencies {
  api(platform(libs.otel.bom))
  api(platform(libs.otel.alpha.bom))
  api(platform(libs.otel.instrumentation.bom))
  api(platform(libs.otel.instrumentation.alpha.bom))

  constraints {
    api(projects.ichnoisOpentelemetry.ichnoisOpentelemetryAgent)
    api(projects.ichnoisOpentelemetry.ichnoisOpentelemetryAgentcustomization)
    api(projects.ichnoisOpentelemetry.ichnoisOpentelemetryAgentless)
    api(projects.ichnoisOpentelemetry.ichnoisOpentelemetryAgentlessSpring)
    api(projects.ichnoisOpentelemetry.ichnoisOpentelemetryBootstrap)
    api(projects.ichnoisOpentelemetry.ichnoisOpentelemetryCore)
    api(projects.ichnoisOpentelemetry.ichnoisOpentelemetryOtlp)
    api(projects.ichnoisOpentelemetry.ichnoisOpentelemetryOtlpSpring)
  }
}
