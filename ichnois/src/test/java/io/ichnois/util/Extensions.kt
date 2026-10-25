package io.ichnois.util

import io.ichnois.IchnoisCoreOptions

fun IchnoisCoreOptions.noFlushTimeout(): IchnoisCoreOptions = this.apply { flushTimeoutMillis = 0L }
