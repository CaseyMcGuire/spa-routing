package io.github.caseymcguire.sparouting.runtime.rules

import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.response.RouteHttpResponse

open class RouteRuleActionResolver(
  routeConfigs: List<SinglePageApplicationConfig>
) {
  private val applications = routeConfigs.map { it.application }

  open fun resolve(action: RouteRuleAction): RouteHttpResponse {
    return RouteHttpResponse(
      statusCode = action.statusCode,
      location = action.location ?: action.routeTarget?.let { target ->
        val application = applications.firstOrNull { it.id == target.applicationId }
          ?: throw IllegalStateException("Unknown SPA application route target: ${target.applicationId}")
        val route = application.routes.firstOrNull { it.id == target.routeId }
          ?: throw IllegalStateException("Unknown SPA route target: ${target.applicationId}:${target.routeId}")

        require(route.hasValidParameterValues(target.parameters)) {
          "Invalid parameters for SPA route target ${target.applicationId}:${target.routeId}"
        }

        val path = route.resolvePath(application.getFullPathPattern(route), target.parameters)
        val query = route.resolveQueryString(target.queryString)
        if (query.isEmpty()) path else "$path?$query"
      }
    )
  }
}
