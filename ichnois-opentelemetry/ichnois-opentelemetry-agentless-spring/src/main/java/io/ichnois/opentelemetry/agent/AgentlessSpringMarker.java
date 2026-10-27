package io.ichnois.opentelemetry.agent;

import io.ichnois.IchnoisCoreIntegrationPackageStorage;
import io.ichnois.opentelemetry.agentless.spring.BuildConfig;

public final class AgentlessSpringMarker {
  static {
    IchnoisCoreIntegrationPackageStorage.getInstance()
        .addPackage(
            "maven:io.github.ichnois:ichnois-opentelemetry-agentless-spring", BuildConfig.VERSION_NAME);
  }
}
