package io.github.caseymcguire.sparouting.spring.web

import com.caseymcguiredotcom.sparoutecontract.SpaRouteDefinition
import io.github.caseymcguire.sparouting.spring.autoconfigure.SpaRoutingProperties
import io.github.caseymcguire.sparouting.spring.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.rendering.SpaHtmlRenderer
import io.github.caseymcguire.sparouting.spring.request.SpaRouteRequestFactory
import io.github.caseymcguire.sparouting.spring.response.SpaRouteHttpResponse
import io.github.caseymcguire.sparouting.spring.response.toCheckResponse
import io.github.caseymcguire.sparouting.spring.response.toServerResponse
import io.github.caseymcguire.sparouting.spring.rules.SpaRouteResponseEvaluator
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse
import org.springframework.web.servlet.function.router

class SpaRouterFunctionFactory(
  private val routeConfigs: List<SinglePageApplicationConfig>,
  private val routeResponseEvaluator: SpaRouteResponseEvaluator,
  private val requestFactory: SpaRouteRequestFactory,
  private val htmlRenderer: SpaHtmlRenderer,
  private val properties: SpaRoutingProperties
) {
  fun routes(): RouterFunction<ServerResponse> {
    return router {
      routeConfigs.forEach { config ->
        config.routes.forEach { route ->
          GET(config.getFullUrl(route)) { request ->
            handleSinglePageApplicationRoute(config, route, request)
          }
        }
      }
    }
  }

  private fun handleSinglePageApplicationRoute(
    config: SinglePageApplicationConfig,
    route: SpaRouteDefinition,
    request: ServerRequest
  ): ServerResponse {
    val checkHeader = properties.server.routeCheckHeader
    val isRouteCheck = request.headers().firstHeader(checkHeader) != null

    if (!route.hasValidParameterValues(request.pathVariables())) {
      val status = properties.server.invalidPathParameterStatus
      return if (isRouteCheck) {
        SpaRouteHttpResponse(status).toCheckResponse(checkHeader)
      } else {
        ServerResponse.status(status).build()
      }
    }

    val response = routeResponseEvaluator.evaluate(
      rules = config.rules + config.getRouteRules(route),
      request = requestFactory.create(request, config, route)
    )

    if (isRouteCheck) {
      return response.toCheckResponse(checkHeader)
    }

    return response.toServerResponse() ?: config.renderHtml() ?: htmlRenderer.render(config)
  }
}
