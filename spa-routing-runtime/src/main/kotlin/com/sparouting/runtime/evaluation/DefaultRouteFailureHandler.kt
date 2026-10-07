package com.sparouting.runtime.evaluation

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.DenialReason
import com.sparouting.contract.RouteRequest
import com.sparouting.contract.RouteTarget

/** Uses configured typed recovery routes and standard reasons for lookup and validation failures. */
class DefaultRouteFailureHandler(
  private val unknownRouteDestination: RouteTarget,
  private val invalidRequestDestination: RouteTarget
) : RouteFailureHandler {
  override fun unknownRoute(request: RouteRequest): AccessDecision.Denied = AccessDecision.Denied(
    reason = DenialReason(code = "unknown_route", message = "That page does not exist."),
    destination = unknownRouteDestination
  )

  override fun invalidRequest(request: RouteRequest): AccessDecision.Denied = AccessDecision.Denied(
    reason = DenialReason(code = "invalid_request", message = "That address is invalid."),
    destination = invalidRequestDestination
  )
}
