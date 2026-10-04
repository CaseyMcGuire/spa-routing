package io.github.caseymcguire.sparouting.spring.access

import com.sparouting.contract.RouteDecision
import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteAccessContext
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.spring.request.RouteRequest

/** Binds DI-managed handlers to routes and rejects missing, duplicate, or stale registrations. */
class RouteHandlerRegistry(
  routeRegistry: SinglePageApplicationRouteRegistry,
  handlers: List<RouteAccessHandler<*>>
) {
  private val routeToHandler = handlers.groupBy { handler ->
    RouteKey(applicationId = handler.route.applicationId, routeId = handler.route.routeId)
  }

  init {
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

  /** Called after parameter validation and the application gate, for both navigation entry points. */
  fun evaluate(request: RouteRequest): RouteDecision {
    val key = RouteKey(applicationId = request.applicationId, routeId = request.routeId)
    val handler = routeToHandler[key]?.single()
      ?: return RouteDecision.Allow
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

  private data class RouteKey(
    val applicationId: String,
    val routeId: String
  )
}
