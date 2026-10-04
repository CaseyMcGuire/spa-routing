package io.github.caseymcguire.sparouting.spring.rules.builtin

import io.github.caseymcguire.sparouting.spring.request.RouteRequest
import io.github.caseymcguire.sparouting.spring.rules.RouteRule
import io.github.caseymcguire.sparouting.spring.rules.RouteRuleAction
import io.github.caseymcguire.sparouting.spring.rules.RouteRuleResult

/**
 * spa-routing rule that denies every route, answering with 404 unless a different action is given
 * (e.g. a maintenance redirect). Use it as an application-wide kill switch. Rules ahead of it still
 * run, so an [AllowPublic] placed before an application-wide [DenyAll] keeps the listed routes reachable.
 */
class DenyAll(
  private val action: RouteRuleAction = RouteRuleAction.notFound()
) : RouteRule {
  override fun evaluate(request: RouteRequest): RouteRuleResult {
    return RouteRuleResult.Deny(action)
  }
}
