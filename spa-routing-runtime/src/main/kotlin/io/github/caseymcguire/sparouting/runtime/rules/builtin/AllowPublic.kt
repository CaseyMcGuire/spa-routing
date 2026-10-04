package io.github.caseymcguire.sparouting.runtime.rules.builtin

import com.sparouting.contract.Route
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest
import io.github.caseymcguire.sparouting.runtime.rules.RouteRule
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleResult

/**
 * spa-routing rule that allows the listed routes for everyone. Allow ends the chain it runs in, so
 * placing this ahead of an application-wide gate (e.g. a RequireLogin rule) exempts the public
 * routes while every other route — including ones added later — stays gated by default. A registered
 * route access handler still runs and can redirect even a public route.
 */
class AllowPublic(vararg routes: Route) : RouteRule {
  // Match incoming requests by application and route IDs.
  private val publicRoutes = routes.mapTo(HashSet()) { it.applicationId to it.routeId }

  override fun evaluate(request: RouteRequest): RouteRuleResult {
    return if (request.applicationId to request.routeId in publicRoutes) {
      RouteRuleResult.Allow
    } else {
      RouteRuleResult.Skip
    }
  }
}
