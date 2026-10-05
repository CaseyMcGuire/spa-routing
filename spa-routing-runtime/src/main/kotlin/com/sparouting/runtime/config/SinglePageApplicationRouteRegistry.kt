package com.sparouting.runtime.config

import com.sparouting.contract.RouteAccessHandler
import com.sparouting.contract.SinglePageApplicationConfig

/** Registers routes with their application and optional route access handlers. */
open class SinglePageApplicationRouteRegistry(
  routeConfigs: List<SinglePageApplicationConfig>
) {
  private val registrations: List<SinglePageApplicationRouteRegistration>
  private val routesByKey: Map<RouteKey, SinglePageApplicationRouteRegistration>

  init {
    SinglePageApplicationConfigValidator.validate(routeConfigs)
    registrations = routeConfigs.flatMap { application ->
      val routeHandlersByKey = application.routeAccessHandlers.handlers.groupBy {
        RouteKey(it.route.applicationId, it.route.routeId)
      }
      validateHandlerRegistrations(application, routeHandlersByKey)
      application.routes.map { route ->
        SinglePageApplicationRouteRegistration(
          application = application,
          route = route,
          applicationAccessHandler = application.applicationAccessHandler,
          routeAccessHandler = routeHandlersByKey[RouteKey(application.id, route.id)]?.single()
        )
      }
    }
    routesByKey = registrations.associateByUnique { RouteKey(it.application.id, it.route.id) }
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
    application: SinglePageApplicationConfig,
    routeHandlers: Map<RouteKey, List<RouteAccessHandler<*>>>
  ) {
    routeHandlers.forEach { (key, implementations) ->
      require(key.applicationId == application.id) {
        "Access handler for ${key.applicationId}:${key.routeId} belongs to a different application than ${application.id}."
      }
      val route = application.routes.find { it.id == key.routeId }
      require(route != null && route.hasAccessHandler) {
        "Access handler registered for ${key.applicationId}:${key.routeId}, but that route does not declare hasAccessHandler = true."
      }
      require(implementations.size == 1) {
        "Expected exactly one access handler for ${key.applicationId}:${key.routeId}, found ${implementations.size}."
      }
    }
    application.routes.filter { it.hasAccessHandler }.forEach { route ->
      val key = RouteKey(application.id, route.id)
      require(routeHandlers.containsKey(key)) {
        "Missing access handler for ${key.applicationId}:${key.routeId}. Supply an implementation of ${route.id}AccessHandler."
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
