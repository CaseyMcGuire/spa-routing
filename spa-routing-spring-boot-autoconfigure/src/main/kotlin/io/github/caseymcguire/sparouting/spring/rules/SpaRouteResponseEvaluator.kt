package io.github.caseymcguire.sparouting.spring.rules

import com.sparouting.contract.RouteDecision
import io.github.caseymcguire.sparouting.spring.access.RouteHandlerRegistry
import io.github.caseymcguire.sparouting.spring.request.RouteRequest
import io.github.caseymcguire.sparouting.spring.response.SpaRouteHttpResponse

/**
 * Evaluates the application gate, followed by the registered route access handler:
 *
 * 1. Application rules are a gate, deny-by-default: the first Allow passes the request on to the
 *    handler, the first Deny denies it, and if every rule skips the request is denied with 404.
 * 2. A registered access handler returns Allow to serve the route or Redirect to send the user
 *    to another route. Routes without an access handler are served once the application gate passes.
 */
open class SpaRouteResponseEvaluator @JvmOverloads constructor(
  private val actionResolver: SpaRouteRuleActionResolver,
  private val handlerRegistry: RouteHandlerRegistry? = null
) {
  open fun evaluate(
    applicationRules: List<SpaRouteRule>,
    request: RouteRequest
  ): SpaRouteHttpResponse {
    val gate = firstDecision(applicationRules, request)
      ?: return SpaRouteHttpResponse.notFound()
    if (gate is SpaRouteRuleResult.Deny) {
      return actionResolver.resolve(gate.action)
    }

    return when (val access = handlerRegistry?.evaluate(request) ?: RouteDecision.Allow) {
      RouteDecision.Allow -> SpaRouteHttpResponse.ok()
      is RouteDecision.Redirect -> actionResolver.resolve(SpaRouteRuleAction.redirectTo(access.destination))
    }
  }

  private fun firstDecision(
    rules: List<SpaRouteRule>,
    request: RouteRequest
  ): SpaRouteRuleResult? {
    for (rule in rules) {
      val result = rule.evaluate(request)
      if (result != SpaRouteRuleResult.Skip) {
        return result
      }
    }
    return null
  }
}
