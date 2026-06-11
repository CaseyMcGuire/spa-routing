package io.github.caseymcguire.sparouting.spring.response

import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.spring.request.SpaRouteRequest
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteResponseEvaluator

open class SpaRouteResponseService(
  private val routeRegistry: SinglePageApplicationRouteRegistry,
  private val evaluator: SpaRouteResponseEvaluator
) {
  open fun evaluate(request: SpaRouteResponseRequest): SpaRouteHttpResponse {
    val match = routeRegistry.findByApplicationAndRouteId(
      applicationId = request.applicationId,
      routeId = request.routeId
    ) ?: return SpaRouteHttpResponse.notFound()

    if (!match.route.hasValidParameterValues(request.parameters)) {
      return SpaRouteHttpResponse.badRequest()
    }

    val spaRouteRequest = SpaRouteRequest(
      applicationId = match.application.applicationId,
      routeId = match.route.id,
      method = "GET",
      path = match.application.getFullUrl(match.route.resolvePath(request.parameters)),
      pathParameters = request.parameters,
      headers = request.headers
    )

    return evaluator.evaluate(
      match.application.rules + match.application.getRouteRules(match.route),
      spaRouteRequest
    )
  }
}
