package com.sparouting.ktor

import com.sparouting.contract.RouteRequest
import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.runtime.response.RouteResponseService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Registers page routes for [configs] and one shared endpoint at [routeDecisionPath].
 *
 * Call once at the routing root. Builds and validates the shared runtime service at registration.
 * Allowed pages use the matching config's HTML renderer.
 * Install ContentNegotiation with a converter for RouteHttpResponse, such as Jackson.
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
  val responseService = RouteResponseService(
    configs = configs,
    invalidPathParameterStatus = invalidPathParameterStatus,
    invalidQueryStringStatus = invalidQueryStringStatus
  )

  configs.forEach { config ->
    config.routes.forEach { route ->
      get(route.path) {
        val response = responseService.evaluate(
          RouteRequest(
            applicationId = config.id,
            routeId = route.id,
            pathParameters = call.toRoutePathParameters(),
            queryString = call.toRouteQueryString(),
            headers = call.toRouteHeaders()
          )
        )
        if (response.statusCode == 200) {
          call.respondText(config.htmlRenderer.render(config), ContentType.Text.Html)
        } else {
          response.location?.let { call.response.headers.append(HttpHeaders.Location, it) }
          call.respond(HttpStatusCode.fromValue(response.statusCode))
        }
      }
    }
  }

  get(routeDecisionPath) {
    val query = call.toRouteQueryString()
    val response = responseService.evaluate(
      RouteRequest(
        applicationId = query.firstValueOrEmpty("applicationId"),
        routeId = query.firstValueOrEmpty("routeId"),
        pathParameters = query.toRouteDecisionPathParameters(),
        queryString = query.toRouteDecisionQueryString(),
        headers = call.toRouteHeaders()
      )
    )
    call.response.headers.append(HttpHeaders.CacheControl, "no-store")
    call.respond(response)
  }
}
