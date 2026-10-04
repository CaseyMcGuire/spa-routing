package io.github.caseymcguire.sparouting.spring.web

import com.sparouting.contract.RouteDefinition
import io.github.caseymcguire.sparouting.spring.autoconfigure.RoutingProperties
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.rendering.HtmlRenderer
import io.github.caseymcguire.sparouting.spring.request.RouteRequestFactory
import io.github.caseymcguire.sparouting.spring.response.toServerResponse
import io.github.caseymcguire.sparouting.spring.rules.RouteResponseEvaluator
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse
import org.springframework.web.servlet.function.router

class RouterFunctionFactory(
  private val routeConfigs: List<SinglePageApplicationConfig>,
  private val routeResponseEvaluator: RouteResponseEvaluator,
  private val requestFactory: RouteRequestFactory,
  private val htmlRenderer: HtmlRenderer,
  private val properties: RoutingProperties
) {
  fun routes(): RouterFunction<ServerResponse> {
    return router {
      routeConfigs.forEach { config ->
        config.routes.forEach { route ->
          GET(config.getFullPathPattern(route)) { request ->
            handleSinglePageApplicationRoute(config, route, request)
          }
        }
      }
    }
  }

  private fun handleSinglePageApplicationRoute(
    config: SinglePageApplicationConfig,
    route: RouteDefinition,
    request: ServerRequest
  ): ServerResponse {
    if (!route.hasValidParameterValues(request.pathVariables())) {
      return ServerResponse.status(properties.server.invalidPathParameterStatus).build()
    }

    if (!route.hasValidQueryStringValues(request.params())) {
      return ServerResponse.status(properties.server.invalidQueryStringStatus).build()
    }

    val response = routeResponseEvaluator.evaluate(
      applicationRules = config.rules,
      request = requestFactory.create(request, config, route)
    )

    return response.toServerResponse() ?: config.renderHtml() ?: htmlRenderer.render(config)
  }
}
