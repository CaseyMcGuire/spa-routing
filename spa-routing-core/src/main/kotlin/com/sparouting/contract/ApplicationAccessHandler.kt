package com.sparouting.contract

/** Checks access to an application before any route-specific access handler runs. */
abstract class ApplicationAccessHandler<C : SinglePageApplicationConfig> {
  abstract fun evaluate(request: RouteRequest): AccessDecision
}
