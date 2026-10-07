package com.sparouting.spring.web

import com.sparouting.contract.RouteManifest
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.runtime.evaluation.RouteRequestEvaluator
import com.sparouting.runtime.response.RouteHttpResponseConverter
import com.sparouting.spring.request.RouteRequestFactory
import com.sparouting.spring.response.toServerResponse
import org.springframework.web.servlet.function.RouterFunction
import org.springframework.web.servlet.function.ServerRequest
import org.springframework.web.servlet.function.ServerResponse
import org.springframework.web.servlet.function.router

class SpringRouterFunctionFactory(
  private val routeConfigs: List<SinglePageApplicationConfig>,
  private val evaluator: RouteRequestEvaluator,
  private val requestFactory: RouteRequestFactory,
  private val responseConverter: RouteHttpResponseConverter
) {
  fun routes(): RouterFunction<ServerResponse> {
    return router {
      routeConfigs.forEach { config ->
        config.routes.forEach { route ->
          GET(route.path) { request ->
            handleSinglePageApplicationRoute(config, route, request)
          }
        }
      }
    }
  }

  private fun handleSinglePageApplicationRoute(
    config: SinglePageApplicationConfig,
    route: RouteManifest,
    request: ServerRequest
  ): ServerResponse {
    val routeRequest = requestFactory.create(request, config, route)
    val result = evaluator.evaluate(routeRequest)
    return responseConverter.convert(routeRequest, result).toServerResponse(result, config)
  }
}
