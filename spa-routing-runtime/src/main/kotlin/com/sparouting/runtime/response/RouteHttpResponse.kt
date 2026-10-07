package com.sparouting.runtime.response

/**
 * Framework-neutral HTTP response metadata produced by [RouteHttpResponseConverter].
 * Adapters apply [statusCode] and [location] to page responses.
 * The navigation endpoint serializes RouteResult instead.
 */
data class RouteHttpResponse(
  val statusCode: Int,
  val location: String? = null
)
