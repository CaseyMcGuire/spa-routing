package io.github.caseymcguire.sparouting.runtime.rules

import com.sparouting.contract.RouteDecision
import io.github.caseymcguire.sparouting.runtime.access.RouteHandlerRegistry
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest
import io.github.caseymcguire.sparouting.runtime.response.RouteHttpResponse

/**
 * Evaluates the application gate, followed by the registered route access handler:
 *
 * 1. Application rules are a gate, deny-by-default: the first Allow passes the request on to the
 *    handler, the first Deny denies it, and if every rule skips the request is denied with 404.
 * 2. A registered access handler returns Allow to serve the route or Redirect to send the user
 *    to another route. Routes without an access handler are served once the application gate passes.
 *
 * Input must already be validated. Adapters should use
 * [io.github.caseymcguire.sparouting.runtime.response.RouteResponseService] as their entry point.
 */
open class RouteResponseEvaluator @JvmOverloads constructor(
  private val actionResolver: RouteRuleActionResolver,
  private val handlerRegistry: RouteHandlerRegistry? = null
) {
  open fun evaluate(
    applicationRules: List<RouteRule>,
    request: RouteRequest
  ): RouteHttpResponse {
    val gate = firstDecision(applicationRules, request)
      ?: return RouteHttpResponse.notFound()
    if (gate is RouteRuleResult.Deny) {
      return actionResolver.resolve(gate.action)
    }

    return when (val access = handlerRegistry?.evaluate(request) ?: RouteDecision.Allow) {
      RouteDecision.Allow -> RouteHttpResponse.ok()
      is RouteDecision.Redirect -> actionResolver.resolve(RouteRuleAction.redirectTo(access.destination))
    }
  }

  private fun firstDecision(
    rules: List<RouteRule>,
    request: RouteRequest
  ): RouteRuleResult? {
    for (rule in rules) {
      val result = rule.evaluate(request)
      if (result != RouteRuleResult.Skip) {
        return result
      }
    }
    return null
  }
}
