package com.sparouting.contract

/** A generated collection requires one typed handler for every gated route in [C]. */
interface RouteAccessHandlers<C : SinglePageApplicationConfig> {
  val handlers: List<RouteAccessHandler<*>>
}
