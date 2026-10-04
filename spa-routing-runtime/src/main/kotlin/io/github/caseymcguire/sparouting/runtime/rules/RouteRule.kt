package io.github.caseymcguire.sparouting.runtime.rules

import io.github.caseymcguire.sparouting.runtime.request.RouteRequest

interface RouteRule {
  fun evaluate(request: RouteRequest): RouteRuleResult
}
