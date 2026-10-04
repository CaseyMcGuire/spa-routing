package io.github.caseymcguire.sparouting.runtime.rules

sealed interface RouteRuleResult {
  data object Skip : RouteRuleResult
  data object Allow : RouteRuleResult
  data class Deny(val action: RouteRuleAction) : RouteRuleResult
}
