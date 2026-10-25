package io.ichnois;

import io.ichnois.util.Objects;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SendFireAndForgetEnvelopeSender
    implements SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForgetFactory {

  private final @NotNull SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForgetDirPath
      sendFireAndForgetDirPath;

  public SendFireAndForgetEnvelopeSender(
      final @NotNull SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForgetDirPath
              sendFireAndForgetDirPath) {
    this.sendFireAndForgetDirPath =
        Objects.requireNonNull(sendFireAndForgetDirPath, "SendFireAndForgetDirPath is required");
  }

  @Override
  public @Nullable SendCachedEnvelopeFireAndForgetIntegration.SendFireAndForget create(
      final @NotNull IScopes scopes, final @NotNull IchnoisCoreOptions options) {
    Objects.requireNonNull(scopes, "Scopes are required");
    Objects.requireNonNull(options, "IchnoisCoreOptions is required");

    final String dirPath = sendFireAndForgetDirPath.getDirPath();
    if (dirPath == null || !hasValidPath(dirPath, options.getLogger())) {
      options.getLogger().log(IchnoisCoreLevel.ERROR, "No cache dir path is defined in options.");
      return null;
    }

    final EnvelopeSender envelopeSender =
        new EnvelopeSender(
            scopes,
            options.getSerializer(),
            options.getLogger(),
            options.getFlushTimeoutMillis(),
            options.getMaxQueueSize());

    return processDir(envelopeSender, dirPath, options.getLogger());
  }
}
