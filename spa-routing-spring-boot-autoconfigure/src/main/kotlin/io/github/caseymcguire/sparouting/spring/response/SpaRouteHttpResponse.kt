package io.github.caseymcguire.sparouting.spring.response

import org.springframework.http.HttpHeaders
import org.springframework.web.servlet.function.ServerResponse

data class SpaRouteHttpResponse(
  val statusCode: Int,
  val location: String? = null
) {
  companion object {
    fun ok() = SpaRouteHttpResponse(200)
    fun badRequest() = SpaRouteHttpResponse(400)
    fun notFound() = SpaRouteHttpResponse(404)
    fun found(location: String) = SpaRouteHttpResponse(302, location)
  }
}

fun SpaRouteHttpResponse.toServerResponse(): ServerResponse? {
  if (statusCode == 200) {
    return null
  }

  val response = ServerResponse.status(statusCode)
  if (location != null) {
    response.header(HttpHeaders.LOCATION, location)
  }

  return response.build()
}

/**
 * The verdict a client navigation guard reads to mirror the server's routing decision without
 * triggering a redirect or HTML render. [statusCode] and [location] are the same values the server
 * would have responded with on a real page load, so the client can act identically (block, redirect,
 * or show a status) before performing a client-side navigation.
 */
data class SpaRouteCheckResult(
  val allowed: Boolean,
  val statusCode: Int,
  val location: String? = null
)

fun SpaRouteHttpResponse.toCheckResult(): SpaRouteCheckResult {
  return SpaRouteCheckResult(
    allowed = statusCode == 200,
    statusCode = statusCode,
    location = location
  )
}

/**
 * Serializes the routing decision as a JSON verdict (always HTTP 200) instead of a redirect/render.
 * A real 3xx would be auto-followed by the browser's fetch, so the client could never observe it;
 * encoding the decision in the body keeps it readable. [varyHeader] is echoed in `Vary` so caches
 * never serve an HTML page in place of a verdict or vice versa.
 */
fun SpaRouteHttpResponse.toCheckResponse(varyHeader: String): ServerResponse {
  return ServerResponse.ok()
    .header(HttpHeaders.VARY, varyHeader)
    .body(toCheckResult())
}
