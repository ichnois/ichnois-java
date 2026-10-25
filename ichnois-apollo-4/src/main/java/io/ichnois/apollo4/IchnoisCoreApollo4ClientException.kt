package io.ichnois.apollo4

/**
 * Used for holding an Apollo4 client error, for example. An integration that does not throw when
 * API returns 4xx, 5xx or the `errors` field.
 */
class IchnoisCoreApollo4ClientException(message: String?) : Exception(message) {
  companion object {
    private const val serialVersionUID = 4312160066430858144L
  }
}
