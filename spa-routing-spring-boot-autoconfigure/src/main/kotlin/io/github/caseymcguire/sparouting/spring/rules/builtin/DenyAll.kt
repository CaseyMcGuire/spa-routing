package io.github.caseymcguire.sparouting.spring.rules.builtin

import io.github.caseymcguire.sparouting.spring.request.SpaRouteRequest
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRule
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleAction
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleResult

/**
 * spa-routing rule that denies every route, answering with 404 unless a different action is given
 * (e.g. a maintenance redirect). Use it as an application-wide kill switch, or as a route-level
 * veto to take specific routes out of service. Rules ahead of it still run, so an [AllowPublic]
 * placed before an application-wide [DenyAll] keeps the listed routes reachable.
 */
class DenyAll(
  private val action: SpaRouteRuleAction = SpaRouteRuleAction.notFound()
) : SpaRouteRule {
  override fun evaluate(request: SpaRouteRequest): SpaRouteRuleResult {
    return SpaRouteRuleResult.Deny(action)
  }
}
