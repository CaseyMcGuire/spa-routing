package com.sparouting.contract

/**
 * Base contract for route access. Applications extend a generated route-specific subclass,
 * which supplies the route identity and converts validated input to [R].
 */
abstract class RouteAccessHandler<R>(val route: SpaRouteKey) {
  abstract fun evaluate(request: R): RouteDecision

  protected abstract fun createRequest(context: SpaRouteAccessContext): R

  /** Runtime bridge; path and query cardinality must be validated before calling this method. */
  fun evaluateRequest(context: SpaRouteAccessContext): RouteDecision {
    return evaluate(createRequest(context))
  }
}
