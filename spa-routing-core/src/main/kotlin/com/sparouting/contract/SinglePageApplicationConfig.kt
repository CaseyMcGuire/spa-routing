package com.sparouting.contract

/** Application metadata and access handlers, usually supplied by a generated config class. */
interface SinglePageApplicationConfig {
  val id: String
  val name: String
  val bundleName: String
  val routes: List<RouteManifest>
  val applicationAccessHandler: ApplicationAccessHandler<*>
  val routeAccessHandlers: RouteAccessHandlers<*>
}
