package com.sparouting.contract

data class SpaRouteTarget(
  val applicationId: String,
  val routeId: String,
  val parameters: Map<String, String> = emptyMap(),
  val queryParameters: Map<String, List<String>> = emptyMap()
)
