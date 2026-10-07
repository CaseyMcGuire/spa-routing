package com.sparouting.runtime.response

import com.sparouting.contract.RouteRequest
import com.sparouting.runtime.evaluation.RouteResult

/** Maps a resolved navigation outcome to page HTTP metadata. Client navigation uses [RouteResult] directly. */
fun interface RouteHttpResponseConverter {
  /** [request] contains the target route and caller data supplied to the evaluator. */
  fun convert(request: RouteRequest, result: RouteResult): RouteHttpResponse
}
