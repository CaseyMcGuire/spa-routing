package com.sparouting.runtime.evaluation

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteAccessContext
import com.sparouting.contract.RouteRequest
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.runtime.config.SinglePageApplicationRouteRegistration
import com.sparouting.runtime.config.SinglePageApplicationRouteRegistry

/**
 * Validates requests, evaluates access, and resolves redirects for pages and client navigation.
 * Builds and validates registrations from [configs] once during construction.
 * HTTP response mapping belongs to the framework adapter.
 */
class RouteRequestEvaluator(
  configs: List<SinglePageApplicationConfig>
) {
  private val routeRegistry = SinglePageApplicationRouteRegistry(configs)

  /** Validate and evaluate a target route using headers from the page or navigation-check request. */
  fun evaluate(request: RouteRequest): RouteResult {
    val match = routeRegistry.findByApplicationAndRouteId(
      applicationId = request.applicationId,
      routeId = request.routeId
    ) ?: return RouteResult.NotFound

    if (!match.route.hasValidParameterValues(request.pathParameters)) {
      return RouteResult.InvalidPathParameters
    }

    if (!match.route.hasValidQueryStringValues(request.queryString)) {
      return RouteResult.InvalidQueryString
    }

    return when (val decision = evaluateAccess(match, request)) {
      AccessDecision.Allow -> RouteResult.Allowed
      is AccessDecision.Redirect -> resolveRedirect(decision.destination)
    }
  }

  private fun evaluateAccess(
    registration: SinglePageApplicationRouteRegistration,
    request: RouteRequest
  ): AccessDecision {
    val applicationDecision = registration.applicationAccessHandler.evaluate(request)
    if (applicationDecision != AccessDecision.Allow) {
      return applicationDecision
    }

    val handler = registration.routeAccessHandler ?: return AccessDecision.Allow
    return handler.evaluateRequest(
      RouteAccessContext(
        method = "GET",
        path = registration.route.resolvePath(request.pathParameters),
        pathParameters = request.pathParameters,
        queryString = request.queryString,
        headers = request.headers
      )
    )
  }

  private fun resolveRedirect(target: RouteTarget): RouteResult.Redirect {
    val match = routeRegistry.findByApplicationAndRouteId(target.applicationId, target.routeId)
      ?: throw IllegalStateException("Unknown SPA route target: ${target.applicationId}:${target.routeId}")

    require(match.route.hasValidParameterValues(target.parameters)) {
      "Invalid parameters for SPA route target ${target.applicationId}:${target.routeId}"
    }

    val path = match.route.resolvePath(target.parameters)
    val query = match.route.resolveQueryString(target.queryString)
    return RouteResult.Redirect(if (query.isEmpty()) path else "$path?$query")
  }
}
