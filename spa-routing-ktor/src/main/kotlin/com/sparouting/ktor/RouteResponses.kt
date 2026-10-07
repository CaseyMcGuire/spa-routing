package com.sparouting.ktor

import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.runtime.evaluation.RouteResult
import com.sparouting.runtime.response.RouteHttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall

internal suspend fun RoutingCall.respondPage(
  result: RouteResult,
  application: SinglePageApplicationConfig,
  httpResponse: RouteHttpResponse
) {
  httpResponse.location?.let { response.headers.append(HttpHeaders.Location, it) }
  if (result == RouteResult.Allowed && httpResponse.statusCode == 200 && httpResponse.location == null) {
    respondText(application.htmlRenderer.render(application), ContentType.Text.Html)
  } else {
    respond(HttpStatusCode.fromValue(httpResponse.statusCode))
  }
}

internal suspend fun RoutingCall.respondRouteDecision(httpResponse: RouteHttpResponse) {
  response.headers.append(HttpHeaders.CacheControl, "no-store")
  respond(HttpStatusCode.OK, httpResponse)
}
