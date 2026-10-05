package io.github.caseymcguire.sparouting.runtime.access

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteAccessContext
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest

/**
 * Evaluates the registered application handler before the matching route handler.
 * Requests must refer to a registered route and be validated before evaluation;
 * adapters should use [io.github.caseymcguire.sparouting.runtime.response.RouteResponseService].
 */
open class RouteAccessEvaluator(
  private val routeRegistry: SinglePageApplicationRouteRegistry
) {
  /** Route access is checked only after the application allows access. Redirect targets remain unresolved. */
  open fun evaluate(request: RouteRequest): AccessDecision {
    val registration = requireNotNull(
      routeRegistry.findByApplicationAndRouteId(request.applicationId, request.routeId)
    ) {
      "Unknown SPA route: ${request.applicationId}:${request.routeId}"
    }
    val applicationDecision = registration.applicationAccessHandler.evaluate(request)
    if (applicationDecision != AccessDecision.Allow) {
      return applicationDecision
    }

    val handler = registration.routeAccessHandler ?: return AccessDecision.Allow
    return handler.evaluateRequest(
      RouteAccessContext(
        method = request.method,
        path = request.path,
        pathParameters = request.pathParameters,
        queryString = request.queryString,
        headers = request.headers
      )
    )
  }
}
