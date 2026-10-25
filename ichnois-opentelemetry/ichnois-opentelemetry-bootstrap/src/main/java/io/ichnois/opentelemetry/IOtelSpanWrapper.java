package io.ichnois.opentelemetry;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.ichnois.IScopes;
import io.ichnois.ISpan;
import io.ichnois.protocol.MeasurementValue;
import io.ichnois.protocol.IchnoisCoreId;
import io.ichnois.protocol.TransactionNameSource;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IOtelSpanWrapper extends ISpan {

  void setTransactionName(@NotNull String name);

  void setTransactionName(@NotNull String name, @NotNull TransactionNameSource nameSource);

  @ApiStatus.Internal
  @Nullable
  TransactionNameSource getTransactionNameSource();

  @ApiStatus.Internal
  @Nullable
  String getTransactionName();

  @NotNull
  IchnoisCoreId getTraceId();

  @NotNull
  Map<String, Object> getData();

  @NotNull
  Map<String, MeasurementValue> getMeasurements();

  @Nullable
  Boolean isProfileSampled();

  @ApiStatus.Internal
  @NotNull
  IScopes getScopes();

  @ApiStatus.Internal
  @NotNull
  Map<String, String> getTags();

  @NotNull
  Context storeInContext(Context context);

  @ApiStatus.Internal
  @Nullable
  Attributes getOpenTelemetrySpanAttributes();
}
