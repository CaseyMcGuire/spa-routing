package com.sparouting.ktor

import com.sparouting.contract.RouteRequest
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.runtime.evaluation.RouteRequestEvaluator
import com.sparouting.runtime.evaluation.RouteFailureHandler
import com.sparouting.runtime.response.DefaultRouteHttpResponseConverter
import com.sparouting.runtime.response.RouteHttpResponseConverter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Registers page routes for [configs] and one shared endpoint at [routeDecisionPath].
 *
 * Call once at the routing root. Builds and validates the shared evaluator at registration.
 * Allowed pages use the matching config's HTML renderer.
 * Install ContentNegotiation with a converter for RouteResult, such as Jackson.
 * Configure client authorization to use the same [routeDecisionPath].
 * [failureHandler] supplies typed recovery destinations for both entry points.
 * [responseConverter] maps resolved outcomes to page responses; navigation returns the outcome directly.
 * Asset serving and the server engine are configured by the application.
 */
fun Route.singlePageApplicationRoutes(
  configs: List<SinglePageApplicationConfig>,
  failureHandler: RouteFailureHandler,
  routeDecisionPath: String = "/__spa/route-decision",
  responseConverter: RouteHttpResponseConverter = DefaultRouteHttpResponseConverter()
) {
  val evaluator = RouteRequestEvaluator(configs = configs, failureHandler = failureHandler)

  configs.forEach { config ->
    config.routes.forEach { route ->
      get(route.path) {
        val request = RouteRequest(
          applicationId = config.id,
          routeId = route.id,
          pathParameters = call.toRoutePathParameters(),
          queryString = call.toRouteQueryString(),
          headers = call.toRouteHeaders()
        )
        val result = evaluator.evaluate(request)
        val response = responseConverter.convert(request, result)
        call.respondPage(
          result = result,
          application = config,
          httpResponse = response
        )
      }
    }
  }

  get(routeDecisionPath) {
    val query = call.toRouteQueryString()
    val request = RouteRequest(
      applicationId = query.firstValueOrEmpty("applicationId"),
      routeId = query.firstValueOrEmpty("routeId"),
      pathParameters = query.toRouteDecisionPathParameters(),
      queryString = query.toRouteDecisionQueryString(),
      headers = call.toRouteHeaders()
    )
    val result = evaluator.evaluate(request)
    call.respondRouteDecision(result)
  }
}
