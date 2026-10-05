package io.github.caseymcguire.sparouting.runtime.config

import com.sparouting.contract.RouteAccessHandler
import io.github.caseymcguire.sparouting.runtime.access.ApplicationAccessHandler

/** Registers routes with their application and optional route access handlers. */
open class SinglePageApplicationRouteRegistry @JvmOverloads constructor(
  routeConfigs: List<SinglePageApplicationConfig>,
  applicationHandlers: List<ApplicationAccessHandler>,
  routeHandlers: List<RouteAccessHandler<*>> = emptyList()
) {
  private val registrations: List<SinglePageApplicationRouteRegistration>
  private val routesByKey: Map<RouteKey, SinglePageApplicationRouteRegistration>

  init {
    SinglePageApplicationConfigValidator.validate(routeConfigs)
    val applicationHandlersById = applicationHandlers.groupBy { it.application.id }
    val routeHandlersByKey = routeHandlers.groupBy { RouteKey(it.route.applicationId, it.route.routeId) }
    validateHandlerRegistrations(routeConfigs, applicationHandlersById, routeHandlersByKey)

    registrations = routeConfigs.flatMap { application ->
      val applicationHandler = applicationHandlersById.getValue(application.applicationId).single()
      application.routes.map { route ->
        SinglePageApplicationRouteRegistration(
          application = application,
          route = route,
          applicationAccessHandler = applicationHandler,
          routeAccessHandler = routeHandlersByKey[RouteKey(application.applicationId, route.id)]?.single()
        )
      }
    }
    routesByKey = registrations.associateByUnique { RouteKey(it.application.applicationId, it.route.id) }
  }

  open fun findByApplicationAndRouteId(
    applicationId: String,
    routeId: String
  ): SinglePageApplicationRouteRegistration? {
    return routesByKey[RouteKey(applicationId, routeId)]
  }

  open fun registrations(): List<SinglePageApplicationRouteRegistration> {
    return registrations
  }

  private fun validateHandlerRegistrations(
    routeConfigs: List<SinglePageApplicationConfig>,
    applicationHandlers: Map<String, List<ApplicationAccessHandler>>,
    routeHandlers: Map<RouteKey, List<RouteAccessHandler<*>>>
  ) {
    val applications = routeConfigs.associateBy { it.applicationId }
    applicationHandlers.forEach { (applicationId, implementations) ->
      require(applications.containsKey(applicationId)) {
        "Application access handler registered for unknown application: $applicationId"
      }
      require(implementations.size == 1) {
        "Expected exactly one application access handler for $applicationId, found ${implementations.size}."
      }
    }
    routeConfigs.forEach { application ->
      require(applicationHandlers.containsKey(application.applicationId)) {
        "Missing application access handler for ${application.applicationId}. Register an ApplicationAccessHandler implementation."
      }
    }

    routeHandlers.forEach { (key, implementations) ->
      val route = applications[key.applicationId]?.routes?.find { it.id == key.routeId }
      require(route != null && route.generateAccessHandler) {
        "Access handler registered for ${key.applicationId}:${key.routeId}, but that route does not declare generateAccessHandler = true."
      }
      require(implementations.size == 1) {
        "Expected exactly one access handler for ${key.applicationId}:${key.routeId}, found ${implementations.size}."
      }
    }
    routeConfigs.forEach { application ->
      application.routes.filter { it.generateAccessHandler }.forEach { route ->
        val key = RouteKey(application.applicationId, route.id)
        require(routeHandlers.containsKey(key)) {
          "Missing access handler for ${key.applicationId}:${key.routeId}. Register an implementation of ${route.id}AccessHandler."
        }
      }
    }
  }

  private data class RouteKey(
    val applicationId: String,
    val routeId: String
  )

  private fun <T, K> Iterable<T>.associateByUnique(keySelector: (T) -> K): Map<K, T> {
    val result = mutableMapOf<K, T>()
    for (element in this) {
      val key = keySelector(element)
      require(!result.containsKey(key)) {
        "Duplicate SPA route registration: $key"
      }
      result[key] = element
    }
    return result
  }
}
