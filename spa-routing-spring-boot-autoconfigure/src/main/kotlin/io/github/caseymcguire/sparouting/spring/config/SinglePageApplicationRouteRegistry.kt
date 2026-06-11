package io.github.caseymcguire.sparouting.spring.config

open class SinglePageApplicationRouteRegistry(
  routeConfigs: List<SinglePageApplicationConfig>
) {
  private val registrations = routeConfigs.flatMap { application ->
    application.routes.map { route ->
      SinglePageApplicationRouteRegistration(
        application = application,
        route = route
      )
    }
  }

  private val routesByKey = registrations.associateByUnique { registration ->
    RouteKey(
      applicationId = registration.application.applicationId,
      routeId = registration.route.id
    )
  }

  init {
    SinglePageApplicationConfigValidator.validate(routeConfigs)
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
