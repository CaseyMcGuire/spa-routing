package io.github.caseymcguire.sparouting.spring.rules.builtin

import io.github.caseymcguire.sparouting.spring.request.SpaRouteRequest
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRule
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleResult

/**
 * spa-routing rule that allows every route. Application rule chains are deny-by-default, so use
 * this as the sole rule of an ungated SPA, or at the end of a chain to pass everything the gates
 * ahead of it did not deny. Passing the application gate does not bypass route-level rules — those
 * still run and can veto the route.
 */
class AllowAll : SpaRouteRule {
  override fun evaluate(request: SpaRouteRequest): SpaRouteRuleResult {
    return SpaRouteRuleResult.Allow
  }
}
