package com.sparouting.contract

object SpaApplicationDefinitionValidator {
  fun validate(applications: List<SpaApplicationDefinition>) {
    val duplicateApplicationIds = applications
      .map { it.id }
      .duplicates()
    require(duplicateApplicationIds.isEmpty()) {
      "Duplicate single page application IDs: ${duplicateApplicationIds.joinToString(", ")}"
    }

    applications.forEach { application ->
      validateRouteIds(application)
      validateGeneratedRouteNames(application)
      validateRouteUrls(application)
    }
  }

  private fun validateGeneratedRouteNames(application: SpaApplicationDefinition) {
    val duplicateNames = application.routes.flatMap { route ->
      if (route.generateAccessHandler) {
        listOf(route.id, "${route.id}AccessHandler", "${route.id}Request")
      } else {
        listOf(route.id)
      }
    }.duplicates()
    require(duplicateNames.isEmpty()) {
      "Single page application ${application.id} has colliding generated route types: ${duplicateNames.joinToString(", ")}"
    }
  }

  private fun validateRouteIds(application: SpaApplicationDefinition) {
    val duplicateRouteIds = application.routes
      .map { it.id }
      .duplicates()
    require(duplicateRouteIds.isEmpty()) {
      "Single page application ${application.id} has duplicate route IDs: ${
        duplicateRouteIds.joinToString(", ")
      }"
    }
  }

  private fun validateRouteUrls(application: SpaApplicationDefinition) {
    val duplicateRouteUrls = application.routes
      .map { application.getFullPathPattern(it) }
      .duplicates()
    require(duplicateRouteUrls.isEmpty()) {
      "Single page application ${application.id} has duplicate route URLs: ${
        duplicateRouteUrls.joinToString(", ")
      }"
    }
  }

  private fun <T> List<T>.duplicates(): List<T> {
    return groupingBy { it }
      .eachCount()
      .filterValues { it > 1 }
      .keys
      .toList()
  }
}
