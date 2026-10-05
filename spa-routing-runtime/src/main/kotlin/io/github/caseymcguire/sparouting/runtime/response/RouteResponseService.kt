package io.github.caseymcguire.sparouting.runtime.response

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteTarget
import io.github.caseymcguire.sparouting.runtime.access.RouteAccessEvaluator
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationRouteRegistration
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest

/** Validates requests and converts access decisions into response data for pages and client navigation. */
open class RouteResponseService @JvmOverloads constructor(
  private val routeRegistry: SinglePageApplicationRouteRegistry,
  private val accessEvaluator: RouteAccessEvaluator,
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
      applicationId = match.application.manifest.id,
      routeId = match.route.id,
      method = "GET",
      path = match.route.resolvePath(request.parameters),
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
