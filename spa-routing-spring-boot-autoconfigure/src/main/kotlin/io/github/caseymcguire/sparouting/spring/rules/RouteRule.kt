package io.github.caseymcguire.sparouting.spring.rules

import io.github.caseymcguire.sparouting.spring.request.RouteRequest

interface RouteRule {
  fun evaluate(request: RouteRequest): RouteRuleResult
}
