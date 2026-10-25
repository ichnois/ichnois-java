package io.ichnois.android.replay.util

import android.content.Context

internal fun Context.appContext() = this.applicationContext ?: this
