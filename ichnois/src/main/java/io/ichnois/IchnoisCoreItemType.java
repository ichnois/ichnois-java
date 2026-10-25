package io.ichnois;

import io.ichnois.clientreport.ClientReport;
import io.ichnois.protocol.IchnoisCoreTransaction;
import java.io.IOException;
import java.util.Locale;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public enum IchnoisCoreItemType implements JsonSerializable {
  Session("session"),
  Event("event"), // DataCategory.Error
  UserFeedback("user_report"), // IchnoisCore backend still uses user_report
  Attachment("attachment"),
  Transaction("transaction"),
  Profile("profile"),
  ProfileChunk("profile_chunk"),
  ClientReport("client_report"),
  ReplayEvent("replay_event"),
  ReplayRecording("replay_recording"),
  ReplayVideo("replay_video"),
  CheckIn("check_in"),
  Feedback("feedback"),
  Log("log"),
  TraceMetric("trace_metric"),
  Span("span"),
  Unknown("__unknown__"); // DataCategory.Unknown

  private final String itemType;

  public static IchnoisCoreItemType resolve(Object item) {
    if (item instanceof IchnoisCoreEvent) {
      return ((IchnoisCoreEvent) item).getContexts().getFeedback() == null ? Event : Feedback;
    } else if (item instanceof IchnoisCoreTransaction) {
      return Transaction;
    } else if (item instanceof Session) {
      return Session;
    } else if (item instanceof ClientReport) {
      return ClientReport;
    } else {
      return Attachment;
    }
  }

  IchnoisCoreItemType(final String itemType) {
    this.itemType = itemType;
  }

  public String getItemType() {
    return itemType;
  }

  public static @NotNull IchnoisCoreItemType valueOfLabel(String itemType) {
    for (IchnoisCoreItemType ichnoisItemType : values()) {
      if (ichnoisItemType.itemType.equals(itemType)) {
        return ichnoisItemType;
      }
    }
    return Unknown;
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.value(itemType);
  }

  public static final class Deserializer implements JsonDeserializer<IchnoisCoreItemType> {

    @Override
    public @NotNull IchnoisCoreItemType deserialize(
        @NotNull ObjectReader reader, @NotNull ILogger logger) throws Exception {
      return IchnoisCoreItemType.valueOfLabel(reader.nextString().toLowerCase(Locale.ROOT));
    }
  }
}
