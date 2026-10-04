package io.github.caseymcguire.sparouting.runtime.response

import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationRouteRegistration
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest
import io.github.caseymcguire.sparouting.runtime.rules.RouteResponseEvaluator

/** Validates and evaluates both real page requests and client-side navigation decisions. */
open class RouteResponseService @JvmOverloads constructor(
  private val routeRegistry: SinglePageApplicationRouteRegistry,
  private val evaluator: RouteResponseEvaluator,
  private val invalidPathParameterStatus: Int = 400,
  private val invalidQueryStringStatus: Int = 400
) {
  /** Evaluate an adapter's page request, preserving its method, path, and request metadata. */
  open fun evaluate(request: RouteRequest): RouteHttpResponse {
    val match = routeRegistry.findByApplicationAndRouteId(
      applicationId = request.applicationId,
      routeId = request.routeId
    ) ?: return RouteHttpResponse.notFound()

    return evaluate(match, request)
  }

  /** Evaluate a client navigation as a GET request to the target route. */
  open fun evaluate(request: RouteResponseRequest): RouteHttpResponse {
    val match = routeRegistry.findByApplicationAndRouteId(
      applicationId = request.applicationId,
      routeId = request.routeId
    ) ?: return RouteHttpResponse.notFound()
    val routeRequest = RouteRequest(
      applicationId = match.application.applicationId,
      routeId = match.route.id,
      method = "GET",
      path = match.route.resolvePath(match.application.getFullPathPattern(match.route), request.parameters),
      pathParameters = request.parameters,
      queryString = request.queryString,
      headers = request.headers
    )

    return evaluate(match, routeRequest)
  }

  private fun evaluate(
    match: SinglePageApplicationRouteRegistration,
    request: RouteRequest
  ): RouteHttpResponse {
    if (!match.route.hasValidParameterValues(request.pathParameters)) {
      return RouteHttpResponse(invalidPathParameterStatus)
    }

    if (!match.route.hasValidQueryStringValues(request.queryString)) {
      return RouteHttpResponse(invalidQueryStringStatus)
    }

    return evaluator.evaluate(
      applicationRules = match.application.rules,
      request = request
    )
  }
}
