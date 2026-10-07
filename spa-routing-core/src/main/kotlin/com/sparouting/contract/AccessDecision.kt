package com.sparouting.contract

/** Whether navigation may proceed after an application-level or route-level access check. */
sealed interface AccessDecision {
  data object Allowed : AccessDecision

  /** Denies the requested navigation and supplies a reason and alternative destination. */
  data class Denied(
    val reason: DenialReason,
    val destination: RouteTarget
  ) : AccessDecision
}
