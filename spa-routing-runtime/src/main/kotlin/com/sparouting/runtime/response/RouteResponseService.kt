package com.sparouting.runtime.response

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteTarget
import com.sparouting.runtime.access.RouteAccessEvaluator
import com.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import com.sparouting.contract.RouteRequest

/** Validates requests and converts access decisions into response data for pages and client navigation. */
open class RouteResponseService @JvmOverloads constructor(
  private val routeRegistry: SinglePageApplicationRouteRegistry,
  private val accessEvaluator: RouteAccessEvaluator,
  private val invalidPathParameterStatus: Int = 400,
  private val invalidQueryStringStatus: Int = 400
) {
  /** Validate and evaluate a target route using headers from the page or navigation-check request. */
  open fun evaluate(request: RouteRequest): RouteHttpResponse {
    val match = routeRegistry.findByApplicationAndRouteId(
      applicationId = request.applicationId,
      routeId = request.routeId
    ) ?: return RouteHttpResponse.notFound()

    if (!match.route.hasValidParameterValues(request.pathParameters)) {
      return RouteHttpResponse(invalidPathParameterStatus)
    }

    if (!match.route.hasValidQueryStringValues(request.queryString)) {
      return RouteHttpResponse(invalidQueryStringStatus)
    }

    return when (val decision = accessEvaluator.evaluate(request)) {
      AccessDecision.Allow -> RouteHttpResponse.ok()
      is AccessDecision.Redirect -> resolveRedirect(decision.destination)
    }
  }

  private fun resolveRedirect(target: RouteTarget): RouteHttpResponse {
    val match = routeRegistry.findByApplicationAndRouteId(target.applicationId, target.routeId)
      ?: throw IllegalStateException("Unknown SPA route target: ${target.applicationId}:${target.routeId}")

    require(match.route.hasValidParameterValues(target.parameters)) {
      "Invalid parameters for SPA route target ${target.applicationId}:${target.routeId}"
    }

    val path = match.route.resolvePath(target.parameters)
    val query = match.route.resolveQueryString(target.queryString)
    return RouteHttpResponse.found(if (query.isEmpty()) path else "$path?$query")
  }
}
