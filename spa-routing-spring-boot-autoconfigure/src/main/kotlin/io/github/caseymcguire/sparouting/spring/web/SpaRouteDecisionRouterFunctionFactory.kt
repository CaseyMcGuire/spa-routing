package io.github.caseymcguire.sparouting.spring.web

import io.github.caseymcguire.sparouting.spring.autoconfigure.SpaRoutingProperties
import io.github.caseymcguire.sparouting.spring.request.toSpaRouteHeaders
import io.github.caseymcguire.sparouting.spring.response.SpaRouteResponseRequest
import io.github.caseymcguire.sparouting.spring.response.SpaRouteResponseService
import io.github.caseymcguire.sparouting.spring.response.toRouteDecisionResponse
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse
import org.springframework.web.servlet.function.router

class SpaRouteDecisionRouterFunctionFactory(
  private val responseService: SpaRouteResponseService,
  private val properties: SpaRoutingProperties
) {
  fun routes(): RouterFunction<ServerResponse> {
    return router {
      GET(properties.routeDecision.path) { request ->
        handleRouteDecision(request)
      }
    }
  }

  private fun handleRouteDecision(request: ServerRequest): ServerResponse {
    return responseService.evaluate(
      SpaRouteResponseRequest(
        applicationId = request.queryParameterValue("applicationId"),
        routeId = request.queryParameterValue("routeId"),
        parameters = request.routeParameters(),
        queryParameters = request.routeQueryParameters(),
        headers = request.toSpaRouteHeaders()
      )
    ).toRouteDecisionResponse()
  }

  private fun ServerRequest.queryParameterValue(name: String): String {
    return param(name).orElse("")
  }

  private fun ServerRequest.routeParameters(): Map<String, String> {
    return params()
      .filterKeys { name -> name.startsWith(ROUTE_PARAMETER_PREFIX) }
      .mapKeys { (name, _) -> name.removePrefix(ROUTE_PARAMETER_PREFIX) }
      .mapValues { (_, values) -> values.firstOrNull().orEmpty() }
  }

  private fun ServerRequest.routeQueryParameters(): Map<String, List<String>> {
    return params()
      .filterKeys { name -> name.startsWith(QUERY_PARAMETER_PREFIX) }
      .mapKeys { (name, _) -> name.removePrefix(QUERY_PARAMETER_PREFIX) }
      .mapValues { (_, values) -> values.toList() }
  }

  private companion object {
    const val ROUTE_PARAMETER_PREFIX = "parameters."
    const val QUERY_PARAMETER_PREFIX = "queryParameters."
  }
}
