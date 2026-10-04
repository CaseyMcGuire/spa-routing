package io.github.caseymcguire.sparouting.spring.response

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.servlet.function.ServerResponse

data class RouteHttpResponse(
  val statusCode: Int,
  val location: String? = null
) {
  companion object {
    fun ok() = RouteHttpResponse(200)
    fun badRequest() = RouteHttpResponse(400)
    fun notFound() = RouteHttpResponse(404)
    fun found(location: String) = RouteHttpResponse(302, location)
  }
}

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
