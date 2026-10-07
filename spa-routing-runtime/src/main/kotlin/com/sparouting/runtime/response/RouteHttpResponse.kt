package com.sparouting.runtime.response

import com.sparouting.contract.DenialReason

/**
 * Framework-neutral HTTP response metadata produced by [RouteHttpResponseConverter].
 * Adapters apply [statusCode] and [location] to page responses, or serialize this
 * object inside the navigation endpoint's HTTP 200 response, including [reason].
 */
data class RouteHttpResponse(
  val statusCode: Int,
  val location: String? = null,
  val reason: DenialReason? = null
)
