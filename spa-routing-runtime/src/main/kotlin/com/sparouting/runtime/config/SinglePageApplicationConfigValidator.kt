package com.sparouting.runtime.config

import com.sparouting.contract.SinglePageApplicationConfig

class SinglePageApplicationConfigValidator private constructor() {
  companion object {
    fun validate(routeConfigs: List<SinglePageApplicationConfig>) {
      val duplicateApplicationIds = routeConfigs.map { it.id }.duplicates()
      require(duplicateApplicationIds.isEmpty()) {
        "Duplicate single page application IDs: ${duplicateApplicationIds.joinToString(", ")}"
      }
      routeConfigs.forEach { application ->
        require(application.id.isNotBlank()) { "Application config ID must not be blank." }
        val duplicateRouteIds = application.routes.map { it.id }.duplicates()
        require(duplicateRouteIds.isEmpty()) {
          "Single page application ${application.id} has duplicate route IDs: ${duplicateRouteIds.joinToString(", ")}"
        }
        val duplicatePaths = application.routes.map { it.path }.duplicates()
        require(duplicatePaths.isEmpty()) {
          "Single page application ${application.id} has duplicate route URLs: ${duplicatePaths.joinToString(", ")}"
        }
        application.routes.forEach { route ->
          require(route.id.isNotBlank()) { "Route manifest ID must not be blank." }
          require(route.path.startsWith("/")) {
            "Route manifest ${application.id}:${route.id} must have an absolute path pattern: ${route.path}"
          }
        }
      }
    }

    private fun <T> List<T>.duplicates(): Set<T> {
      return groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    }
  }
}
