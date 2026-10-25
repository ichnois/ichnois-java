package io.ichnois;

import io.ichnois.featureflags.IFeatureFlagBuffer;
import io.ichnois.featureflags.NoOpFeatureFlagBuffer;
import io.ichnois.internal.eventprocessor.EventProcessorAndOrder;
import io.ichnois.protocol.Contexts;
import io.ichnois.protocol.FeatureFlags;
import io.ichnois.protocol.Request;
import io.ichnois.protocol.IchnoisCoreId;
import io.ichnois.protocol.User;
import io.ichnois.util.LazyEvaluator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NoOpScope implements IScope {

  private static final NoOpScope instance = new NoOpScope();

  private final @NotNull LazyEvaluator<IchnoisCoreOptions> emptyOptions =
      new LazyEvaluator<>(() -> IchnoisCoreOptions.empty());

  private NoOpScope() {}

  public static NoOpScope getInstance() {
    return instance;
  }

  @Override
  public @Nullable IchnoisCoreLevel getLevel() {
    return null;
  }

  @Override
  public void setLevel(@Nullable IchnoisCoreLevel level) {}

  @Override
  public @Nullable String getTransactionName() {
    return null;
  }

  @Override
  public void setTransaction(@NotNull String transaction) {}

  @Override
  public @Nullable ISpan getSpan() {
    return null;
  }

  @Override
  public void setActiveSpan(final @Nullable ISpan span) {}

  @Override
  public void setTransaction(@Nullable ITransaction transaction) {}

  @Override
  public @Nullable User getUser() {
    return null;
  }

  @Override
  public void setUser(@Nullable User user) {}

  @ApiStatus.Internal
  @Override
  public @Nullable String getScreen() {
    return null;
  }

  @ApiStatus.Internal
  @Override
  public void setScreen(@Nullable String screen) {}

  @Override
  public @NotNull IchnoisCoreId getReplayId() {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public void setReplayId(@Nullable IchnoisCoreId replayId) {}

  @Override
  public @Nullable Request getRequest() {
    return null;
  }

  @Override
  public void setRequest(@Nullable Request request) {}

  @ApiStatus.Internal
  @Override
  public @NotNull List<String> getFingerprint() {
    return new ArrayList<>();
  }

  @Override
  public void setFingerprint(@NotNull List<String> fingerprint) {}

  @ApiStatus.Internal
  @Override
  public @NotNull Queue<Breadcrumb> getBreadcrumbs() {
    return new ArrayDeque<>();
  }

  @Override
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb, @Nullable Hint hint) {}

  @Override
  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb) {}

  @Override
  public void clearBreadcrumbs() {}

  @Override
  public void clearTransaction() {}

  @Override
  public @Nullable ITransaction getTransaction() {
    return null;
  }

  @Override
  public void clear() {}

  @ApiStatus.Internal
  @Override
  public @NotNull Map<String, String> getTags() {
    return new HashMap<>();
  }

  @Override
  public void setTag(@Nullable String key, @Nullable String value) {}

  @Override
  public void removeTag(@Nullable String key) {}

  @ApiStatus.Internal
  @Override
  public @NotNull Map<String, Object> getExtras() {
    return new HashMap<>();
  }

  @Override
  public void setExtra(@Nullable String key, @Nullable String value) {}

  @Override
  public void removeExtra(@Nullable String key) {}

  @Override
  public @NotNull Contexts getContexts() {
    return new Contexts();
  }

  @Override
  public void setContexts(@Nullable String key, @Nullable Object value) {}

  @Override
  public void setContexts(@Nullable String key, @Nullable Boolean value) {}

  @Override
  public void setContexts(@Nullable String key, @Nullable String value) {}

  @Override
  public void setContexts(@Nullable String key, @Nullable Number value) {}

  @Override
  public void setContexts(@Nullable String key, @Nullable Collection<?> value) {}

  @Override
  public void setContexts(@Nullable String key, @Nullable Object[] value) {}

  @Override
  public void setContexts(@Nullable String key, @Nullable Character value) {}

  @Override
  public void removeContexts(@Nullable String key) {}

  @ApiStatus.Internal
  @Override
  public @NotNull List<Attachment> getAttachments() {
    return new ArrayList<>();
  }

  @Override
  public void addAttachment(@NotNull Attachment attachment) {}

  @Override
  public void clearAttachments() {}

  @ApiStatus.Internal
  @Override
  public @NotNull List<EventProcessor> getEventProcessors() {
    return new ArrayList<>();
  }

  @ApiStatus.Internal
  @Override
  public @NotNull List<EventProcessorAndOrder> getEventProcessorsWithOrder() {
    return new ArrayList<>();
  }

  @Override
  public void addEventProcessor(@NotNull EventProcessor eventProcessor) {}

  @ApiStatus.Internal
  @Override
  public @Nullable Session withSession(Scope.@NotNull IWithSession sessionCallback) {
    return null;
  }

  @ApiStatus.Internal
  @Override
  public @Nullable Scope.SessionPair startSession() {
    return null;
  }

  @ApiStatus.Internal
  @Override
  public @Nullable Session endSession() {
    return null;
  }

  @ApiStatus.Internal
  @Override
  public void withTransaction(Scope.@NotNull IWithTransaction callback) {}

  @ApiStatus.Internal
  @Override
  public @NotNull IchnoisCoreOptions getOptions() {
    return emptyOptions.getValue();
  }

  @ApiStatus.Internal
  @Override
  public @Nullable Session getSession() {
    return null;
  }

  @ApiStatus.Internal
  @Override
  public void clearSession() {}

  @ApiStatus.Internal
  @Override
  public void setPropagationContext(@NotNull PropagationContext propagationContext) {}

  @ApiStatus.Internal
  @Override
  public @NotNull PropagationContext getPropagationContext() {
    return new PropagationContext();
  }

  @ApiStatus.Internal
  @Override
  public @NotNull PropagationContext withPropagationContext(
      Scope.@NotNull IWithPropagationContext callback) {
    return new PropagationContext();
  }

  @Override
  public void setLastEventId(@NotNull IchnoisCoreId lastEventId) {}

  /**
   * Clones the Scope
   *
   * @return the cloned Scope
   */
  @Override
  public @NotNull IScope clone() {
    return NoOpScope.getInstance();
  }

  @Override
  public @NotNull IchnoisCoreId getLastEventId() {
    return IchnoisCoreId.EMPTY_ID;
  }

  @Override
  public void bindClient(@NotNull IIchnoisCoreClient client) {}

  @Override
  public @NotNull IIchnoisCoreClient getClient() {
    return NoOpIchnoisCoreClient.getInstance();
  }

  @Override
  public void assignTraceContext(@NotNull IchnoisCoreEvent event) {}

  @Override
  public void setSpanContext(
      @NotNull Throwable throwable, @NotNull ISpan span, @NotNull String transactionName) {}

  @Override
  public void replaceOptions(@NotNull IchnoisCoreOptions options) {}

  @Override
  public void setAttribute(@Nullable String key, @Nullable Object value) {}

  @Override
  public void setAttribute(@Nullable IchnoisCoreAttribute attribute) {}

  @Override
  public void setAttributes(@Nullable IchnoisCoreAttributes attributes) {}

  @Override
  public void removeAttribute(@Nullable String key) {}

  @ApiStatus.Internal
  @Override
  public @NotNull Map<String, IchnoisCoreAttribute> getAttributes() {
    return new HashMap<>();
  }

  @Override
  public void addFeatureFlag(final @Nullable String flag, final @Nullable Boolean result) {}

  @Override
  public void clearFeatureFlags() {}

  @Override
  public @Nullable FeatureFlags getFeatureFlags() {
    return null;
  }

  @Override
  public @NotNull IFeatureFlagBuffer getFeatureFlagBuffer() {
    return NoOpFeatureFlagBuffer.getInstance();
  }
}
