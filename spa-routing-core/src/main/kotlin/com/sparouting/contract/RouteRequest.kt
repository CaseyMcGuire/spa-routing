package com.sparouting.contract

/** Target route and caller headers shared by page requests and client navigation checks. */
data class RouteRequest @JvmOverloads constructor(
  val applicationId: String,
  val routeId: String,
  val pathParameters: Map<String, String> = emptyMap(),
  /** Decoded query-string values, including undeclared keys and repeated values. */
  val queryString: Map<String, List<String>> = emptyMap(),
  /** Headers from the actual incoming request, including authentication headers. */
  val headers: Map<String, List<String>> = emptyMap()
) {
  fun pathParameter(name: String): String? {
    return pathParameters[name]
  }

  /** Returns the first value for a query-string key, or null when absent. */
  fun queryStringValue(name: String): String? {
    return queryString[name]?.firstOrNull()
  }

  fun header(name: String): List<String> {
    return headers.entries
      .firstOrNull { it.key.equals(name, ignoreCase = true) }
      ?.value
      .orEmpty()
  }
}
