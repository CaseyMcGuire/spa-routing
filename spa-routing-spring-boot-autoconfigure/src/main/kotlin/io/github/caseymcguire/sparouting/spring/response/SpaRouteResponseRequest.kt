package io.github.caseymcguire.sparouting.spring.response

data class SpaRouteResponseRequest @JvmOverloads constructor(
  val applicationId: String,
  val routeId: String,
  val parameters: Map<String, String> = emptyMap(),
  val headers: Map<String, List<String>> = emptyMap(),
  val queryParameters: Map<String, List<String>> = emptyMap()
)
