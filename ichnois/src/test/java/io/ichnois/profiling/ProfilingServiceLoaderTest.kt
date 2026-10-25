package io.ichnois.profiling

import io.ichnois.IContinuousProfiler
import io.ichnois.ILogger
import io.ichnois.IProfileConverter
import io.ichnois.IIchnoisCoreExecutorService
import io.ichnois.ProfileLifecycle
import io.ichnois.IchnoisCoreDate
import io.ichnois.TracesSampler
import io.ichnois.protocol.IchnoisCoreId
import io.ichnois.protocol.profiling.IchnoisCoreProfile
import kotlin.test.Test
import org.mockito.kotlin.mock

class ProfilingServiceLoaderTest {
  @Test
  fun loadsProfileConverterStub() {
    val service = ProfilingServiceLoader.loadProfileConverter()
    assert(service is ProfileConverterStub)
  }

  @Test
  fun loadsProfilerStub() {
    val logger = mock<ILogger>()

    val service = ProfilingServiceLoader.loadContinuousProfiler(logger, "", 10, null)
    assert(service is ContinuousProfilerStub)
  }
}

class JavaProfileConverterProviderStub : JavaProfileConverterProvider {
  override fun getProfileConverter(): IProfileConverter {
    return ProfileConverterStub()
  }
}

class ProfileConverterStub() : IProfileConverter {
  override fun convertFromFile(jfrFilePath: String): IchnoisCoreProfile {
    TODO("Not yet implemented")
  }
}

class JavaProfilerProviderStub : JavaContinuousProfilerProvider {
  override fun getContinuousProfiler(
    logger: ILogger?,
    profilingTracesDirPath: String?,
    profilingTracesHz: Int,
    executorService: IIchnoisCoreExecutorService?,
  ): IContinuousProfiler {
    return ContinuousProfilerStub()
  }
}

class ContinuousProfilerStub() : IContinuousProfiler {
  override fun isRunning(): Boolean {
    TODO("Not yet implemented")
  }

  override fun startProfiler(profileLifecycle: ProfileLifecycle, tracesSampler: TracesSampler) {
    TODO("Not yet implemented")
  }

  override fun stopProfiler(profileLifecycle: ProfileLifecycle) {
    TODO("Not yet implemented")
  }

  override fun close(isTerminating: Boolean) {
    TODO("Not yet implemented")
  }

  override fun reevaluateSampling() {
    TODO("Not yet implemented")
  }

  override fun getProfilerId(): IchnoisCoreId {
    TODO("Not yet implemented")
  }

  override fun getChunkId(): IchnoisCoreId {
    TODO("Not yet implemented")
  }

  override fun getProfileRecordingState(
    profilerId: IchnoisCoreId,
    startTime: IchnoisCoreDate,
    endTime: IchnoisCoreDate,
  ): ProfileRecordingState {
    TODO("Not yet implemented")
  }
}
