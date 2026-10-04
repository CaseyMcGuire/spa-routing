package io.github.caseymcguire.sparouting.spring.rules

import com.sparouting.contract.RouteDecision
import io.github.caseymcguire.sparouting.spring.access.RouteHandlerRegistry
import io.github.caseymcguire.sparouting.spring.request.RouteRequest
import io.github.caseymcguire.sparouting.spring.response.SpaRouteHttpResponse

/**
 * Evaluates the application gate, generated route access handler, and route rules in that order:
 *
 * 1. Application rules are a gate, deny-by-default: the first Allow passes the request on to the
 *    route rules, the first Deny denies it, and if every rule skips the request is denied with 404.
 * 2. A registered access handler can redirect. Allow continues to the route rules.
 * 3. Route rules are vetoes, allow-by-default: the first Deny denies the request, the first Allow
 *    serves it, and if every rule skips the route is served.
 *
 * Within a chain the results mean the same thing — Allow and Deny end the chain, Skip defers to the
 * next rule — only the fallthrough differs: an application must explicitly allow its routes, while
 * route-level rules only exist to deny specific cases.
 */
open class SpaRouteResponseEvaluator @JvmOverloads constructor(
  private val actionResolver: SpaRouteRuleActionResolver,
  private val handlerRegistry: RouteHandlerRegistry? = null
) {
  open fun evaluate(
    applicationRules: List<SpaRouteRule>,
    routeRules: List<SpaRouteRule>,
    request: RouteRequest
  ): SpaRouteHttpResponse {
    val gate = firstDecision(applicationRules, request)
      ?: return SpaRouteHttpResponse.notFound()
    if (gate is SpaRouteRuleResult.Deny) {
      return actionResolver.resolve(gate.action)
    }

    val access = handlerRegistry?.evaluate(request)
    if (access is RouteDecision.Redirect) {
      return actionResolver.resolve(SpaRouteRuleAction.redirectTo(access.destination))
    }

    return when (val veto = firstDecision(routeRules, request)) {
      is SpaRouteRuleResult.Deny -> actionResolver.resolve(veto.action)
      else -> SpaRouteHttpResponse.ok()
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
