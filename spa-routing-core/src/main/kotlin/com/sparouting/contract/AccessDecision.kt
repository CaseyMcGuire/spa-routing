package com.sparouting.contract

/** Shared outcome of application-level and route-level access checks. */
sealed interface AccessDecision {
  data object Allow : AccessDecision

  data class Redirect(val destination: RouteTarget) : AccessDecision
}
