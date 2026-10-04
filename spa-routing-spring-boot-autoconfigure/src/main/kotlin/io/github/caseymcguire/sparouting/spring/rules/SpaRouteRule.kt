package io.github.caseymcguire.sparouting.spring.rules

import io.github.caseymcguire.sparouting.spring.request.RouteRequest

interface SpaRouteRule {
  fun evaluate(request: RouteRequest): SpaRouteRuleResult
}
