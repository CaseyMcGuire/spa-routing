package com.sparouting.runtime.response

import com.sparouting.contract.RouteRequest
import com.sparouting.runtime.evaluation.RouteResult

/** Maps allowed routes to 200, denials to 302, unknown routes to 404, and invalid requests to [invalidRequestStatus]. */
class DefaultRouteHttpResponseConverter(
  private val invalidRequestStatus: Int = 400
) : RouteHttpResponseConverter {
  override fun convert(request: RouteRequest, result: RouteResult): RouteHttpResponse {
    return when (result) {
      RouteResult.Allowed -> RouteHttpResponse(statusCode = 200)
      is RouteResult.Denied -> RouteHttpResponse(
        statusCode = 302,
        location = result.destinationUrl,
        reason = result.reason
      )
      RouteResult.UnknownRoute -> RouteHttpResponse(statusCode = 404)
      RouteResult.InvalidRequest -> RouteHttpResponse(statusCode = invalidRequestStatus)
    }
  }
}
