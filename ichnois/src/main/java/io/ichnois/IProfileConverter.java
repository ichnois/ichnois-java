package io.ichnois;

import io.ichnois.protocol.profiling.IchnoisCoreProfile;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for converting JFR (Java Flight Recorder) files to IchnoisCore profiles. This abstraction
 * allows different profiling implementations to be used without direct dependencies between
 * modules.
 */
@ApiStatus.Internal
public interface IProfileConverter {

  /**
   * Converts a JFR file to a IchnoisCoreProfile.
   *
   * @param jfrFilePath The path to the JFR file to convert
   * @return The converted IchnoisCoreProfile
   * @throws IOException If an error occurs while reading or converting the file
   */
  @NotNull
  IchnoisCoreProfile convertFromFile(@NotNull String jfrFilePath) throws IOException;
}
