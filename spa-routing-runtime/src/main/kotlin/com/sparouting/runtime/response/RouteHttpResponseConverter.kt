package com.sparouting.runtime.response

import com.sparouting.contract.RouteRequest
import com.sparouting.runtime.evaluation.RouteResult

/** Maps an evaluated route to HTTP metadata shared by page requests and navigation checks. */
fun interface RouteHttpResponseConverter {
  /** [request] contains the target route and caller data supplied to the evaluator. */
  fun convert(request: RouteRequest, result: RouteResult): RouteHttpResponse
}
