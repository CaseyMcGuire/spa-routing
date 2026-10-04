package com.sparouting.contract

/** Framework-neutral request metadata available alongside a handler's typed route values. */
data class RouteAccessContext(
  val method: String,
  val path: String,
  val pathParameters: Map<String, String> = emptyMap(),
  /** All decoded query-string values, including undeclared fields. */
  val queryString: Map<String, List<String>> = emptyMap(),
  val headers: Map<String, List<String>> = emptyMap()
) {
  /** Returns request header values using a case-insensitive name. */
  fun header(name: String): List<String> {
    return headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value.orEmpty()
  }
}
