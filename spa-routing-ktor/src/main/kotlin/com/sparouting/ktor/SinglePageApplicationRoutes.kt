package com.sparouting.ktor

import com.sparouting.contract.RouteRequest
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.runtime.evaluation.RouteRequestEvaluator
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Registers page routes for [configs] and one shared endpoint at [routeDecisionPath].
 *
 * Call once at the routing root. Builds and validates the shared evaluator at registration.
 * Allowed pages use the matching config's HTML renderer.
 * Install ContentNegotiation with a converter for RouteDecisionResponse, such as Jackson.
 * Configure client authorization to use the same [routeDecisionPath].
 * [invalidPathParameterStatus] and [invalidQueryStringStatus] apply to both pages and navigation checks.
 * Asset serving and the server engine are configured by the application.
 */
fun Route.singlePageApplicationRoutes(
  configs: List<SinglePageApplicationConfig>,
  routeDecisionPath: String = "/__spa/route-decision",
  invalidPathParameterStatus: Int = 400,
  invalidQueryStringStatus: Int = 400
) {
  val evaluator = RouteRequestEvaluator(configs)

  configs.forEach { config ->
    config.routes.forEach { route ->
      get(route.path) {
        val result = evaluator.evaluate(
          RouteRequest(
            applicationId = config.id,
            routeId = route.id,
            pathParameters = call.toRoutePathParameters(),
            queryString = call.toRouteQueryString(),
            headers = call.toRouteHeaders()
          )
        )
        call.respondPage(
          result = result,
          application = config,
          invalidPathParameterStatus = invalidPathParameterStatus,
          invalidQueryStringStatus = invalidQueryStringStatus
        )
      }
    }
  }

  get(routeDecisionPath) {
    val query = call.toRouteQueryString()
    val result = evaluator.evaluate(
      RouteRequest(
        applicationId = query.firstValueOrEmpty("applicationId"),
        routeId = query.firstValueOrEmpty("routeId"),
        pathParameters = query.toRouteDecisionPathParameters(),
        queryString = query.toRouteDecisionQueryString(),
        headers = call.toRouteHeaders()
      )
    )
    call.respondRouteDecision(
      result = result,
      invalidPathParameterStatus = invalidPathParameterStatus,
      invalidQueryStringStatus = invalidQueryStringStatus
    )
  }
}
