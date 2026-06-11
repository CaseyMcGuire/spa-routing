package io.github.caseymcguire.sparouting.spring.rules

import io.github.caseymcguire.sparouting.spring.request.SpaRouteRequest
import io.github.caseymcguire.sparouting.spring.response.SpaRouteHttpResponse

open class SpaRouteResponseEvaluator(
  private val actionResolver: SpaRouteRuleActionResolver
) {
  open fun evaluate(
    rules: List<SpaRouteRule>,
    request: SpaRouteRequest
  ): SpaRouteHttpResponse {
    for (rule in rules) {
      when (val result = rule.evaluate(request)) {
        SpaRouteRuleResult.Allow -> return SpaRouteHttpResponse.ok()
        is SpaRouteRuleResult.Deny -> return actionResolver.resolve(result.action)
        SpaRouteRuleResult.Skip -> Unit
      }
    }

    return SpaRouteHttpResponse.ok()
  }
}
