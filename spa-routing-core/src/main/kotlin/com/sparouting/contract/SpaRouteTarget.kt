package com.sparouting.contract

data class SpaRouteTarget(
  val applicationId: String,
  val routeId: String,
  val parameters: Map<String, String> = emptyMap(),
  /** Query-string values to encode in the target URL, preserving repeated values. */
  val queryString: Map<String, List<String>> = emptyMap()
)
