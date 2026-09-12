package com.sparouting.contract

open class SpaTypedRoute(
  override val applicationId: String,
  override val routeId: String
) : SpaRouteKey {
  protected fun target(
    parameters: Map<String, String> = emptyMap(),
    queryParameters: Map<String, List<String>> = emptyMap()
  ): SpaRouteTarget {
    return SpaRouteTarget(
      applicationId = applicationId,
      routeId = routeId,
      parameters = parameters,
      queryParameters = queryParameters
    )
  }
}
