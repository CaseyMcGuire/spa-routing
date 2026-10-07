package com.sparouting.runtime.evaluation

/** Navigation outcome shared by page responses and the client decision endpoint. */
sealed interface RouteResult {
  val type: String

  data object Allowed : RouteResult {
    override val type: String = "allowed"
  }

  /** Every unsuccessful navigation supplies a validated, encoded destination. */
  sealed interface Failure : RouteResult {
    val destination: String
  }

  /** A navigation denial with a validated and encoded alternative destination URL. */
  data class Denied(
    override val destination: String
  ) : Failure {
    override val type: String = "denied"
  }

  /** The requested application or route ID is not registered. */
  data class UnknownRoute(
    override val destination: String
  ) : Failure {
    override val type: String = "unknown_route"
  }

  /** Path or declared query parameters do not satisfy the registered route's contract. */
  data class InvalidRequest(
    override val destination: String
  ) : Failure {
    override val type: String = "invalid_request"
  }
}
