package io.ichnois.opentelemetry;

import io.ichnois.NoOpLogger;
import io.ichnois.IchnoisCoreLevel;
import io.ichnois.IchnoisCoreOpenTelemetryMode;
import io.ichnois.IchnoisCoreOptions;
import io.ichnois.util.LoadClass;
import io.ichnois.util.Platform;
import io.ichnois.util.SpanUtils;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class OpenTelemetryUtil {

  @ApiStatus.Internal
  public static void applyIgnoredSpanOrigins(final @NotNull IchnoisCoreOptions options) {
    if (Platform.isJvm()) {
      final @NotNull List<String> ignored = ignoredSpanOrigins(options);
      for (String origin : ignored) {
        options.addIgnoredSpanOrigin(origin);
      }
    }
  }

  @ApiStatus.Internal
  public static void updateOpenTelemetryModeIfAuto(
      final @NotNull IchnoisCoreOptions options, final @NotNull LoadClass loadClass) {
    if (!Platform.isJvm()) {
      return;
    }

    final @NotNull IchnoisCoreOpenTelemetryMode openTelemetryMode = options.getOpenTelemetryMode();
    if (IchnoisCoreOpenTelemetryMode.AUTO.equals(openTelemetryMode)) {
      if (loadClass.isClassAvailable(
          "io.ichnois.opentelemetry.agent.AgentMarker", NoOpLogger.getInstance())) {
        options
            .getLogger()
            .log(IchnoisCoreLevel.DEBUG, "openTelemetryMode has been inferred from AUTO to AGENT");
        options.setOpenTelemetryMode(IchnoisCoreOpenTelemetryMode.AGENT);
        return;
      }
      if (loadClass.isClassAvailable(
          "io.ichnois.opentelemetry.agent.AgentlessMarker", NoOpLogger.getInstance())) {
        options
            .getLogger()
            .log(IchnoisCoreLevel.DEBUG, "openTelemetryMode has been inferred from AUTO to AGENTLESS");
        options.setOpenTelemetryMode(IchnoisCoreOpenTelemetryMode.AGENTLESS);
        return;
      }
      if (loadClass.isClassAvailable(
          "io.ichnois.opentelemetry.agent.AgentlessSpringMarker", NoOpLogger.getInstance())) {
        options
            .getLogger()
            .log(
                IchnoisCoreLevel.DEBUG,
                "openTelemetryMode has been inferred from AUTO to AGENTLESS_SPRING");
        options.setOpenTelemetryMode(IchnoisCoreOpenTelemetryMode.AGENTLESS_SPRING);
        return;
      }
    }
  }

  private static @NotNull List<String> ignoredSpanOrigins(final @NotNull IchnoisCoreOptions options) {
    final @NotNull IchnoisCoreOpenTelemetryMode openTelemetryMode = options.getOpenTelemetryMode();

    if (IchnoisCoreOpenTelemetryMode.OFF.equals(openTelemetryMode)) {
      return Collections.emptyList();
    }

    return SpanUtils.ignoredSpanOriginsForOpenTelemetry(openTelemetryMode);
  }
}
