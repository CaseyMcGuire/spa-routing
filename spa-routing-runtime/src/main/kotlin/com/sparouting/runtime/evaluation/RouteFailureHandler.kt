package com.sparouting.runtime.evaluation

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteRequest

/** Chooses recovery destinations before HTTP conversion, for both page loads and client navigation. */
interface RouteFailureHandler {
  /** Also handles unknown application IDs, so this handler belongs to the shared runtime. */
  fun unknownRoute(request: RouteRequest): AccessDecision.Denied

  fun invalidRequest(request: RouteRequest): AccessDecision.Denied
}
