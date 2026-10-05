package io.github.caseymcguire.sparouting.runtime.access

import com.sparouting.contract.AccessDecision
import com.sparouting.contract.RouteAccessContext
import com.sparouting.contract.RouteAccessHandler
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest

/**
 * Evaluates the application access handler and then the matching route access handler.
 * Handler registrations are validated at construction. Requests must be validated before evaluation;
 * adapters should use [io.github.caseymcguire.sparouting.runtime.response.RouteResponseService].
 */
open class RouteAccessEvaluator @JvmOverloads constructor(
  routeRegistry: SinglePageApplicationRouteRegistry,
  handlers: List<RouteAccessHandler<*>> = emptyList()
) {
  private val routeToHandler = handlers.groupBy { handler ->
    RouteKey(applicationId = handler.route.applicationId, routeId = handler.route.routeId)
  }

  init {
    validateHandlerRegistrations(routeRegistry)
  }

  /** Route access is checked only after the application handler allows access. Redirect targets remain unresolved. */
  open fun evaluate(
    applicationAccessHandler: ApplicationAccessHandler,
    request: RouteRequest
  ): AccessDecision {
    val applicationDecision = applicationAccessHandler.evaluate(request)
    if (applicationDecision != AccessDecision.Allow) {
      return applicationDecision
    }

    return evaluateRouteAccess(request)
  }

  private fun evaluateRouteAccess(request: RouteRequest): AccessDecision {
    val key = RouteKey(applicationId = request.applicationId, routeId = request.routeId)
    val handler = routeToHandler[key]?.single()
      ?: return AccessDecision.Allow
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

  private fun validateHandlerRegistrations(routeRegistry: SinglePageApplicationRouteRegistry) {
    routeToHandler.forEach { (key, implementations) ->
      val registration = routeRegistry.findByApplicationAndRouteId(key.applicationId, key.routeId)
      require(registration != null && registration.route.generateAccessHandler) {
        "Access handler registered for ${key.applicationId}:${key.routeId}, but that route does not declare generateAccessHandler = true."
      }
      require(implementations.size == 1) {
        "Expected exactly one access handler for ${key.applicationId}:${key.routeId}, found ${implementations.size}."
      }
    }
    routeRegistry.registrations().filter { it.route.generateAccessHandler }.forEach { registration ->
      val key = RouteKey(
        applicationId = registration.application.applicationId,
        routeId = registration.route.id
      )
      require(routeToHandler.containsKey(key)) {
        "Missing access handler for ${key.applicationId}:${key.routeId}. Register an implementation of ${registration.route.id}AccessHandler."
      }
    }
  }

  private data class RouteKey(
    val applicationId: String,
    val routeId: String
  )
}
