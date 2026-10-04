package io.github.caseymcguire.sparouting.spring.request

data class RouteRequest(
  val applicationId: String,
  val routeId: String,
  val method: String,
  val path: String,
  val pathParameters: Map<String, String> = emptyMap(),
  /** Decoded query-string values, including undeclared keys and repeated values. */
  val queryString: Map<String, List<String>> = emptyMap(),
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
