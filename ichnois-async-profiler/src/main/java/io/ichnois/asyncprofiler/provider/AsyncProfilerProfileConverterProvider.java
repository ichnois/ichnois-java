package io.ichnois.asyncprofiler.provider;

import io.ichnois.IProfileConverter;
import io.ichnois.asyncprofiler.convert.JfrAsyncProfilerToIchnoisCoreProfileConverter;
import io.ichnois.profiling.JavaProfileConverterProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * AsyncProfiler implementation of {@link JavaProfileConverterProvider}. This provider integrates
 * AsyncProfiler's JFR converter with IchnoisCore's profiling system.
 */
@ApiStatus.Internal
public final class AsyncProfilerProfileConverterProvider implements JavaProfileConverterProvider {

  @Override
  public @NotNull IProfileConverter getProfileConverter() {
    return new AsyncProfilerProfileConverter();
  }

  /**
   * Internal implementation of IProfileConverter that delegates to
   * JfrAsyncProfilerToIchnoisCoreProfileConverter.
   */
  public static final class AsyncProfilerProfileConverter implements IProfileConverter {

    @Override
    public @NotNull io.ichnois.protocol.profiling.IchnoisCoreProfile convertFromFile(
        @NotNull String jfrFilePath) throws java.io.IOException {
      return JfrAsyncProfilerToIchnoisCoreProfileConverter.convertFromFileStatic(jfrFilePath);
    }
  }
}
