package io.github.caseymcguire.sparouting.spring.rules.builtin

import io.github.caseymcguire.sparouting.spring.request.RouteRequest
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRule
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleResult

/**
 * spa-routing rule that allows every route. Application rule chains are deny-by-default, so use
 * this as the sole rule of an ungated SPA, or at the end of a chain to pass everything the gates
 * ahead of it did not deny. A registered route access handler still runs after the gate passes.
 */
class AllowAll : SpaRouteRule {
  override fun evaluate(request: RouteRequest): SpaRouteRuleResult {
    return SpaRouteRuleResult.Allow
  }
}
