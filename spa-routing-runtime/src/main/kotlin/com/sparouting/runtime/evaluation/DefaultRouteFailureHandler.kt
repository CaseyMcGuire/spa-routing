package com.sparouting.runtime.evaluation

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteRequest
import com.sparouting.contract.RouteTarget

/** Uses configured typed recovery routes for lookup and validation failures. */
class DefaultRouteFailureHandler(
  private val unknownRouteDestination: RouteTarget,
  private val invalidRequestDestination: RouteTarget
) : RouteFailureHandler {
  override fun unknownRoute(request: RouteRequest): AccessDecision.Denied = AccessDecision.Denied(
    destination = unknownRouteDestination
  )

  override fun invalidRequest(request: RouteRequest): AccessDecision.Denied = AccessDecision.Denied(
    destination = invalidRequestDestination
  )
}
