package io.github.caseymcguire.sparouting.runtime.access

import com.sparouting.contract.RouteAccessContext
import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.RouteDecision
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.runtime.request.RouteRequest
import io.github.caseymcguire.sparouting.runtime.rules.RouteRule
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleAction
import io.github.caseymcguire.sparouting.runtime.rules.RouteRuleResult

/**
 * Evaluates the application gate and then the matching route access handler.
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

  /** Returns Allow or Deny, leaving redirect targets unresolved. An undecided application gate denies access. */
  open fun evaluate(
    applicationRules: List<RouteRule>,
    request: RouteRequest
  ): RouteRuleResult {
    val gate = firstDecision(applicationRules, request)
      ?: return RouteRuleResult.Deny(RouteRuleAction.notFound())
    if (gate is RouteRuleResult.Deny) {
      return gate
    }

    return when (val access = evaluateRouteAccess(request)) {
      RouteDecision.Allow -> RouteRuleResult.Allow
      is RouteDecision.Redirect -> RouteRuleResult.Deny(RouteRuleAction.redirectTo(access.destination))
    }
  }

  private fun firstDecision(rules: List<RouteRule>, request: RouteRequest): RouteRuleResult? {
    for (rule in rules) {
      val result = rule.evaluate(request)
      if (result != RouteRuleResult.Skip) {
        return result
      }
    }
    return null
  }

  private fun evaluateRouteAccess(request: RouteRequest): RouteDecision {
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
