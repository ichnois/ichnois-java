package io.ichnois.opentelemetry.agent;

import io.ichnois.IchnoisCoreIntegrationPackageStorage;
import io.ichnois.opentelemetry.agentless.BuildConfig;

public final class AgentlessMarker {
  static {
    IchnoisCoreIntegrationPackageStorage.getInstance()
        .addPackage("maven:io.github.ichnois:ichnois-opentelemetry-agentless", BuildConfig.VERSION_NAME);
  }
}
