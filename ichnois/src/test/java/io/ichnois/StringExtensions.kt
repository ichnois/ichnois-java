package io.ichnois

import java.io.ByteArrayInputStream

fun String.toInputStream(): ByteArrayInputStream =
  ByteArrayInputStream(this.toByteArray(Charsets.UTF_8))
