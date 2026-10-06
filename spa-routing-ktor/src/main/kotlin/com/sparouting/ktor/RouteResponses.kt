package com.sparouting.ktor

import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.runtime.evaluation.RouteResult
import com.sparouting.runtime.response.RouteDecisionResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall

internal suspend fun RoutingCall.respondPage(
  result: RouteResult,
  application: SinglePageApplicationConfig,
  invalidPathParameterStatus: Int,
  invalidQueryStringStatus: Int
) {
  when (result) {
    RouteResult.Allowed -> respondText(application.htmlRenderer.render(application), ContentType.Text.Html)
    is RouteResult.Redirect -> {
      response.headers.append(HttpHeaders.Location, result.url)
      respond(HttpStatusCode.Found)
    }
    RouteResult.NotFound -> respond(HttpStatusCode.NotFound)
    RouteResult.InvalidPathParameters -> respond(HttpStatusCode.fromValue(invalidPathParameterStatus))
    RouteResult.InvalidQueryString -> respond(HttpStatusCode.fromValue(invalidQueryStringStatus))
  }
}

internal suspend fun RoutingCall.respondRouteDecision(
  result: RouteResult,
  invalidPathParameterStatus: Int,
  invalidQueryStringStatus: Int
) {
  val body = when (result) {
    RouteResult.Allowed -> RouteDecisionResponse(statusCode = 200)
    is RouteResult.Redirect -> RouteDecisionResponse(statusCode = 302, location = result.url)
    RouteResult.NotFound -> RouteDecisionResponse(statusCode = 404)
    RouteResult.InvalidPathParameters -> RouteDecisionResponse(statusCode = invalidPathParameterStatus)
    RouteResult.InvalidQueryString -> RouteDecisionResponse(statusCode = invalidQueryStringStatus)
  }
  response.headers.append(HttpHeaders.CacheControl, "no-store")
  respond(body)
}
