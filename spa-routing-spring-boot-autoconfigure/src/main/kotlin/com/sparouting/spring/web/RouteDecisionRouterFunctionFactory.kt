package com.sparouting.spring.web

import com.sparouting.runtime.response.RouteResponseRequest
import com.sparouting.runtime.response.RouteResponseService
import com.sparouting.spring.autoconfigure.RoutingProperties
import com.sparouting.spring.request.toRouteHeaders
import com.sparouting.spring.response.toRouteDecisionResponse
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse
import org.springframework.web.servlet.function.router

class RouteDecisionRouterFunctionFactory(
  private val responseService: RouteResponseService,
  private val properties: RoutingProperties
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
      RouteResponseRequest(
        applicationId = request.queryStringValue("applicationId"),
        routeId = request.queryStringValue("routeId"),
        parameters = request.routeParameters(),
        queryString = request.routeQueryString(),
        headers = request.toRouteHeaders()
      )
    ).toRouteDecisionResponse()
  }

  private fun ServerRequest.queryStringValue(name: String): String {
    return param(name).orElse("")
  }

  private fun ServerRequest.routeParameters(): Map<String, String> {
    return params()
      .filterKeys { name -> name.startsWith(ROUTE_PARAMETER_PREFIX) }
      .mapKeys { (name, _) -> name.removePrefix(ROUTE_PARAMETER_PREFIX) }
      .mapValues { (_, values) -> values.firstOrNull().orEmpty() }
  }

  private fun ServerRequest.routeQueryString(): Map<String, List<String>> {
    return params()
      .filterKeys { name -> name.startsWith(QUERY_STRING_PREFIX) }
      .mapKeys { (name, _) -> name.removePrefix(QUERY_STRING_PREFIX) }
      .mapValues { (_, values) -> values.toList() }
  }

  private companion object {
    const val ROUTE_PARAMETER_PREFIX = "parameters."
    const val QUERY_STRING_PREFIX = "queryString."
  }
}
