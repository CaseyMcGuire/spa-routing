package io.github.caseymcguire.sparouting.spring.web

import com.sparouting.contract.RouteDefinition
import io.github.caseymcguire.sparouting.runtime.config.SinglePageApplicationConfig
import io.github.caseymcguire.sparouting.runtime.response.RouteResponseService
import io.github.caseymcguire.sparouting.spring.config.SpringSinglePageApplicationConfig
import io.github.caseymcguire.sparouting.spring.rendering.HtmlRenderer
import io.github.caseymcguire.sparouting.spring.request.RouteRequestFactory
import io.github.caseymcguire.sparouting.spring.response.toServerResponse
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse
import org.springframework.web.servlet.function.router

class RouterFunctionFactory(
  private val routeConfigs: List<SinglePageApplicationConfig>,
  private val routeResponseService: RouteResponseService,
  private val requestFactory: RouteRequestFactory,
  private val htmlRenderer: HtmlRenderer
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
    val response = routeResponseService.evaluate(requestFactory.create(request, config, route))

    return response.toServerResponse()
      ?: (config as? SpringSinglePageApplicationConfig)?.renderHtml()
      ?: htmlRenderer.render(config)
  }
}
