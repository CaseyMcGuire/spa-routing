package com.sparouting.contract

/** A route access handler either permits navigation or redirects to a typed route target. */
sealed interface RouteDecision {
  data object Allow : RouteDecision

  data class Redirect(val destination: SpaRouteTarget) : RouteDecision
}
