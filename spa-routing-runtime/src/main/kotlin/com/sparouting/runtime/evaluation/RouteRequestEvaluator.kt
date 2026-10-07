package com.sparouting.runtime.evaluation

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteAccessContext
import com.sparouting.contract.RouteRequest
import com.sparouting.contract.RouteTarget
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.runtime.config.SinglePageApplicationRouteRegistration
import com.sparouting.runtime.config.SinglePageApplicationRouteRegistry

/**
 * Validates requests, evaluates access, and resolves denial destinations for pages and client navigation.
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
    ) ?: return RouteResult.UnknownRoute

    if (
      !match.route.hasValidParameterValues(request.pathParameters) ||
      !match.route.hasValidQueryStringValues(request.queryString)
    ) {
      return RouteResult.InvalidRequest
    }

    return when (val decision = evaluateAccess(match, request)) {
      AccessDecision.Allowed -> RouteResult.Allowed
      is AccessDecision.Denied -> RouteResult.Denied(
        reason = decision.reason,
        destinationUrl = resolveDestination(decision.destination)
      )
    }
  }

  private fun evaluateAccess(
    registration: SinglePageApplicationRouteRegistration,
    request: RouteRequest
  ): AccessDecision {
    val applicationDecision = registration.applicationAccessHandler.evaluate(request)
    if (applicationDecision != AccessDecision.Allowed) {
      return applicationDecision
    }

    val handler = registration.routeAccessHandler ?: return AccessDecision.Allowed
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

  private fun resolveDestination(target: RouteTarget): String {
    val match = routeRegistry.findByApplicationAndRouteId(target.applicationId, target.routeId)
      ?: throw IllegalStateException("Unknown SPA route target: ${target.applicationId}:${target.routeId}")

    require(match.route.hasValidParameterValues(target.parameters)) {
      "Invalid parameters for SPA route target ${target.applicationId}:${target.routeId}"
    }

    val path = match.route.resolvePath(target.parameters)
    val query = match.route.resolveQueryString(target.queryString)
    return if (query.isEmpty()) path else "$path?$query"
  }
}
