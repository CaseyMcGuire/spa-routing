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
    val applicationHandlersById = applicationHandlers.groupBy { it.manifest.id }
    val routeHandlersByKey = routeHandlers.groupBy { RouteKey(it.route.applicationId, it.route.routeId) }
    validateHandlerRegistrations(routeConfigs, applicationHandlersById, routeHandlersByKey)

    registrations = routeConfigs.flatMap { application ->
      val manifest = application.manifest
      val applicationHandler = applicationHandlersById.getValue(manifest.id).single()
      manifest.routes.map { route ->
        SinglePageApplicationRouteRegistration(
          application = application,
          route = route,
          applicationAccessHandler = applicationHandler,
          routeAccessHandler = routeHandlersByKey[RouteKey(manifest.id, route.id)]?.single()
        )
      }
    }
    routesByKey = registrations.associateByUnique { RouteKey(it.application.manifest.id, it.route.id) }
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
    val applications = routeConfigs.associateBy { it.manifest.id }
    applicationHandlers.forEach { (applicationId, implementations) ->
      require(applications.containsKey(applicationId)) {
        "Application access handler registered for unknown application: $applicationId"
      }
      require(implementations.size == 1) {
        "Expected exactly one application access handler for $applicationId, found ${implementations.size}."
      }
    }
    routeConfigs.forEach { application ->
      require(applicationHandlers.containsKey(application.manifest.id)) {
        "Missing application access handler for ${application.manifest.id}. Register an ApplicationAccessHandler implementation."
      }
    }

    routeHandlers.forEach { (key, implementations) ->
      val route = applications[key.applicationId]?.manifest?.routes?.find { it.id == key.routeId }
      require(route != null && route.hasAccessHandler) {
        "Access handler registered for ${key.applicationId}:${key.routeId}, but that route does not declare hasAccessHandler = true."
      }
      require(implementations.size == 1) {
        "Expected exactly one access handler for ${key.applicationId}:${key.routeId}, found ${implementations.size}."
      }
    }
    routeConfigs.forEach { application ->
      val manifest = application.manifest
      manifest.routes.filter { it.hasAccessHandler }.forEach { route ->
        val key = RouteKey(manifest.id, route.id)
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
