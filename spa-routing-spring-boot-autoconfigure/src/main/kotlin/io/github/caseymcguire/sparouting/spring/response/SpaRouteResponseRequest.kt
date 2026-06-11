package io.github.caseymcguire.sparouting.spring.response

data class SpaRouteResponseRequest(
  val applicationId: String,
  val routeId: String,
  val parameters: Map<String, String> = emptyMap(),
  val headers: Map<String, List<String>> = emptyMap()
)
