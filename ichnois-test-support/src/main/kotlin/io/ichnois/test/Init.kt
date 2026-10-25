package io.ichnois.test

import io.ichnois.IchnoisCore
import io.ichnois.IchnoisCore.OptionsConfiguration
import io.ichnois.IchnoisCoreOptions

fun initForTest(optionsConfiguration: OptionsConfiguration<IchnoisCoreOptions>) {
  IchnoisCore.init {
    applyTestOptions(it)
    optionsConfiguration.configure(it)
  }
}

fun initForTest(optionsConfiguration: OptionsConfiguration<IchnoisCoreOptions>, globalHubMode: Boolean) {
  IchnoisCore.init(
    {
      applyTestOptions(it)
      optionsConfiguration.configure(it)
    },
    globalHubMode,
  )
}

fun initForTest(dsn: String) {
  IchnoisCore.init {
    applyTestOptions(it)
    it.dsn = dsn
  }
}

fun initForTest(options: IchnoisCoreOptions) {
  applyTestOptions(options)
  IchnoisCore.init(options)
}

fun initForTest() {
  IchnoisCore.init()
}

fun applyTestOptions(options: IchnoisCoreOptions) {
  options.shutdownTimeoutMillis = 0
  options.sessionFlushTimeoutMillis = 0
}
