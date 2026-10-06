package com.sparouting.runtime.evaluation

/** Route evaluation outcome before an adapter maps it to a page response or navigation payload. */
sealed interface RouteResult {
  data object Allowed : RouteResult

  /** A validated destination URL, with path and query values already encoded. */
  data class Redirect(val url: String) : RouteResult

  data object NotFound : RouteResult
  data object InvalidPathParameters : RouteResult
  data object InvalidQueryString : RouteResult
}
