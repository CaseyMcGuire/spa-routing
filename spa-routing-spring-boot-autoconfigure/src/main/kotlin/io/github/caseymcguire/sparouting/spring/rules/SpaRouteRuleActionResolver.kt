package io.github.caseymcguire.sparouting.spring.rules

import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.response.SpaRouteHttpResponse

open class SpaRouteRuleActionResolver(
  routeConfigs: List<SinglePageApplicationConfig>
) {
  private val applications = routeConfigs.map { it.application }

  open fun resolve(action: SpaRouteRuleAction): SpaRouteHttpResponse {
    return SpaRouteHttpResponse(
      statusCode = action.statusCode,
      location = action.location ?: action.routeTarget?.let { target ->
        val application = applications.firstOrNull { it.id == target.applicationId }
          ?: throw IllegalStateException("Unknown SPA application route target: ${target.applicationId}")
        val route = application.routes.firstOrNull { it.id == target.routeId }
          ?: throw IllegalStateException("Unknown SPA route target: ${target.applicationId}:${target.routeId}")

        require(route.hasValidParameterValues(target.parameters)) {
          "Invalid parameters for SPA route target ${target.applicationId}:${target.routeId}"
        }

        application.getFullUrl(route.resolvePath(target.parameters))
      }
    )
  }
}
