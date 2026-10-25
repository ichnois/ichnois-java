package io.ichnois;

import java.io.IOException;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/** the IchnoisCoreLevel */
public enum IchnoisCoreLevel implements JsonSerializable {
  DEBUG,
  INFO,
  WARNING,
  ERROR,
  FATAL;

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.value(name().toLowerCase(Locale.ROOT));
  }

  public static final class Deserializer implements JsonDeserializer<IchnoisCoreLevel> {

    @Override
    public @NotNull IchnoisCoreLevel deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      return IchnoisCoreLevel.valueOf(reader.nextString().toUpperCase(Locale.ROOT));
    }
  }
}
