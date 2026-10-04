package io.github.caseymcguire.sparouting.spring.response

import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.spring.request.RouteRequest
import io.github.caseymcguire.sparouting.spring.rules.RouteResponseEvaluator

open class RouteResponseService @JvmOverloads constructor(
  private val routeRegistry: SinglePageApplicationRouteRegistry,
  private val evaluator: RouteResponseEvaluator,
  private val invalidPathParameterStatus: Int = 400,
  private val invalidQueryStringStatus: Int = 400
) {
  open fun evaluate(request: RouteResponseRequest): RouteHttpResponse {
    val match = routeRegistry.findByApplicationAndRouteId(
      applicationId = request.applicationId,
      routeId = request.routeId
    ) ?: return RouteHttpResponse.notFound()

    if (!match.route.hasValidParameterValues(request.parameters)) {
      return RouteHttpResponse(invalidPathParameterStatus)
    }

    if (!match.route.hasValidQueryStringValues(request.queryString)) {
      return RouteHttpResponse(invalidQueryStringStatus)
    }

    val routeRequest = RouteRequest(
      applicationId = match.application.applicationId,
      routeId = match.route.id,
      method = "GET",
      path = match.route.resolvePath(match.application.getFullPathPattern(match.route), request.parameters),
      pathParameters = request.parameters,
      queryString = request.queryString,
      headers = request.headers
    )

    return evaluator.evaluate(
      applicationRules = match.application.rules,
      request = routeRequest
    )
  }
}
