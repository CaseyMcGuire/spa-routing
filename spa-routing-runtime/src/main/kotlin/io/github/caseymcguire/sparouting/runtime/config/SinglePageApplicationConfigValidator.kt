package io.github.caseymcguire.sparouting.runtime.config

class SinglePageApplicationConfigValidator private constructor() {
  companion object {
    fun validate(routeConfigs: List<SinglePageApplicationConfig>) {
      val duplicateApplicationIds = routeConfigs.map { it.manifest.id }.duplicates()
      require(duplicateApplicationIds.isEmpty()) {
        "Duplicate single page application IDs: ${duplicateApplicationIds.joinToString(", ")}"
      }
      routeConfigs.forEach { application ->
        val manifest = application.manifest
        require(manifest.id.isNotBlank()) { "Application manifest ID must not be blank." }
        val duplicateRouteIds = manifest.routes.map { it.id }.duplicates()
        require(duplicateRouteIds.isEmpty()) {
          "Single page application ${manifest.id} has duplicate route IDs: ${duplicateRouteIds.joinToString(", ")}"
        }
        val duplicatePaths = manifest.routes.map { it.path }.duplicates()
        require(duplicatePaths.isEmpty()) {
          "Single page application ${manifest.id} has duplicate route URLs: ${duplicatePaths.joinToString(", ")}"
        }
        manifest.routes.forEach { route ->
          require(route.id.isNotBlank()) { "Route manifest ID must not be blank." }
          require(route.path.startsWith("/")) {
            "Route manifest ${manifest.id}:${route.id} must have an absolute path pattern: ${route.path}"
          }
        }
      }
    }

    private fun <T> List<T>.duplicates(): Set<T> {
      return groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    }
  }
}
