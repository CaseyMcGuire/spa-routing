package com.sparouting.runtime.response

import com.sparouting.contract.RouteRequest
import com.sparouting.runtime.evaluation.RouteResult

/** Maps allowed pages to 200 and every failure to its recovery destination with a 302 response. */
class DefaultRouteHttpResponseConverter : RouteHttpResponseConverter {
  override fun convert(request: RouteRequest, result: RouteResult): RouteHttpResponse {
    return when (result) {
      RouteResult.Allowed -> RouteHttpResponse(statusCode = 200)
      is RouteResult.Failure -> RouteHttpResponse(
        statusCode = 302,
        location = result.destination
      )
    }
  }
}
