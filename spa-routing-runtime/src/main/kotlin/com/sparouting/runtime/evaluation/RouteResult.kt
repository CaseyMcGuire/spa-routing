package com.sparouting.runtime.evaluation

import com.sparouting.contract.DenialReason

/** Route evaluation outcome before an adapter maps it to a page response or navigation payload. */
sealed interface RouteResult {
  data object Allowed : RouteResult

  /** A navigation denial with a validated and encoded alternative destination URL. */
  data class Denied(
    val reason: DenialReason,
    val destinationUrl: String
  ) : RouteResult

  /** The requested application or route ID is not registered. */
  data object UnknownRoute : RouteResult

  /** Path or declared query parameters do not satisfy the registered route's contract. */
  data object InvalidRequest : RouteResult
}
