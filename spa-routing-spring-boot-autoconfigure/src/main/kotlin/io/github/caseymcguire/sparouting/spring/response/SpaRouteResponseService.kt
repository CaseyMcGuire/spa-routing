package io.github.caseymcguire.sparouting.spring.response

import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationRouteRegistry
import io.github.caseymcguire.sparouting.spring.request.SpaRouteRequest
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteResponseEvaluator

open class SpaRouteResponseService @JvmOverloads constructor(
  private val routeRegistry: SinglePageApplicationRouteRegistry,
  private val evaluator: SpaRouteResponseEvaluator,
  private val invalidPathParameterStatus: Int = 400
) {
  open fun evaluate(request: SpaRouteResponseRequest): SpaRouteHttpResponse {
    val match = routeRegistry.findByApplicationAndRouteId(
      applicationId = request.applicationId,
      routeId = request.routeId
    ) ?: return SpaRouteHttpResponse.notFound()

    if (!match.route.hasValidParameterValues(request.parameters)) {
      return SpaRouteHttpResponse(invalidPathParameterStatus)
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
      match.application.rules + match.application.getRouteRules(match.route),
      spaRouteRequest
    )
  }
}
