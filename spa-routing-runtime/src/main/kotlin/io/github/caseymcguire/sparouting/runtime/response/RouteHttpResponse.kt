package io.github.caseymcguire.sparouting.runtime.response

/** Resolved route outcome, independent of the server framework and response serialization. */
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
