package com.sparouting.runtime.response

/** Navigation endpoint JSON payload. Adapters map route results to these wire-format fields. */
data class RouteDecisionResponse(
  val statusCode: Int,
  val location: String? = null
)
