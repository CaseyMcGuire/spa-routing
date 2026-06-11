package io.github.caseymcguire.sparouting.spring.rules

import io.github.caseymcguire.sparouting.spring.request.SpaRouteRequest

interface SpaRouteRule {
  fun evaluate(request: SpaRouteRequest): SpaRouteRuleResult
}
