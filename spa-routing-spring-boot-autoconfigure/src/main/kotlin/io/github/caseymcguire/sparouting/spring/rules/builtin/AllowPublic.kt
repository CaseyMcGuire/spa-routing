package io.github.caseymcguire.sparouting.spring.rules.builtin

import com.sparouting.contract.SpaRouteKey
import io.github.caseymcguire.sparouting.spring.request.SpaRouteRequest
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRule
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteRuleResult

/**
 * spa-routing rule that allows the listed routes for everyone. Allow ends the chain it runs in, so
 * placing this ahead of an application-wide gate (e.g. a RequireLogin rule) exempts the public
 * routes while every other route — including ones added later — stays gated by default. Route-level
 * rules still run and can veto even a public route.
 */
class AllowPublic(vararg routes: SpaRouteKey) : SpaRouteRule {
  // SpaRouteKey is an interface without value equality, so key the set on the id pair.
  private val publicRoutes = routes.mapTo(HashSet()) { it.applicationId to it.routeId }

  override fun evaluate(request: SpaRouteRequest): SpaRouteRuleResult {
    return if (request.applicationId to request.routeId in publicRoutes) {
      SpaRouteRuleResult.Allow
    } else {
      SpaRouteRuleResult.Skip
    }
  }
}