package com.sparouting.spring.response

import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.runtime.evaluation.RouteResult
import com.sparouting.runtime.response.RouteDecisionResponse
import com.sparouting.spring.autoconfigure.RoutingProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.servlet.function.ServerResponse

/** Map every route result to a complete page response, rendering HTML only when access is allowed. */
fun RouteResult.toServerResponse(
  application: SinglePageApplicationConfig,
  properties: RoutingProperties
): ServerResponse {
  return when (this) {
    RouteResult.Allowed -> ServerResponse.ok()
      .contentType(MediaType.TEXT_HTML)
      .body(application.htmlRenderer.render(application))
    is RouteResult.Redirect -> ServerResponse.status(302)
      .header(HttpHeaders.LOCATION, url)
      .build()
    RouteResult.NotFound -> ServerResponse.notFound().build()
    RouteResult.InvalidPathParameters -> ServerResponse.status(properties.server.invalidPathParameterStatus).build()
    RouteResult.InvalidQueryString -> ServerResponse.status(properties.server.invalidQueryStringStatus).build()
  }
}

internal fun RouteResult.toRouteDecisionResponse(properties: RoutingProperties): ServerResponse {
  val body = when (this) {
    RouteResult.Allowed -> RouteDecisionResponse(statusCode = 200)
    is RouteResult.Redirect -> RouteDecisionResponse(statusCode = 302, location = url)
    RouteResult.NotFound -> RouteDecisionResponse(statusCode = 404)
    RouteResult.InvalidPathParameters -> RouteDecisionResponse(statusCode = properties.server.invalidPathParameterStatus)
    RouteResult.InvalidQueryString -> RouteDecisionResponse(statusCode = properties.server.invalidQueryStringStatus)
  }
  return ServerResponse.ok()
    .contentType(MediaType.APPLICATION_JSON)
    .header(HttpHeaders.CACHE_CONTROL, "no-store")
    .body(body)
}
