package com.sparouting.spring.response

import com.sparouting.runtime.response.RouteHttpResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.servlet.function.ServerResponse

/** Convert a denied or redirected page request; null means the adapter should render HTML. */
fun RouteHttpResponse.toServerResponse(): ServerResponse? {
  if (statusCode == 200) {
    return null
  }

  val response = ServerResponse.status(statusCode)
  if (location != null) {
    response.header(HttpHeaders.LOCATION, location)
  }

  return response.build()
}

internal fun RouteHttpResponse.toRouteDecisionResponse(): ServerResponse {
  return ServerResponse.ok()
    .contentType(MediaType.APPLICATION_JSON)
    .header(HttpHeaders.CACHE_CONTROL, "no-store")
    .body(this)
}
