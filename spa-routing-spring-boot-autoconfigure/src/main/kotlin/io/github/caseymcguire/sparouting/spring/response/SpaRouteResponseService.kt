package io.github.caseymcguire.sparouting.spring.response

import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.spring.request.SpaRouteRequest
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteResponseEvaluator

open class SpaRouteResponseService @JvmOverloads constructor(
  private val routeRegistry: SinglePageApplicationRouteRegistry,
  private val evaluator: SpaRouteResponseEvaluator,
  private val invalidPathParameterStatus: Int = 400,
  private val invalidQueryParameterStatus: Int = 400
) {
  open fun evaluate(request: SpaRouteResponseRequest): SpaRouteHttpResponse {
    val match = routeRegistry.findByApplicationAndRouteId(
      applicationId = request.applicationId,
      routeId = request.routeId
    ) ?: return SpaRouteHttpResponse.notFound()

    if (!match.route.hasValidParameterValues(request.parameters)) {
      return SpaRouteHttpResponse(invalidPathParameterStatus)
    }

    if (!match.route.hasValidQueryParameterValues(request.queryParameters)) {
      return SpaRouteHttpResponse(invalidQueryParameterStatus)
    }

    val spaRouteRequest = SpaRouteRequest(
      applicationId = match.application.applicationId,
      routeId = match.route.id,
      method = "GET",
      path = match.application.getFullUrl(match.route.resolvePath(request.parameters)),
      pathParameters = request.parameters,
      queryParameters = request.queryParameters,
      headers = request.headers
    )

    return evaluator.evaluate(
      applicationRules = match.application.rules,
      routeRules = match.application.getRouteRules(match.route),
      request = spaRouteRequest
    )
  }
}
