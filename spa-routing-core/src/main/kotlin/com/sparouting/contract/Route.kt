package com.sparouting.contract

open class Route(
  val applicationId: String,
  val routeId: String
) {
  protected fun target(
    parameters: Map<String, String> = emptyMap(),
    queryString: Map<String, List<String>> = emptyMap()
  ): RouteTarget {
    return RouteTarget(
      applicationId = applicationId,
      routeId = routeId,
      parameters = parameters,
      queryString = queryString
    )
  }
}
