package com.sparouting.contract

/** Whether navigation may proceed after an application-level or route-level access check. */
sealed interface AccessDecision {
  data object Allowed : AccessDecision

  /** Denies the requested navigation and supplies an alternative destination. */
  data class Denied(
    val destination: RouteTarget
  ) : AccessDecision
}
