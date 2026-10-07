package com.sparouting.spring.response

import com.sparouting.contract.SinglePageApplicationConfig
import com.sparouting.runtime.evaluation.RouteResult
import com.sparouting.runtime.response.RouteHttpResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.servlet.function.ServerResponse

/** Apply converted HTTP metadata, rendering HTML only for an allowed route mapped to 200 without a location. */
fun RouteHttpResponse.toServerResponse(
  result: RouteResult,
  application: SinglePageApplicationConfig
): ServerResponse {
  val response = ServerResponse.status(statusCode)
  location?.let { response.header(HttpHeaders.LOCATION, it) }
  return if (result == RouteResult.Allowed && statusCode == 200 && location == null) {
    response
      .contentType(MediaType.TEXT_HTML)
      .body(application.htmlRenderer.render(application))
  } else {
    response.build()
  }
}

internal fun RouteHttpResponse.toRouteDecisionResponse(): ServerResponse {
  return ServerResponse.ok()
    .contentType(MediaType.APPLICATION_JSON)
    .header(HttpHeaders.CACHE_CONTROL, "no-store")
    .body(this)
}
