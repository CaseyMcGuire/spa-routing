package io.github.caseymcguire.sparouting.runtime.response

data class RouteResponseRequest @JvmOverloads constructor(
  val applicationId: String,
  val routeId: String,
  val parameters: Map<String, String> = emptyMap(),
  val headers: Map<String, List<String>> = emptyMap(),
  /** Decoded query-string values for the target route. */
  val queryString: Map<String, List<String>> = emptyMap()
)
